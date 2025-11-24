/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.checker;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import jxl.read.biff.BiffException;
import org.dspace.app.submissionform.script.builder.InputFormErrorBuilder;
import org.dspace.core.Context;

/**
 * ExcelSheetValidator interface to validate excel sheets
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public interface ExcelSheetValidator {

    List<InputFormErrorBuilder> check(File fileExcel, Context context, String defaultDefinition)
            throws SQLException, BiffException, IOException;

}
