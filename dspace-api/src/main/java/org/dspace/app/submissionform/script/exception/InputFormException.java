/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.exception;

public class InputFormException extends Exception {

	public InputFormException() {
		super();
	}

	public InputFormException(String message) {
		super(message);
	}

	public InputFormException(String message, Throwable cause) {
		super(message, cause);
	}

	public InputFormException(Throwable cause) {
		super(cause);
	}

}