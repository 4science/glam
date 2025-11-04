/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.core.Context;
import org.dspace.curate.FilesNotFoundAfterRetriesException;
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

    private int maxAttempts;
    private TimeUnit delayTimeUnit;
    private long delayBetweenAttempts;
    private boolean useExponentialBackoff;

    @Autowired
    private ConfigurationService configurationService;

    /**
     * Checks for output files in S3 bucket and launches serverless curation tasks when files are found.
     * Uses a retry mechanism with configurable delays to wait for files to appear.
     *
     * @param context the DSpace context
     * @param s3Client the Amazon S3 client for file operations
     * @param executorService the executor service for running tasks asynchronously
     * @param scheduledProcess the scheduled process containing task information
     * @param allResolvedTasks list of all resolved tasks available for execution
     * @return list of CompletableFuture objects representing the launched serverless tasks
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws FilesNotFoundAfterRetriesException if files are not found after maximum retry attempts
     */
    public List<CompletableFuture<CurationTaskResult>> checkOutputFilesAndLaunchServerlessTask(Context context,
            AmazonS3 s3Client, ExecutorService executorService, ScheduledProcess scheduledProcess,
            List<ResolvedTask> allResolvedTasks) throws InterruptedException, FilesNotFoundAfterRetriesException {

        List<ScheduledCurationTask> remainingFiles = new ArrayList<>(scheduledProcess.files());
        List<CompletableFuture<CurationTaskResult>> futures = new ArrayList<>();

        if (CollectionUtils.isEmpty(remainingFiles)) {
            return futures;
        }

        int attempt = 0;
        sleep(attempt);
        var bucketName = getOutPutBucketName();
        while (!remainingFiles.isEmpty() && attempt < this.maxAttempts) {
            attempt++;
            var message = "S3FileChecker: Attempt {}/{} - Files remaining to be checked: {} ";
            log.info(message, attempt, maxAttempts, remainingFiles.size());

            // Scroll through the list and remove the files found.
            Iterator<ScheduledCurationTask> iterator = remainingFiles.iterator();
            int filesFoundInThisAttempt = 0;

            while (iterator.hasNext()) {
                ScheduledCurationTask scheduledCurationTask = iterator.next();
                String outputFileName = String.format(STATUS_FILE_PATTER_NAME, scheduledCurationTask.uuid(),
                                                                               scheduledCurationTask.jobType());
                try {
                    String fileKey = scheduledProcess.process() + "/" + outputFileName;
                    log.info("S3FileChecker: Checking for key:{} , into bucket:{} ", fileKey, bucketName);

                    if (s3Client.doesObjectExist(bucketName, fileKey)) {
                        log.info("S3FileChecker: FILE:{} found!", fileKey);
                        iterator.remove();
                        filesFoundInThisAttempt++;

                        ServerlessCurationTask serverlessTask = getResolvedTask(allResolvedTasks,
                                                                                scheduledCurationTask);
                        // Launch ExecutorService to process the file just found
                        CompletableFuture<CurationTaskResult> future = CompletableFuture.supplyAsync(() -> {
                            // Create a new Context for this thread to avoid Hibernate session conflicts
                            Context threadContext = createThreadContext(context);
                            try {
                                logStartInitPerform(scheduledCurationTask);
                                return serverlessTask.initPerform(threadContext, s3Client, scheduledCurationTask,
                                                                  scheduledProcess.process());
                            } finally {
                                cleanUpContext(threadContext);
                            }
                        }, executorService);
                        futures.add(future);
                    }
                } catch (SdkClientException e) {
                    var errorMessage = "S3FileChecker: Error while checking file:{} , due to:{} .";
                    log.error(errorMessage, outputFileName, e.getMessage());
                }
            }
            log.info("S3FileChecker: Files found in this attempt:{} .", filesFoundInThisAttempt);

            if (remainingFiles.isEmpty()) {
                log.info("S3FileChecker: All files have been found!");
                break;
            }

            // If this is not your last attempt, wait until the next cycle.
            if (attempt < this.maxAttempts) {
                sleep(attempt);
            }
        }

        if (!remainingFiles.isEmpty()) {
            throw new FilesNotFoundAfterRetriesException(remainingFiles, attempt);
        }
        return futures;
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
     * Sets the maximum number of retry attempts when checking for files.
     *
     * @param maxAttempts the maximum number of attempts before giving up
     */
    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
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

}
