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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Item;
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
    public static final String STATUS_FILE_PATTER_NAME = "%s-%s.json";

    private int maxAttempts;
    private TimeUnit delayTimeUnit;
    private long delayBetweenAttempts;
    private boolean useExponentialBackoff;

    @Autowired
    private ConfigurationService configurationService;

    public List<CompletableFuture<CurationTaskResult>> checkOutputFilesAndLaunchServerlessTask(Context context,
            AmazonS3 s3Client, Item item, ExecutorService executorService, ScheduledProcess scheduledProcess,
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
            log.info("Attempt {}/{} - Files remaining to be checked: {}", attempt, maxAttempts, remainingFiles.size());

            // Scroll through the list and remove the files found.
            Iterator<ScheduledCurationTask> iterator = remainingFiles.iterator();
            int filesFoundInThisAttempt = 0;

            while (iterator.hasNext()) {
                ScheduledCurationTask scheduledCurationTask = iterator.next();
                String outputFileName = String.format(STATUS_FILE_PATTER_NAME, scheduledCurationTask.uuid(),
                                                                               scheduledCurationTask.jobType());
                try {
                    String fileKey = scheduledProcess.process() + "/" + outputFileName;
                    log.info("Checking for key: {} , into bucket: {} ", fileKey, bucketName);

                    if (s3Client.doesObjectExist(bucketName, fileKey)) {
                        log.info("**FILE: {} found!*", fileKey);
                        iterator.remove();
                        filesFoundInThisAttempt++;

                        ResolvedTask resolvedTask = getResolvedTask(allResolvedTasks, scheduledCurationTask);
                        // Launch ExecutorService to process the file just found
                        CompletableFuture<CurationTaskResult> future = CompletableFuture.supplyAsync(() ->
                                executeCurationTask(context, item, s3Client, resolvedTask,
                                                   scheduledCurationTask, scheduledProcess.process()), executorService);
                        futures.add(future);
                    }
                } catch (AmazonServiceException e) {
                    log.error("S3 error while checking file: {} : , {} ", outputFileName, e.getErrorMessage());
                } catch (SdkClientException e) {
                    log.error("SDK client error while checking file: {} , {}", outputFileName, e.getMessage());
                }
            }
            log.info(String.format("Files found in this attempt: %d", filesFoundInThisAttempt));

            if (remainingFiles.isEmpty()) {
                log.info("All files have been found!");
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

    private ResolvedTask getResolvedTask(List<ResolvedTask> allResolvedTasks, ScheduledCurationTask scheduledTask) {
        return allResolvedTasks.stream()
                               .filter(rt -> StringUtils.equals(rt.getName(), scheduledTask.jobType()))
                               .findFirst()
                               .get();
    }

    /**
     * Executes the curation task for a specific file.
     */
    private CurationTaskResult executeCurationTask(Context ctx, Item item, AmazonS3 s3Client, ResolvedTask resolvedTask,
                                                   ScheduledCurationTask scheduledTask, String processId) {
        try {
            log.info("Executing curation task {} for bitstream: {}", scheduledTask.jobType(), scheduledTask.uuid());
            int statusCode =
                         ((ServerlessCurationTask) resolvedTask).perform(ctx, item, s3Client, scheduledTask, processId);
            if (statusCode == 0) {
                var message = "Curation Task:{} completed successfully for bitstream:{} with status:{} ";
                log.info(message, scheduledTask.jobType(), scheduledTask.uuid(), statusCode);
                return CurationTaskResult.success(scheduledTask.jobType(), scheduledTask.uuid());
            } else {
                var message = "Curation Task:{} completed with ERROR status:{} for bitstream: {} ";
                log.error(message, scheduledTask.jobType(), statusCode, scheduledTask.uuid());
                return CurationTaskResult.failure(scheduledTask.jobType(), scheduledTask.uuid(),
                                      "Task completed with error status: " + statusCode);
            }
        } catch (Exception e) {
            var message = "**FAILED** executing Curation Task:{} for bistream:{} , with error: {} ";
            log.error(message, scheduledTask.jobType(), scheduledTask.uuid(), e.getMessage());
            return CurationTaskResult.failure(scheduledTask.jobType(), scheduledTask.uuid(),
                                  "Exception during task execution: " + e.getMessage());
        }
    }

    private void sleep(int attempt) throws InterruptedException {
        long sleepTime = calculateSleepTime(attempt);
        var delayTime = delayTimeUnit.toString().toLowerCase();
        log.info("Wait {} {} before the next attempt.", sleepTime, delayTime);
        try {
            delayTimeUnit.sleep(sleepTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

    public void setDelayBetweenAttempts(long delayBetweenAttempts) {
        this.delayBetweenAttempts = delayBetweenAttempts;
    }

    public void setDelayTimeUnit(TimeUnit delayTimeUnit) {
        this.delayTimeUnit = delayTimeUnit;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void setUseExponentialBackoff(boolean useExponentialBackoff) {
        this.useExponentialBackoff = useExponentialBackoff;
    }

}
