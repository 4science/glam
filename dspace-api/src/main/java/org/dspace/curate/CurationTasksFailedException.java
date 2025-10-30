/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.util.ArrayList;
import java.util.List;

/**
 * Exception thrown when one or more curation tasks fail.
 * 
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class CurationTasksFailedException extends Exception {

    private final List<String> failedTasks;
    private final List<String> failedServerTasks;

    public CurationTasksFailedException(List<String> failedTasks, List<String> failedServerTasks) {
        super(buildMessage(failedTasks, failedServerTasks));
        this.failedTasks = new ArrayList<>(failedTasks);
        this.failedServerTasks = new ArrayList<>(failedServerTasks);
    }

    private static String buildMessage(List<String> failedTasks, List<String> failedServerTasks) {
        StringBuilder sb = new StringBuilder("Curation tasks failed: ");

        if (!failedTasks.isEmpty()) {
            sb.append("S3 tasks: ").append(String.join(", ", failedTasks));
        }

        if (!failedServerTasks.isEmpty()) {
            if (!failedTasks.isEmpty()) {
                sb.append("; ");
            }
            sb.append("Server tasks: ").append(String.join(", ", failedServerTasks));
        }
        return sb.toString();
    }

    public List<String> getFailedTasks() {
        return new ArrayList<>(failedTasks);
    }

    public List<String> getFailedServerTasks() {
        return new ArrayList<>(failedServerTasks);
    }
}
