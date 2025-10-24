/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate.service;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.curate.FilesNotFoundAfterRetriesException;
import org.dspace.curate.ScheduledProcess;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Service to check the presence of files in an S3 bucket with retry mechanism.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 **/
public class S3FileChecker {

    private static final Logger log = LogManager.getLogger(S3FileChecker.class);

    private int maxAttempts;
    private TimeUnit delayTimeUnit;
    private long delayBetweenAttempts;
    private boolean useExponentialBackoff;

    @Autowired
    private ConfigurationService configurationService;

    public void checkFiles(AmazonS3 s3Client, ScheduledProcess scheduledProcess, List<String> filesToCheck)
            throws InterruptedException, FilesNotFoundAfterRetriesException {

        if (CollectionUtils.isEmpty(filesToCheck)) {
            return;
        }

        var bucketName = getOutPutBucketName();
        int attempt = 0;
        sleep(attempt);
        while (!filesToCheck.isEmpty() && attempt < this.maxAttempts) {
            attempt++;
            log.info("Attempt {}/{} - Files remaining to be checked: {}", attempt, maxAttempts, filesToCheck.size());

            // Scroll through the list and remove the files found.
            Iterator<String> iterator = filesToCheck.iterator();
            int filesFoundInThisAttempt = 0;

            while (iterator.hasNext()) {
                String fileName = iterator.next();
                try {
                    String fileKey = scheduledProcess.process() + "/" + fileName;
                    log.info("Checking for key: {} , into bucket: {} ", fileKey, bucketName);
                    if (s3Client.doesObjectExist(bucketName, fileKey)) {
                        log.info("File found: {} .", fileKey);
                        iterator.remove();
                        filesFoundInThisAttempt++;
                    }
                } catch (AmazonServiceException e) {
                    log.error("S3 error while checking file: {} : , {} ", fileName, e.getErrorMessage());
                } catch (SdkClientException e) {
                    log.error("SDK client error while checking file: {} , {}", fileName, e.getMessage());
                }
            }
            log.info(String.format("Files found in this attempt: %d", filesFoundInThisAttempt));

            if (filesToCheck.isEmpty()) {
                log.info("All files have been found!");
                return;
            }

            // If this is not your last attempt, wait until the next cycle.
            if (attempt < this.maxAttempts) {
                sleep(attempt);
            }
        }

        throw new FilesNotFoundAfterRetriesException(filesToCheck, attempt);
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
