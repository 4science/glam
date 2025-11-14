/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.core.Context;
import org.dspace.curate.ResolvedTask;
import org.dspace.curate.ScheduledCurationTask;
import org.dspace.curate.ScheduledProcess;
import org.dspace.curate.ServerlessCurationTask;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Service to check the presence of files in an S3 bucket with retry mechanism.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 **/
public class S3FileChecker {

    private static final Logger log = LogManager.getLogger(S3FileChecker.class);
    /**
     * Pattern for generating status file names in the format: bitstreamId-taskType.json
     */
    public static final String STATUS_FILE_PATTER_NAME = "%s-%s.json";

    @Autowired
    private ConfigurationService configurationService;

    private TimeUnit delayTimeUnit = TimeUnit.SECONDS;
    private long delayBetweenAttempts = 60;

    private TimeUnit globalTimeoutUnit = TimeUnit.MINUTES;
    private long globalTimeoutDuration = 15;

    private boolean useExponentialBackoff = false;

    /**
     * Checks for output files in S3 bucket and launches serverless curation tasks when files are found.
     * Uses a circular queue approach with time-based timeout to wait for files to appear.
     *
     * @param context the DSpace context
     * @param s3Client the Amazon S3 client for file operations
     * @param executorService the executor service for running tasks asynchronously
     * @param scheduledProcess the scheduled process containing task information
     * @param allResolvedTasks list of all resolved tasks available for execution
     * @return list of CompletableFuture objects representing the launched serverless tasks
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public List<CompletableFuture<CurationTaskResult>> checkOutputFilesAndLaunchServerlessTask(
        Context context,
        AmazonS3 s3Client,
        ExecutorService executorService,
        ScheduledProcess scheduledProcess,
        List<ResolvedTask> allResolvedTasks
    ) throws InterruptedException {

        if (scheduledProcess == null || scheduledProcess.files() == null || scheduledProcess.files().isEmpty()) {
            return List.of();
        }

        Queue<ScheduledCurationTask> remainingFiles = new LinkedList<>(scheduledProcess.files());
        Map<ScheduledCurationTask, Integer> attempts = new HashMap<>(remainingFiles.size());
        List<CompletableFuture<CurationTaskResult>> futures = new ArrayList<>(remainingFiles.size());

        long startTime = System.currentTimeMillis();
        long timeoutMillis = globalTimeoutUnit.toMillis(globalTimeoutDuration);
        var bucketName = getOutPutBucketName();

        // Scroll through the list and remove the files found.
        while (!remainingFiles.isEmpty() && (System.currentTimeMillis() - startTime) < timeoutMillis) {
            ScheduledCurationTask scheduledCurationTask = remainingFiles.poll();

            String outputFileName =
                String.format(
                    STATUS_FILE_PATTER_NAME,
                    scheduledCurationTask.uuid(),
                    scheduledCurationTask.jobType()
                );

            log.info(
                "S3FileChecker: Checking file: {} - Files remaining to be checked: {} ",
                outputFileName,
                remainingFiles.size()
            );
            try {
                String fileKey = scheduledProcess.process() + "/" + outputFileName;
                log.info("S3FileChecker: Checking for key:{} , into bucket:{} ", fileKey, bucketName);

                if (s3Client.doesObjectExist(bucketName, fileKey)) {
                    log.info("S3FileChecker: FILE:{} found!", fileKey);

                    // Launch ExecutorService to process the file just found
                    futures.add(
                        supplyAsyncCurationTask(
                            context, s3Client, scheduledProcess, scheduledCurationTask,
                            getResolvedTask(allResolvedTasks, scheduledCurationTask),
                            executorService
                        )
                    );
                } else {

                    remainingFiles.add(scheduledCurationTask);

                    if (attempts.containsKey(scheduledCurationTask)) {
                        sleep(attempts.get(scheduledCurationTask));
                    }

                    attempts.compute(
                        scheduledCurationTask,
                        (key, value) -> value == null ? 0 : value + 1
                    );

                }
            } catch (SdkClientException e) {
                var errorMessage = "S3FileChecker: Error while checking file:{} , due to:{} .";
                log.error(errorMessage, outputFileName, e.getMessage());
            }
        }

        if (remainingFiles.isEmpty()) {
            log.info("S3FileChecker: All files have been found!");
        }

        // Mark remaining files as failed
        for (ScheduledCurationTask failedTask : remainingFiles) {
            log.error(
                "S3FileChecker: File not found within timeout for task: {} bitstream: {}",
                failedTask.jobType(),
                failedTask.uuid()
            );
            futures.add(CompletableFuture.completedFuture(failedCurationTask(failedTask)));
        }

        return futures;
    }

    protected CurationTaskResult failedCurationTask(ScheduledCurationTask failedTask) {
        return CurationTaskResult.failure(
            failedTask.jobType(), failedTask.uuid(), List.of(), "File not found within timeout"
        );
    }

    protected CompletableFuture<CurationTaskResult> supplyAsyncCurationTask(
        Context context, AmazonS3 s3Client,
        ScheduledProcess scheduledProcess,
        ScheduledCurationTask scheduledCurationTask,
        ServerlessCurationTask serverlessTask,
        ExecutorService executorService
    ) {
        return CompletableFuture.supplyAsync(
            () -> asyncCurationTask(
                context, s3Client, scheduledProcess, scheduledCurationTask, serverlessTask
            ),
            executorService
        );
    }

    protected CurationTaskResult asyncCurationTask(
        Context context,
        AmazonS3 s3Client,
        ScheduledProcess scheduledProcess,
        ScheduledCurationTask scheduledCurationTask,
        ServerlessCurationTask serverlessTask
    ) {
        // Create a new Context for this thread to avoid Hibernate session conflicts
        Context threadContext = createThreadContext(context);
        try {
            logStartInitPerform(scheduledCurationTask);
            return serverlessTask.initPerform(threadContext, s3Client, scheduledCurationTask,
                                              scheduledProcess.process());
        } finally {
            cleanUpContext(threadContext);
        }
    }

    private static void cleanUpContext(Context threadContext) {
        try {
            threadContext.complete();
        } catch (Exception e) {
            log.error("S3FileChecker: Error completing thread context", e);
        }
    }

    private static Context createThreadContext(Context context) {
        Context threadContext = new Context();
        threadContext.setCurrentUser(context.getCurrentUser());
        return threadContext;
    }

    private static void logStartInitPerform(ScheduledCurationTask scheduledCurationTask) {
        var message = "S3FileChecker: Executing curation task:{} for bitstream:{} .";
        log.info(message, scheduledCurationTask.jobType(), scheduledCurationTask.uuid());
    }

    private ServerlessCurationTask getResolvedTask(List<ResolvedTask> allResolvedTasks, ScheduledCurationTask task) {
        var message = "S3FileChecker: Cannot find ResolvedTask for job type: ";
        return allResolvedTasks.stream()
                               .filter(rt -> StringUtils.equals(rt.getName(), task.jobType()))
                               .findFirst()
                               .map(ResolvedTask::getcTask)
                               .filter(ServerlessCurationTask.class::isInstance)
                               .map(ServerlessCurationTask.class::cast)
                               .orElseThrow(() -> new IllegalStateException(message + task.jobType()));
    }

    private void sleep(int attempt) throws InterruptedException {
        long sleepTime = calculateSleepTime(attempt);
        var delayTime = delayTimeUnit.toString().toLowerCase();
        log.info("S3FileChecker: Wait {} {} before the next attempt.", sleepTime, delayTime);
        try {
            delayTimeUnit.sleep(sleepTime);
        } catch (InterruptedException e) {
            log.error("S3FileChecker: Thread interrupted while waiting between attempts.", e);
            throw new InterruptedException("Thread interrupted while waiting between attempts");
        }
    }

    private String getOutPutBucketName() {
        return this.configurationService.getProperty("curation.s3.bucketName-output");
    }

    private long calculateSleepTime(int attempt) {
        if (this.useExponentialBackoff) {
            // Exponential backoff
            double exponentialDelay = this.delayBetweenAttempts * Math.pow(2, attempt - 1);
            // Limit the maximum delay to 120 seconds.
            long maxDelay = delayTimeUnit.convert(120, TimeUnit.SECONDS);
            long delay = (long) Math.min(exponentialDelay, maxDelay);
            return delay;
        } else {
            return this.delayBetweenAttempts;
        }
    }

    /**
     * Sets the delay between retry attempts when checking for files.
     *
     * @param delayBetweenAttempts the delay duration between attempts
     */
    public void setDelayBetweenAttempts(long delayBetweenAttempts) {
        this.delayBetweenAttempts = delayBetweenAttempts;
    }

