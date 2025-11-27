/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.service;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import jxl.read.biff.BiffException;
import org.dspace.app.submissionform.script.exception.InputFormException;
import org.dspace.core.Context;

/**
 * Interface for generating submission form XML documents
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public interface SubmissionFormXmlGenerator {

    /**
     * Generates item-submission.xml from the Excel file and writes it to the output file
     *
     * @param  xlsFile the Excel file containing the form definitions
     * @param  outputFile the file where the XML will be written
     * @param  context the DSpace context
     * @param  defaultDefinition the default submission definition name
     *
     * @throws BiffException if there's an error reading the Excel file
     * @throws IOException if there's an I/O error
     * @throws SQLException if there's a database error
     * @throws InputFormException if there's a validation error
     */
    void generateItemSubmissionXml(File xlsFile, File outputFile, Context context, String defaultDefinition)
            throws BiffException, IOException, SQLException, InputFormException;

    /**
     * Generates submission-forms.xml from the Excel file and writes it to the output file
     *
     * @param  xlsFile the Excel file containing the form definitions
     * @param  outputFile the file where the XML will be written
     * @param  locale the locale for localized content (can be null)
     * @throws BiffException if there's an error reading the Excel file
     * @throws IOException if there's an I/O error
     * @throws InputFormException if there's a validation error
     */
    void generateSubmissionFormXml(File xlsFile, File outputFile, String locale)
            throws BiffException, IOException, InputFormException;

}
