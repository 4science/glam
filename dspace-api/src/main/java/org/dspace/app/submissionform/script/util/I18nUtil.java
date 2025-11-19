/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.util;

import java.text.MessageFormat;
import java.util.ResourceBundle;

public class I18nUtil {

    private static ResourceBundle messageSource = ResourceBundle.getBundle("inputform-validation-labels");

	public static String getMessage(String i18nMetadataCheckError, Object[] objects) {
		String message = messageSource.getString(i18nMetadataCheckError);
		return MessageFormat.format(message, objects);
	}

}