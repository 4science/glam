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

/**
 * Service to check the presence of files in an S3 bucket with retry mechanism.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 **/
public class S3FileChecker {

    private static final Logger log = LogManager.getLogger(S3FileChecker.class);

    private int maxAttempts;
    private String bucketName;
    private TimeUnit delayTimeUnit;
    private long delayBetweenAttempts;
    private boolean useExponentialBackoff;

    public void checkFiles(AmazonS3 s3Client, List<String> filesToCheck)
            throws InterruptedException, FilesNotFoundAfterRetriesException {

        if (CollectionUtils.isEmpty(filesToCheck)) {
            return;
        }

        int attempt = 0;
        while (!filesToCheck.isEmpty() && attempt < this.maxAttempts) {
            attempt++;
            log.info(String.format("Attempt %d/%d - Files remaining to be checked: %d",
                                   attempt, maxAttempts, filesToCheck.size()));

            // Scroll through the list and remove the files found.
            Iterator<String> iterator = filesToCheck.iterator();
            int filesFoundInThisAttempt = 0;

            while (iterator.hasNext()) {
                String fileKey = iterator.next();
                try {
                    if (s3Client.doesObjectExist(this.bucketName, fileKey)) {
                        log.info("File found: %s", fileKey);
                        iterator.remove();
                        filesFoundInThisAttempt++;
                    }
                } catch (AmazonServiceException e) {
                    var error = String.format("S3 error while checking file: '%s': %s", fileKey,e.getErrorMessage());
                    log.error(error);
                } catch (SdkClientException e) {
                    var error = String.format("SDK client error while checking file '%s': %s", fileKey, e.getMessage());
                    log.error(error);
                }
            }
            log.info(String.format("Files found in this attempt: %d", filesFoundInThisAttempt));

            if (filesToCheck.isEmpty()) {
                log.info("All files have been found!");
                return;
            }

            // If this is not your last attempt, wait until the next cycle.
            if (attempt < this.maxAttempts) {
                long sleepTime = calculateSleepTime(attempt);
                var delayTime = delayTimeUnit.toString().toLowerCase();
                log.info(String.format("Wait %d %s before the next attempt...", sleepTime, delayTime));
                try {
                    delayTimeUnit.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedException("Thread interrupted while waiting between attempts");
                }
            }
        }

        throw new FilesNotFoundAfterRetriesException(filesToCheck, attempt);
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
            // Delay costante
            return this.delayBetweenAttempts;
        }
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
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
