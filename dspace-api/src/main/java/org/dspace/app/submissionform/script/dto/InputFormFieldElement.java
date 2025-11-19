/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.dto;

public class InputFormFieldElement {

	private String formName, dcSchema, dcElement, dcQualifier;

	public InputFormFieldElement(String formName, String dcSchema, String dcElement, String dcQualifier) {
		super();
		this.formName = formName;
		this.dcSchema = dcSchema;
		this.dcElement = dcElement;
		this.dcQualifier = dcQualifier;
	}

	public String toString() {
		return getFormName() + ":" + getDcSchema() + "." + getDcElement() + "." + getDcQualifier();
	}

	public String getElementName() {
		String name = getDcSchema() + "." + getDcElement();
		if (!getDcQualifier().isEmpty()) {
			name += "." + getDcQualifier();
		}
		return name;		
	}

	public boolean equals(Object obj) {
		InputFormFieldElement elemento = (InputFormFieldElement) obj;
		return elemento.getDcSchema().equals(dcSchema) &&
               elemento.getDcElement().equals(dcElement) &&
               elemento.getDcQualifier().equals(dcQualifier) &&
               elemento.getFormName().equals(formName);
	}

	@Override
	public int hashCode() {
        return (getElementName() + formName).hashCode();
	}

    public String getDcElement() {
        return dcElement;
    }

    public void setDcElement(String dcElement) {
        this.dcElement = dcElement;
    }

    public String getDcQualifier() {
        return dcQualifier;
    }

    public void setDcQualifier(String dcQualifier) {
        this.dcQualifier = dcQualifier;
    }

    public String getFormName() {
        return formName;
    }

    public void setFormName(String formName) {
        this.formName = formName;
    }

    public String getDcSchema()
    {
        return dcSchema;
    }

    public void setDcSchema(String dcSchema) {
        this.dcSchema = dcSchema;
    }

}

