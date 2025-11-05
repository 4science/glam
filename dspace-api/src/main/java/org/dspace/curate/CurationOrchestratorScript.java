/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.curate.service.CurationTaskResult;
import org.dspace.curate.service.S3FileChecker;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
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
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 **/
public class CurationOrchestratorScript extends DSpaceRunnable<CurationOrchestratorScriptConfiguration> {

    private static final Logger log = LogManager.getLogger(CurationOrchestratorScript.class);
    public static final String EMAIL_TEMPLATE = "curation_task_report_template";
    public static final String STATUS_FILE_PATTERN_NAME = "%s-%s.json";

    protected S3FileChecker s3FileChecker;
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
    private ExecutorService executorService;
    private TaskResolver resolver = new TaskResolver();
    private ObjectMapper objectMapper = new ObjectMapper();
    private List<ResolvedTask> allResolvedTasks = new ArrayList<>();

    List<String> failedServerTasks = new ArrayList<>();
    List<String> failedServerlessTasks = new ArrayList<>();

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
        s3FileChecker = serviceManager.getServiceByName("s3FileChecker", S3FileChecker.class);
        if (hasInvalidParameters()) {
            return;
        }
        // Initialize executor service if not already initialized
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newFixedThreadPool(10);
        }

        tasks = List.of(commandLine.getOptionValues("task"));
        identifier = commandLine.getOptionValue("identifier");
    }

    private boolean hasInvalidParameters() {
        return !commandLine.hasOption("task") || !commandLine.hasOption("identifier");
    }

    @Override
    public void internalRun() throws Exception {
        this.context = new Context();
        this.curator = initCurator();
        assignCurrentUserInContext();
        handler.logInfo("*************************************************************");
        handler.logInfo("**START** : CurationOrchestrator script for Item:" + identifier);
        handler.logInfo("*************************************************************");
        Optional<Item> item$ = getItemByUUID().or(this::getItemByHandle);
        if (item$.isEmpty()) {
            log.info("Cannot find any related item with identifier: {}", identifier);
            return;
        }

        try {
            AmazonS3 amazonS3 = s3Client(this.configurationService);
            // PHASE 1: upload curation task JSON to S3
            ScheduledProcess scheduledProcess = scheduleProcess(item$.get(), amazonS3);

            // Send email to submitter with process details
            sendEmailToSubmitter(item$.get(), scheduledProcess);

            // PHASE 2: Launch all tasks AND save futures for final result checking
            List<Future<Integer>> serverFutures = launchServerCurationTasks(item$.get());
            List<CompletableFuture<CurationTaskResult>> serverlessFutures =
                    s3FileChecker.checkOutputFilesAndLaunchServerlessTask(context, amazonS3,
                                                                   executorService, scheduledProcess, allResolvedTasks);

            executorService.shutdown();
            boolean allCompleted = waitForTasksToComplete();
            if (allCompleted) {
                // Check results and throw exception if there are failures
                checkTaskResults(serverFutures, serverlessFutures);
            } else {
                throw new RuntimeException("Tasks did not complete after maximum retries");
            }
            // PHASE 3: Finalization of serverless tasks
            log.info("Launching finalization tasks for serverless curation tasks.");
            launchFinalizationTasks(serverlessFutures, item$.get());
            setExecutionMetadata(item$.get());
        } finally {
            cleanup();
        }

        handler.logInfo("***************************************");
        handler.logInfo("**END** : All Curation Tasks completed!");
        handler.logInfo("***************************************");
        lauchExceptionForFailedTasks();
    }

    private void lauchExceptionForFailedTasks() {
        if (!failedServerTasks.isEmpty() || !failedServerlessTasks.isEmpty()) {
            throw new RuntimeException("Some curation tasks failed. Check logs for details.");
        }
    }

    private void launchFinalizationTasks(List<CompletableFuture<CurationTaskResult>> serverlessFutures, Item item) {
        log.info("There are {} serverless tasks to finalize.", serverlessFutures.size());
        for (CompletableFuture<CurationTaskResult> future : serverlessFutures) {
            try {
                CurationTaskResult result = future.get();
                if (!result.successful()) {
                    continue;
                }
                ResolvedTask resolvedTask = getResolvedTasks(result.curationTask());
                if (resolvedTask.getcTask() instanceof ServerlessCurationTask serverlessTask) {
                    serverlessTask.finalizeTask(context, item, result);
                }
            } catch (Exception e) {
                handler.logError("Error during finalization of serverless task", e.getCause());
            }
        }
        log.info("Finalization of serverless tasks completed.");
    }

    private List<Future<Integer>> launchServerCurationTasks(Item item) {
        List<Callable<Integer>> serverTasks = new ArrayList<>();
        for (ResolvedTask resolvedTask : allResolvedTasks) {
            if (resolvedTask.getcTask() instanceof ServerlessCurationTask) {
                continue;
            }
            serverTasks.add(() -> resolvedTask.perform(item));
        }
        try {
            return executorService.invokeAll(serverTasks);
        } catch (InterruptedException e) {
            handler.logError("Error executing server curation tasks for item: " + item.getID(), e);
            return List.of();
        }
    }

    private Curator initCurator() {
        Curator curator = new Curator(this.handler);
        curator.setInvoked(Curator.Invoked.BATCH);
        return curator;
    }

    private ScheduledProcess scheduleProcess(Item item, AmazonS3 amazonS3)
                                            throws SQLException, IOException, InterruptedException, AuthorizeException {
        List<ScheduledCurationTask> scheduledCurationTasks = new ArrayList<>();
        for (String task : this.tasks) {
            ResolvedTask resolvedTask = getResolvedTasks(task);
            if (resolvedTask.getcTask() instanceof ServerlessCurationTask serverlessTask) {
                List<Bitstream> bitstreams = serverlessTask.getProcessableBitstreams(this.context, item);
                handler.logInfo("Task:" + task + " will process:" + bitstreams.size() + " bitstreams.");
                for (Bitstream currentBitstream : bitstreams) {
                    String path = getPathOfCurrentBitstream(currentBitstream);
                    String bucketName = getBucketNameOfCurrentBitstream(currentBitstream);
                    scheduledCurationTasks.add(new ScheduledCurationTask(currentBitstream.getID(),
                                                                         bucketName, path, task));
                }
            }
        }
        ScheduledProcess curationProcess = new ScheduledProcess(getCustomerId(), getProcessId(), getBucketNameOutput(),
                                                                scheduledCurationTasks);
        String uploadBucket = getUploadBucket();
        checkBucket(amazonS3, uploadBucket);
        TransferManager transferManager = null;
        try {
            transferManager = TransferManagerBuilder.standard().withS3Client(amazonS3).build();
            uploadFile(curationProcess, transferManager, uploadBucket);
        } finally {
            if (transferManager != null) {
                transferManager.shutdownNow(false);
            }
        }
        return curationProcess;
    }

    private String getPathOfCurrentBitstream(Bitstream currentBitstream) {
        BitStoreService bitStoreService =
                         ((BitstreamStorageServiceImpl) bitstreamStorageService).getStores()
                                                                                .get(currentBitstream.getStoreNumber());
        return ((S3BitStoreService) bitStoreService).getRelativePath(currentBitstream.getInternalId());
    }

    private String getBucketNameOfCurrentBitstream(Bitstream currentBitstream) {
        BitStoreService bitStoreService =
                         ((BitstreamStorageServiceImpl) bitstreamStorageService).getStores()
                                                                                .get(currentBitstream.getStoreNumber());
        return ((S3BitStoreService) bitStoreService).getBucketName();
    }

    private String getBucketNameOutput() {
        return configurationService.getProperty("curation.s3.bucketName-output");
    }

    private String getCustomerId() {
        return configurationService.getProperty("curation.s3.customer-id");
    }

    private void checkBucket(AmazonS3 amazonS3, String uploadBucket) {
        if (!amazonS3.doesBucketExistV2(uploadBucket)) {
            log.info("Creating S3 bucket {} for uploading curation tasks", uploadBucket);
            amazonS3.createBucket(uploadBucket);
        }
    }

    private void uploadFile(ScheduledProcess curationProcess, TransferManager transferManager, String uploadBucket)
            throws IOException, InterruptedException, SQLException, AuthorizeException {
        File tempFile = null;
        try {
            File tempDir = getTempDir(curationProcess);
            var prefixTempFile = curationProcess.id() + "-" + curationProcess.process() + "-";
            tempFile = File.createTempFile(prefixTempFile, ".json", tempDir);
            objectMapper.writeValue(tempFile, curationProcess);

            var directoryOnS3 = curationProcess.id() + "/";
            log.info("Uploading curation JSON with key:{} to S3!", directoryOnS3 + tempFile.getName());
            var multipleFileUpload = transferManager.uploadDirectory(uploadBucket, directoryOnS3, tempDir, true);
            multipleFileUpload.waitForCompletion();
            log.info("Curation process upload state: {}", multipleFileUpload.getState());
            log.info("Curation process file: {} uploaded successfully to S3 bucket!", tempFile.getName());
            handler.writeFilestream(context, tempFile.getName(), new FileInputStream(tempFile), "application/json");
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
                tempFile.deleteOnExit();
            }
        }
    }

    private File getTempDir(ScheduledProcess curationProcess) {
        String tempDirectory = getTempDirectory() + "/" + curationProcess.id() + "/";
        File tempDir = new File(tempDirectory);
        if (!tempDir.exists()) {
            log.info("Creating temporary directory: {} .", tempDirectory);
            tempDir.mkdirs();
        }
        return tempDir;
    }

    private String getTempDirectory() {
        return configurationService.hasProperty("upload.temp.dir") ?
                     configurationService.getProperty("upload.temp.dir") :
                                    System.getProperty("java.io.tmpdir");
    }

    private String getProcessId() {
        if (handler instanceof ProcessDSpaceRunnableHandler processDSpaceRunnableHandler) {
            return processDSpaceRunnableHandler.getProcessId().toString();
        }
        return "unknown-" + UUID.randomUUID();
    }

    private ResolvedTask getResolvedTasks(String task) throws IOException {
        ResolvedTask resolvedTask = this.allResolvedTasks.stream()
                                                         .filter(rt -> rt.getName().equals(task))
                                                         .findFirst()
                                                         .orElse(null);
        if (resolvedTask == null) {
            resolvedTask = resolver.resolveTask(task);
            resolvedTask.init(this.curator);
            allResolvedTasks.add(resolvedTask);
        }
        return resolvedTask;
    }

    private Optional<Item> getItemByUUID() {
        try {
            return Optional.ofNullable(this.itemService.find(context, UUID.fromString(identifier)));
        } catch (IllegalArgumentException | SQLException e) {
            handler.logError("Cannot convert identifier " + identifier + " as uuid.", e);
            return Optional.empty();
        }
    }

    private Optional<Item> getItemByHandle() {
        try {
            return Optional.ofNullable((Item) this.handleService.resolveToObject(context, identifier));
        } catch (Exception e) {
            handler.logError("Cannot find the proper item with handle: " + identifier, e);
            return Optional.empty();
        }
    }

    private String getUploadBucket() {
        return this.configurationService.getProperty("curation.s3.bucketName-input");
    }

    private void sendEmailToSubmitter(Item item, ScheduledProcess scheduledProcess) {
        if (!isSendingReportEnabled()) {
            return;
        }
        String recipient = item.getSubmitter().getEmail();
        if (StringUtils.isBlank(recipient)) {
            handler.logError("No mail address found for the submitter: " + item.getSubmitter().getID());
            return;
        }

        try {
            Email email = Email.getEmail(I18nUtil.getEmailFilename(context.getCurrentLocale(), EMAIL_TEMPLATE));
            email.addRecipient(recipient);

            // Create list of tasks with their status and links
            List<String> taskList = new ArrayList<>();
            for (ScheduledCurationTask scheduledProces : scheduledProcess.files()) {
                String taskInfo = scheduledProces.jobType() + " : RUNNING";
                taskList.add(taskInfo);
            }

            email.addArgument(taskList);

            var baseUrl = configurationService.getProperty("dspace.ui.url");
            var processLink = baseUrl + "/process/" + scheduledProcess.process();
            email.addArgument(processLink);

            email.send();
        } catch (IOException | MessagingException e) {
            handler.logError("Error sending curation report email to: " + recipient, e);
        }
    }

    private boolean isSendingReportEnabled() {
        return configurationService.getBooleanProperty("curation.s3.send-report.enabled", true);
    }

    private void assignCurrentUserInContext() throws SQLException {
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(context, uuid);
            context.setCurrentUser(ePerson);
        }
    }

    /**
     * Cleanup resources with robust error handling.
     */
    private void cleanup() {
        try {
            if (executorService != null && !executorService.isTerminated()) {
                if (!executorService.isShutdown()) {
                    executorService.shutdown();
                }
                boolean terminated = executorService.awaitTermination(5, TimeUnit.SECONDS);
                if (!terminated) {
                    log.warn("Forcing executor shutdown during cleanup");
                    executorService.shutdownNow();
                    executorService.awaitTermination(2, TimeUnit.SECONDS);
                }
            }
        } catch (Exception e) {
            handler.logError("Error during executor cleanup", e);
        }
        try {
            if (context != null) {
                context.complete();
            }
        } catch (Exception e) {
            handler.logError("Error during context cleanup", e);
        }
    }

    /**
     * Check results of all tasks and throw exception if there are failures.
     */
    private void checkTaskResults(List<Future<Integer>> serverFutures,
                                  List<CompletableFuture<CurationTaskResult>> s3Futures) {

        // Check server task results - count successes and failures
        handler.logInfo("*************************************************************");
        handler.logInfo("Checking results of " + serverFutures.size() + " server tasks");
        ServerTaskCounts counts = countServerTaskResults(serverFutures);
        if (counts.failed > 0) {
            var message = String.format("Server tasks failed: %d out of %d total", counts.failed, counts.total);
            handler.logInfo(message);
            failedServerTasks.add(message);
        }

        // Check Serverless task results
        handler.logInfo("*************************************************************");
        handler.logInfo(String.format("Checking results of %d serverless tasks", s3Futures.size()));
        for (CompletableFuture<CurationTaskResult> future : s3Futures) {
            try {
                CurationTaskResult result = future.get();
                if (result.successful()) {
                    logSuccessfulMessage(result);
                } else {
                    var message = logFailedMessage(result);
                    failedServerlessTasks.add(message);
                }
            } catch (ExecutionException e) {
                handler.logError("Error executing serverless task", e.getCause());
                failedServerlessTasks.add("Serverless task (exception: " + e.getCause().getMessage() + ")");
            } catch (InterruptedException e) {
                handler.logError("Serverless task interrupted", e);
                failedServerlessTasks.add("Serverless task (interrupted)");
            }
        }

        if (!failedServerlessTasks.isEmpty()) {
            handler.logInfo("*************************************************************");
            handler.logError("Serverless tasks failed:" + failedServerlessTasks.size());
            failedServerlessTasks.forEach(handler::logError);
            handler.logInfo("*************************************************************");
        }

        if (!failedServerTasks.isEmpty()) {
            handler.logInfo("*************************************************************");
            handler.logError("Server tasks failed:" + failedServerlessTasks.size());
            failedServerlessTasks.forEach(handler::logError);
            handler.logInfo("*************************************************************");
        }

    }

    private String logFailedMessage(CurationTaskResult result) {
        var error = result.errorMessage() != null ? result.errorMessage() : "Unknown error";
        var message = "FAILED: Serverless task:%s, with error:%s , for bitstreams: %s, and origin bitstream:%s .";
        String bitstreamIds = result.bitsreams()
                                    .stream()
                                    .map(Bitstream::getID)
                                    .map(UUID::toString)
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse("");
        var finalMessage = String.format(message, result.curationTask(), error, bitstreamIds, result.originBitstream());
        handler.logInfo(finalMessage);
        return message;
    }

    private void logSuccessfulMessage(CurationTaskResult result) {
        String bitstreamsId = result.bitsreams()
                                    .stream()
                                    .map(Bitstream::getID)
                                    .map(UUID::toString)
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse("");
        var  message = "SUCCESS: Serverless task %s for bitstreams:%s , and origin bitstream:%s .";
        handler.logInfo(String.format(message, result.curationTask(), bitstreamsId, result.originBitstream()));
    }

    private record ServerTaskCounts(int total, int successful, int failed) { }

    private ServerTaskCounts countServerTaskResults(List<Future<Integer>> serverFutures) {
        if (serverFutures == null) {
            return new ServerTaskCounts(0, 0, 0);
        }
        int total = serverFutures.size();
        int successful = 0;
        int failed = 0;

        for (Future<Integer> future : serverFutures) {
            try {
                Integer result = future.get();
                if (result == 0) {
                    successful++;
                } else {
                    failed++;
                }
            } catch (ExecutionException | InterruptedException e) {
                handler.logError("Error executing server task", e);
                failed++;
            }
        }
        return new ServerTaskCounts(total, successful, failed);
    }

    private boolean waitForTasksToComplete() {
        try {
            final int TIMEOUT_PER_RETRY_MINUTES = getTotalTimeout();
            log.info("Waiting for tasks to complete with timeout of {} minutes.", TIMEOUT_PER_RETRY_MINUTES);
            return executorService.awaitTermination(TIMEOUT_PER_RETRY_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            handler.logError("Task execution interrupted with error:" + e.getMessage());
            return false;
        }
    }

    private int getTotalTimeout() {
        return configurationService.getIntProperty("curation.total.timeout.minutes", 20);
    }

    private void setExecutionMetadata(Item item) throws SQLException {
        addOrUpdateProcessMetadata(item);
        appendHistoryMetadata(item);
    }

    private void addOrUpdateProcessMetadata(Item item) throws SQLException {
        List<MetadataValue> existingProcesses = itemService.getMetadata(item, "cris", "curation", "process", Item.ANY);
        for (String task : tasks) {
            // Check if processName already exists
            boolean alreadyExists = existingProcesses.stream()
                                                     .anyMatch(md -> md.getValue().equalsIgnoreCase(task));
            if (!alreadyExists) {
                itemService.addMetadata(context, item, "cris", "curation", "process", null, task);
            }
        }
    }

    private void appendHistoryMetadata(Item item) throws SQLException {
        String now = DCDate.getCurrent().toString();
        String templateString = "Executed %s on %s \n";
        StringBuilder newHistoryEntry = new StringBuilder();

        for (String task : tasks) {
            newHistoryEntry.append(String.format(templateString, task, now));
        }

        List<MetadataValue> existing = itemService.getMetadata(item, "cris", "curation", "history", Item.ANY);

        String combinedValue;
        if (existing.isEmpty()) {
            combinedValue = newHistoryEntry.toString();
        } else {
            // Assume only one value exists and we want to append to it
            String currentValue = existing.get(0).getValue();
            combinedValue = currentValue + "\n" + newHistoryEntry;
        }

        // Remove old metadata
        itemService.clearMetadata(context, item, "cris", "curation", "history", Item.ANY);

        // Add the new combined value
        itemService.addMetadata(context, item, "cris", "curation", "history", null, combinedValue);
    }

    @Override
    public CurationOrchestratorScriptConfiguration getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("curateOrchestrator",
                CurationOrchestratorScriptConfiguration.class);
    }

}
