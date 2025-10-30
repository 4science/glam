/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when not all files are found after a number of retries.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 **/
public class FilesNotFoundAfterRetriesException extends Exception {

    private final int attemptsMade;
    private final List<ScheduledCurationTask> missingCurationTasks;

    public FilesNotFoundAfterRetriesException(List<ScheduledCurationTask> missingFiles, int attemptsMade) {
        super(String.format(
                   "Not all files were found after %d attempts. Missing files: %d", attemptsMade, missingFiles.size()));
        this.missingCurationTasks = Collections.unmodifiableList(missingFiles);
        this.attemptsMade = attemptsMade;
    }

    public List<ScheduledCurationTask> getMissingCurationTasks() {
        return missingCurationTasks;
    }
    public int getAttemptsMade() {
        return attemptsMade;
    }

}