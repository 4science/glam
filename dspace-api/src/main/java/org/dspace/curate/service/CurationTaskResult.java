/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate.service;

import java.util.UUID;

/**
 * Represents the result of a curation task execution.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public record CurationTaskResult(
    String curationTask,
    UUID uuid,
    boolean successful,
    String errorMessage
) {

    /**
     * Creates a successful result.
     */
    public static CurationTaskResult success(String curationTask, UUID uuid) {
        return new CurationTaskResult(curationTask, uuid, true, null);
    }

    /**
     * Creates a failed result.
     */
    public static CurationTaskResult failure(String curationTask, UUID uuid, String errorMessage) {
        return new CurationTaskResult(curationTask, uuid, false, errorMessage);
    }

}