    /**
     * Sets the time unit for the delay between retry attempts.
     *
     * @param delayTimeUnit the time unit for delays (e.g., SECONDS, MINUTES)
     */
    public void setDelayTimeUnit(TimeUnit delayTimeUnit) {
        this.delayTimeUnit = delayTimeUnit;
    }

    /**
     * Configures whether to use exponential backoff for retry delays.
     * When enabled, delays increase exponentially with each retry attempt.
     *
     * @param useExponentialBackoff true to enable exponential backoff, false for fixed delays
     */
    public void setUseExponentialBackoff(boolean useExponentialBackoff) {
        this.useExponentialBackoff = useExponentialBackoff;
    }

    /**
     * Sets the time unit for the timeout.
     *
     * @param timeoutUnit the time unit for timeout (e.g., SECONDS, MINUTES)
     */
    public void setTimeoutUnit(TimeUnit timeoutUnit) {
        this.globalTimeoutUnit = timeoutUnit;
    }

    public S3FileChecker setGlobalTimeoutUnit(TimeUnit globalTimeoutUnit) {
        this.globalTimeoutUnit = globalTimeoutUnit;
        return this;
    }

    public S3FileChecker setGlobalTimeoutDuration(long globalTimeoutDuration) {
        this.globalTimeoutDuration = globalTimeoutDuration;
        return this;
    }
}
