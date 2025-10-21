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
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.curate.service.S3FileChecker;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.dspace.kernel.ServiceManager;
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
    public static final String STATUS_FILE_PATTER_NAME = "%s-%s.json";

    protected S3FileChecker S3FileChecker;
    protected ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    protected HandleService handleService = HandleServiceFactory.getInstance().getHandleService();
    protected BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    protected ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
    protected BitstreamStorageService bitstreamStorageService = StorageServiceFactory.getInstance()
                                                                                     .getBitstreamStorageService();

    private Context context;
    private Curator curator;
    private String identifier;
    private List<String> tasks;
    private TaskResolver resolver = new TaskResolver();
    private ObjectMapper objectMapper = new ObjectMapper();
    private List<ResolvedTask> resolvedTasks = new ArrayList<>();
    private List<String> cloudCurationTasksToCheck = new ArrayList<>();

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
        ServiceManager serviceManager = new DSpace().getServiceManager();
        S3FileChecker = serviceManager.getServiceByName("s3FileChecker", S3FileChecker.class);
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
        this.curator = initCurator();
        Optional<Item> item$ = getItemByUUID().or(this::getItemByHandle);

        if (item$.isEmpty()) {
            log.info("Cannot find any related item with identifier: {}", identifier);
            return;
        }

        AmazonS3 amazonS3 = s3Client(this.configurationService);
        TransferManager transferManager = TransferManagerBuilder.standard()
                                                                .withS3Client(amazonS3)
                                                                .build();

        Iterator<Bitstream> bitstreams = this.bitstreamService.getItemBitstreams(this.context, item$.get());

        // FASE 1: upload curation task JSON to S3
        ScheduledProcess scheduledProcess = schedulProcess(bitstreams, amazonS3, transferManager);
        // FASE 2: check for status files in S3
        this.S3FileChecker.checkFiles(amazonS3, this.cloudCurationTasksToCheck);
        // FASE 3: launch curation tasks
        launchCurationTasks(item$.get(), amazonS3, scheduledProcess);
    }

    private void launchCurationTasks(Item item, AmazonS3 amazonS3, ScheduledProcess scheduledProcess)
            throws IOException {
        for (ResolvedTask resolvedTask : this.resolvedTasks) {
            if (resolvedTask.getcTask().isCloudCurationTask()) {
                ((CloudCurationTask) resolvedTask.getcTask()).perform(this.context, item, amazonS3, scheduledProcess);
            } else {
                resolvedTask.perform(item);
            }

        }
    }

    private Curator initCurator() {
        Curator curator = new Curator(this.handler);
        curator.setInvoked(Curator.Invoked.BATCH);
        return curator;
    }

    private ScheduledProcess schedulProcess(Iterator<Bitstream> bitstreams, AmazonS3 amazonS3,
                     TransferManager transferManager) throws SQLException, IOException, InterruptedException {
        List<ScheduledCurationTask> scheduledCurationTasks = new ArrayList<>();
        while (bitstreams.hasNext()) {
            Bitstream currentBitstream = bitstreams.next();
            BitStoreService bitStoreService =
                ((BitstreamStorageServiceImpl) this.bitstreamStorageService).getStores()
                                                                            .get(currentBitstream.getStoreNumber());
            if (!(bitStoreService instanceof S3BitStoreService)) {
                log.info("Skipping bitstream {} because is not stored on S3!", currentBitstream.getID());
                continue;
            }
            if (skipBitstream(currentBitstream)) {
                log.info("Skipping bitstream {} was required during submission!", currentBitstream.getID());
                continue;
            }

            String bucketName = ((S3BitStoreService) bitStoreService).getBucketName();
            String path = this.bitstreamStorageService.absolutePath(context, currentBitstream);
            scheduledCurationTasks.addAll(buildTask(currentBitstream, bucketName, path.substring(1)));
        }
        ScheduledProcess curationProcess = new ScheduledProcess(getCustomerId(), getProcessId(),scheduledCurationTasks);

        String uploadBucket = getUploadBucket();
        checkBucket(amazonS3, uploadBucket);
        try {
            uploadFile(curationProcess, transferManager, uploadBucket);
        } finally {
            transferManager.shutdownNow(false);
        }
        return curationProcess;
    }

    private String getCustomerId() {
        return configurationService.getProperty("customer-id", "customer-id");
    }

    private boolean skipBitstream(Bitstream bitstream) {
        var storeNumber = bitstream.getStoreNumber();
        BitStoreService bitStoreService = ((BitstreamStorageServiceImpl) bitstreamStorageService).getStores()
                                                                                                 .get(storeNumber);
        if (!(bitStoreService instanceof S3BitStoreService)) {
            log.info("Skipping bitstream {} because is not stored on S3!", bitstream.getID());
            return true;
        }
        String curationMetadata = this.configurationService.getProperty("curation.task.bitstream.metadata.definition");
        if (StringUtils.isEmpty(curationMetadata)) {
            return false;
        }

        List<MetadataValue> metadata = this.bitstreamService.getMetadataByMetadataString(bitstream, curationMetadata);
        if (metadata.isEmpty()) {
            return false;
        }
        //TODO check metadata value
        return true;
    }

    private void checkBucket(AmazonS3 amazonS3, String uploadBucket) {
        if (!amazonS3.doesBucketExistV2(uploadBucket)) {
            amazonS3.createBucket(uploadBucket);
        }
    }

    private void uploadFile(ScheduledProcess curationProcess, TransferManager transferManager, String uploadBucket)
            throws IOException, InterruptedException {
        String tempDirectory = getTempDirectory();
        File tempFile = null;
        try {
            tempFile = File.createTempFile("curation-task-temp-" + UUID.randomUUID(), ".json", new File(tempDirectory));
            tempFile.deleteOnExit();
            objectMapper.writeValue(tempFile, curationProcess);
            var key = getUploadCustomerFolder() + "/" +
                      curationProcess.id() + "/" + curationProcess.process() + ".json";
            Upload upload = transferManager.upload(uploadBucket, key, tempFile);
            upload.waitForUploadResult();
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
                tempFile.deleteOnExit();
            }
        }
    }

    private String getTempDirectory() {
        return configurationService.hasProperty("upload.temp.dir")
               ? configurationService.getProperty("upload.temp.dir") : System.getProperty("java.io.tmpdir");
    }

    private String getUploadCustomerFolder() {
        return configurationService.getProperty("curation.s3.upload.customer.folder");
    }

    private String getProcessId() {
        if (handler instanceof ProcessDSpaceRunnableHandler) {
            return ((ProcessDSpaceRunnableHandler) handler).getProcessId().toString();
        }
        return "unknown-" + UUID.randomUUID();
    }

    private List<ScheduledCurationTask> buildTask(Bitstream bitstream, String bucketName, String path)
            throws IOException {
        List<ScheduledCurationTask> scheduledCurationTasks = new ArrayList<>(tasks.size());
        for (String task : this.tasks) {
            ResolvedTask resolvedTask = getResolvedTasks(task);
            if (resolvedTask.getcTask().isCloudCurationTask()) {
                scheduledCurationTasks.add(new ScheduledCurationTask(bitstream.getID(), bucketName, path, task));
                String statusFileName = String.format(STATUS_FILE_PATTER_NAME, bitstream.getID(), task);
                cloudCurationTasksToCheck.add(statusFileName);
            }
        }
        return scheduledCurationTasks;
    }

    private ResolvedTask getResolvedTasks(String task) throws IOException {
        ResolvedTask resolvedTask = this.resolvedTasks.stream()
                                                      .filter(rt -> rt.getName().equals(task))
                                                      .findFirst()
                                                      .orElse(null);
        if (resolvedTask == null) {
            resolvedTask = resolver.resolveTask(task);
            resolvedTask.init(this.curator);
            this.resolvedTasks.add(resolvedTask);
        }
        return resolvedTask;
    }

    private Optional<Item> getItemByUUID() {
        try {
            return Optional.ofNullable(this.itemService.find(context, UUID.fromString(identifier)));
        } catch (IllegalArgumentException | SQLException e) {
            log.warn("Cannot convert identifier {} as uuid.", identifier, e);
        }
        return Optional.empty();
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

    private String getUploadBucket() {
        return this.configurationService.getProperty("aws.s3.bucket");
    }

    @Override
    public CurationOrchestratorScriptConfiguration getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("curateOrchestrator",
                CurationOrchestratorScriptConfiguration.class);
    }

}
