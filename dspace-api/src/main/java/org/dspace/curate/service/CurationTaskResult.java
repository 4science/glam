/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate.service;

import java.util.List;

import org.dspace.content.Bitstream;

/**
 * Represents the result of a curation task execution.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public record CurationTaskResult(
    String curationTask,
    List<Bitstream> bitsreams,
    boolean successful,
    String errorMessage
) {

    /**
     * Creates a successful result.
     */
    public static CurationTaskResult success(String curationTask, List<Bitstream> bitsreams) {
        return new CurationTaskResult(curationTask, bitsreams, true, null);
    }

    /**
     * Creates a failed result.
     */
    public static CurationTaskResult failure(String curationTask, List<Bitstream> bitsreams, String errorMessage) {
        return new CurationTaskResult(curationTask, bitsreams, false, errorMessage);
    }

}
