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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
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
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
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
    private ExecutorService executorService;
    private TaskResolver resolver = new TaskResolver();
    private ObjectMapper objectMapper = new ObjectMapper();
    private List<ResolvedTask> allResolvedTasks = new ArrayList<>();

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
        handler.logInfo("**START** : CurationOrchestrator script for Item:" + identifier);
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
                    S3FileChecker.checkOutputFilesAndLaunchServerlessTask(context, amazonS3, item$.get(),
                                                                   executorService, scheduledProcess, allResolvedTasks);

            executorService.shutdown();
            // Wait for completion with 3 retry attempts
            boolean allCompleted = waitForTasksWithRetries();
            if (allCompleted) {
                // Check results and throw exception if there are failures
                checkTaskResults(serverFutures, serverlessFutures);
            } else {
                throw new RuntimeException("Tasks did not complete after maximum retries");
            }
        } finally {
            cleanup();
        }
        handler.logInfo("**END** : All Curation Tasks completed successfully!");
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
                                                                throws SQLException, IOException, InterruptedException {
        List<ScheduledCurationTask> scheduledCurationTasks = new ArrayList<>();
        for (String task : this.tasks) {
            ResolvedTask resolvedTask = getResolvedTasks(task);
            if (resolvedTask.getcTask() instanceof ServerlessCurationTask) {
                ServerlessCurationTask serverlessCurationTask = ((ServerlessCurationTask) resolvedTask.getcTask());
                List<Bitstream> bitstreams = serverlessCurationTask.getProcessableBitstreams(this.context, item);
                if (!bitstreams.isEmpty()) {
                    serverlessCurationTask.init(this.context, item);
                }
                for (Bitstream currentBitstream : bitstreams) {
                    String path = getPathOfCurrentBitstream(currentBitstream);
                    String bucketName = getBucketNameOfCurrentBitstream(currentBitstream);
                    scheduledCurationTasks.add(new ScheduledCurationTask(currentBitstream.getID(),
                                                                         bucketName, path, task));
                }
            }
        }
        ScheduledProcess curationProcess = new ScheduledProcess(getCustomerId(), getProcessId(),
                                                                getBucketNameOutput(), scheduledCurationTasks);
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
            throws IOException, InterruptedException {
        File tempFile = null;
        try {
            File tempDir = getTempDir(curationProcess);
            var prefixTempFile = curationProcess.id() + "-" + curationProcess.process() + "-";
            tempFile = File.createTempFile(prefixTempFile, ".json", tempDir);
            tempFile.deleteOnExit();
            objectMapper.writeValue(tempFile, curationProcess);

            var directoryOnS3 = curationProcess.id() + "/";
            log.info("Uploading curation JSON with key:{} to S3!", directoryOnS3 + tempFile.getName());
            var multipleFileUpload = transferManager.uploadDirectory(uploadBucket, directoryOnS3, tempDir, true);
            multipleFileUpload.waitForCompletion();
            log.info("Curation process upload state: {}", multipleFileUpload.getState());
            log.info("Curation process file: {} uploaded successfully to S3 bucket!", tempFile.getName());
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
        return configurationService.hasProperty("upload.temp.dir")
               ? configurationService.getProperty("upload.temp.dir") : System.getProperty("java.io.tmpdir");
    }

    private String getProcessId() {
        if (handler instanceof ProcessDSpaceRunnableHandler) {
            return ((ProcessDSpaceRunnableHandler) handler).getProcessId().toString();
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
            log.warn("Error during executor cleanup", e);
        }
        try {
            if (context != null) {
                context.complete();
            }
        } catch (Exception e) {
            log.warn("Error during context cleanup", e);
        }
    }

    /**
     * Check results of all tasks and throw exception if there are failures.
     */
    private void checkTaskResults(List<Future<Integer>> serverFutures,
                            List<CompletableFuture<CurationTaskResult>> s3Futures) throws CurationTasksFailedException {
        List<String> failedServerTasks = new ArrayList<>();
        // Check server task results - count successes and failures
        ServerTaskCounts counts = countServerTaskResults(serverFutures);
        if (counts.failed > 0) {
            var message = String.format("Server tasks failed: %d out of %d total", counts.failed, counts.total);
            handler.logInfo(message);
            failedServerTasks.add(message);
        }

        // Check Serverless task results
        List<String> failedServerlessTasks = new ArrayList<>();
        for (CompletableFuture<CurationTaskResult> future : s3Futures) {
            try {
                CurationTaskResult result = future.get();
                if (!result.successful()) {
                    String error = result.errorMessage() != null ? result.errorMessage() : "Unknown error";
                    failedServerlessTasks.add(result.curationTask() + " [" + result.uuid() + "]: " + error);
                }
            } catch (ExecutionException e) {
                failedServerlessTasks.add("Serverless task (exception: " + e.getCause().getMessage() + ")");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failedServerlessTasks.add("Serverless task (interrupted)");
            }
        }
        // Throw exception if there are failures
        if (!failedServerlessTasks.isEmpty() || !failedServerTasks.isEmpty()) {
            throw new CurationTasksFailedException(failedServerlessTasks, failedServerTasks);
        }
        log.info("All curation tasks completed successfully");
    }

    private record ServerTaskCounts(int total, int successful, int failed) {}

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
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                failed++; // Any exception = failed
            }
        }

        return new ServerTaskCounts(total, successful, failed);
    }

    /**
     * Wait for task completion with multiple retry attempts.
     * IMPORTANT: shutdown() is called only once at the beginning.
     */
    private boolean waitForTasksWithRetries() {
        final int MAX_RETRIES = 3;
        final int TIMEOUT_PER_RETRY_MINUTES = getTotalTimeout();

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            var info = "Attempt {}/{}: Waiting for task completion (timeout: {} minutes)";
            log.info(info, attempt, MAX_RETRIES, TIMEOUT_PER_RETRY_MINUTES);
            try {
                boolean finished = executorService.awaitTermination(TIMEOUT_PER_RETRY_MINUTES, TimeUnit.MINUTES);
                if (finished) {
                    log.info("All tasks completed successfully on attempt {}/{}", attempt, MAX_RETRIES);
                    return true;
                }
                // Handle timeout case
                TimeoutResult result = handleTaskTimeout(attempt, MAX_RETRIES, TIMEOUT_PER_RETRY_MINUTES);
                if (result == TimeoutResult.COMPLETED) {
                    return true;
                }
                if (result == TimeoutResult.FAILED) {
                    return false;
                }
                // Continue to next attempt if RETRY
            } catch (InterruptedException e) {
                return handleInterruption(attempt, MAX_RETRIES, e);
            }
        }
        return false;
    }

    private enum TimeoutResult {
        RETRY,// Continue with next attempt
        COMPLETED,// All tasks are actually done
        FAILED// Maximum retries exceeded
    }

    private TimeoutResult handleTaskTimeout(int attempt, int maxRetries, int timeoutMinutes) {
        var message = "Tasks did not complete within {} minutes on attempt {}/{} ";
        log.warn(message, timeoutMinutes, attempt, maxRetries);

        // If this was the last attempt, force shutdown
        if (attempt >= maxRetries) {
            performForcedShutdown(maxRetries);
            return TimeoutResult.FAILED;
        }

        // Check if tasks are actually still running
        int activeTasks = getActiveTaskCount();
        log.info("Active tasks remaining: {}", activeTasks);

        if (activeTasks == 0) {
            log.info("No active tasks found, considering as completed");
            return TimeoutResult.COMPLETED;
        }

        log.info("Retrying... ({}/{} attempts remaining)",maxRetries - attempt, maxRetries);
        return TimeoutResult.RETRY;
    }

    private void performForcedShutdown(int maxRetries) {
        log.error("Maximum retries ({}) exceeded. Forcing shutdown.", maxRetries);
        executorService.shutdownNow();

        try {
            executorService.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            handler.logError("Interrupted during forced shutdown", ie);
        }
    }

    private boolean handleInterruption(int attempt, int maxRetries, InterruptedException e) {
        handler.logError("Task execution interrupted on attempt:" + attempt);
        return false;
    }

    private int getActiveTaskCount() {
        if (executorService instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) executorService;
            return tpe.getActiveCount();
        }
        // Fallback: assume there are active tasks if not terminated
        return executorService.isTerminated() ? 0 : 1;
    }

    private int getTotalTimeout() {
        return configurationService.getIntProperty("curation.total.timeout.minutes", 10);
    }

    @Override
    public CurationOrchestratorScriptConfiguration getScriptConfiguration() {
        return new DSpace().getServiceManager().getServiceByName("curateOrchestrator",
                CurationOrchestratorScriptConfiguration.class);
    }

}
