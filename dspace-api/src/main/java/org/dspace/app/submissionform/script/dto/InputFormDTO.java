/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.dto;

import java.util.List;

import org.dspace.app.submissionform.script.builder.InputFormErrorBuilder;

public class InputFormDTO {

    private List<InputFormErrorBuilder> errorsList;

    public InputFormDTO(List<InputFormErrorBuilder> errorsList) {
        this.errorsList = errorsList;
    }

    public List<InputFormErrorBuilder> getErrorsList() {
        return errorsList;
    }

    public void setErrorsList(List<InputFormErrorBuilder> errorsList) {
        this.errorsList = errorsList;
    }

}