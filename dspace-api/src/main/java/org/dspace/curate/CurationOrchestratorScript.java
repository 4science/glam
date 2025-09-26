/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.scripts.ProcessDSpaceRunnableHandler;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.storage.bitstore.BitStoreService;
import org.dspace.storage.bitstore.BitstreamStorageServiceImpl;
import org.dspace.storage.bitstore.S3BitStoreService;
import org.dspace.storage.bitstore.factory.StorageServiceFactory;
import org.dspace.storage.bitstore.service.BitstreamStorageService;
import org.dspace.utils.DSpace;

/**
 * Orchestrates curation tasks for a given DSpace Item.
 * <p>
 * Given an Item identifier (UUID or handle) and a list of task names, this runnable:
 * - Resolves the Item
 * - Collects its Bitstreams stored on S3
 * - Builds a ScheduledProcess containing ScheduledCurationTask entries
 * - Serializes the process as JSON and uploads it to a configured S3 bucket
 * </p>
 * The produced JSON is intended to be consumed by external workers to execute the requested curation tasks.
 *
 * Configuration:
 * - http.proxy.host / http.proxy.port / http.proxy.hosts-to-ignore
 * - aws.s3.bucket (target bucket for the JSON payload)
 *
 * Limitations:
 * - Only Bitstreams stored in S3 are considered
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class CurationOrchestratorScript extends DSpaceRunnable<CurationOrchestratorScriptConfiguration> {

    private static final Logger log = LogManager.getLogger(CurationOrchestratorScript.class);

    protected ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    protected HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    protected BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    protected ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
    protected BitstreamStorageService bitstreamStorageService = StorageServiceFactory.getInstance()
                                                                                     .getBitstreamStorageService();

    private List<String> tasks;
    private String identifier;
    private Context context;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static ClientConfiguration getProxyClientConfig(String proxyHost, String proxyPort, String ignoredHosts) {
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        if (StringUtils.isNotBlank(proxyHost)) {
            clientConfiguration.setProxyHost(proxyHost);
        }
        if (StringUtils.isNotBlank(proxyPort)) {
            clientConfiguration.setProxyPort(Integer.parseInt(proxyPort));
        }
        if (StringUtils.isNotBlank(ignoredHosts)) {
            clientConfiguration.setNonProxyHosts(ignoredHosts);
        }
        return clientConfiguration;
    }

    public AmazonS3 s3Client(ConfigurationService configurationService) {
        String proxyHost = configurationService.getProperty("http.proxy.host");
        String proxyPort = configurationService.getProperty("http.proxy.port");
        String ignoredHosts = configurationService.getProperty("http.proxy.hosts-to-ignore");
        return AmazonS3Client.builder()
                             .withClientConfiguration(getProxyClientConfig(proxyHost, proxyPort, ignoredHosts))
                             .build();
    }

    @Override
    public void setup() throws ParseException {
        if (hasInvalidParameters()) {
            return;
        }
        tasks = List.of(commandLine.getOptionValues("task"));
        identifier = commandLine.getOptionValue("identifier");
    }

    private boolean hasInvalidParameters() {
        return !commandLine.hasOption("task") || !commandLine.hasOption("identifier");
    }

    @Override
    public void internalRun() throws Exception {
        this.context = new Context(Context.Mode.READ_ONLY);
        Optional<Item> item$ = getItemByUUID().or(this::getItemByHandle);

        if (item$.isEmpty()) {
            log.info("Cannot find any related item with identifier: {}", identifier);
            return;
        }

        Item item = item$.get();
        Iterator<Bitstream> bitstreams = this.bitstreamService.getItemBitstreams(context, item);

        AmazonS3 amazonS3 = s3Client(this.configurationService);
        TransferManager transferManager = TransferManagerBuilder.standard().withS3Client(amazonS3).build();

        List<ScheduledCurationTask> scheduledCurationTasks = new ArrayList<>();
        while (bitstreams.hasNext()) {
            Bitstream next = bitstreams.next();
            BitStoreService bitStoreService =
                ((BitstreamStorageServiceImpl) this.bitstreamStorageService).getStores().get(next.getStoreNumber());
            if (!(bitStoreService instanceof S3BitStoreService)) {
                log.info("Skipping bitstream {} because is not stored on S3!", next.getID());
                continue;
            }
            String bucketName = ((S3BitStoreService) bitStoreService).getBucketName();
            String path = this.bitstreamStorageService.absolutePath(context, next);
            scheduledCurationTasks.addAll(buildTask(next, bucketName, path));
        }
        ScheduledProcess curationProcess = new ScheduledProcess("cliente", getProcessId(), scheduledCurationTasks);

        String uploadBucket = this.configurationService.getProperty("aws.s3.bucket");
        checkBucket(amazonS3, uploadBucket);
        try {
            uploadFile(curationProcess, transferManager, uploadBucket);
        } finally {
            transferManager.shutdownNow(false);
        }
    }

    private void checkBucket(AmazonS3 amazonS3, String uploadBucket) {
        if (!amazonS3.doesBucketExistV2(uploadBucket)) {
            amazonS3.createBucket(uploadBucket);
        }
    }

    private void uploadFile(ScheduledProcess curationProcess, TransferManager transferManager, String uploadBucket)
            throws IOException, InterruptedException {
        File tempFile = File.createTempFile("temp-", "upload.json");
        tempFile.deleteOnExit();
        objectMapper.writeValue(tempFile, curationProcess);
        var key = "pdfa-input/" + curationProcess.id() + "/" + curationProcess.process() + ".json";
        Upload upload = transferManager.upload(uploadBucket, key, tempFile);
        upload.waitForUploadResult();
    }

    private String getProcessId() {
        if (handler instanceof ProcessDSpaceRunnableHandler) {
            return ((ProcessDSpaceRunnableHandler) handler).getProcessId().toString();
        }
        return "unknown-" + UUID.randomUUID();
    }

    private List<ScheduledCurationTask> buildTask(Bitstream next, String bucketName, String path) {
        List<ScheduledCurationTask> scheduledCurationTasks = new ArrayList<>(tasks.size());
        for (String task : this.tasks) {
            scheduledCurationTasks.add(new ScheduledCurationTask(next.getID(), bucketName, path, task));
        }
        return scheduledCurationTasks;
    }

    private Optional<Item> getItemByUUID() {
        Optional<Item> item = Optional.empty();
        try {
            UUID uuid = UUID.fromString(identifier);
            item = Optional.ofNullable(this.itemService.find(context, uuid));
        } catch (IllegalArgumentException | SQLException e) {
            log.warn("Cannot convert identifier {} as uuid.", identifier, e);
        }
        return item;
    }

    private Optional<Item> getItemByHandle() {
        Optional<Item> item = Optional.empty();
        try {
            item = Optional.ofNullable((Item) this.handleService.resolveToObject(context, identifier));
        } catch (Exception e) {
            log.warn("Cannot find the proper item with handle {}", identifier, e);
        }
        return item;
    }

    @Override
    public CurationOrchestratorScriptConfiguration getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("curateOrchestrator",
                CurationOrchestratorScriptConfiguration.class);
    }

}
