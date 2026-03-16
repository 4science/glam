/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;

import jxl.read.biff.BiffException;
import org.dspace.core.Context;
import org.jdom2.DocType;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 * Implementation for generating submission form XML documents
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class SubmissionFormXmlGeneratorImpl implements SubmissionFormXmlGenerator {

    private static final String INPUT_FORMS_ROOT = "input-forms";
    private static final String ITEM_SUBMISSION_ROOT = "item-submission";
    private static final String ITEM_SUBMISSION_DTD = "item-submission.dtd";
    private static final String SUBMISSION_FORMS_DTD = "submission-forms.dtd";

    private InputSubmissionMap submissionMap;
    private SubmissionFormGeneratorI18nService i18nService;

    @Override
    public void generateItemSubmissionXml(File xlsFile, File outputFile, Context context, String defaultDefinition)
            throws BiffException, IOException, SQLException {
        StepDefinitions stepDefinitions = new StepDefinitions();
        SubmissionDefinitions submissionDefinitions = new SubmissionDefinitions();

        Element root = new Element(ITEM_SUBMISSION_ROOT);
        Element formMapEl = new Element("submission-map");
        Element stepDefinitionsEl = new Element("step-definitions");
        Element submissionDefinitionsEl = new Element("submission-definitions");

        DocType dt = new DocType(ITEM_SUBMISSION_ROOT, ITEM_SUBMISSION_DTD);
        Document doc = new Document(root, dt);

        submissionMap.create(formMapEl, context, defaultDefinition);
        stepDefinitions.create(stepDefinitionsEl, xlsFile);
        submissionDefinitions.create(submissionDefinitionsEl, xlsFile);

        root.addContent(formMapEl);
        root.addContent(stepDefinitionsEl);
        root.addContent(submissionDefinitionsEl);

        writeDocument(doc, outputFile);
    }

    @Override
    public void generateSubmissionFormXml(File xlsFile, File outputFile, String locale)
            throws BiffException, IOException {
        InputFormDefinitions formDefinitions = new InputFormDefinitions(i18nService);
        InputFormValuePairs formValuePairs = new InputFormValuePairs(i18nService);

        Element root = new Element(INPUT_FORMS_ROOT);
        Element formDefinitionsEl = new Element("form-definitions");
        Element formValuePairsEl = new Element("form-value-pairs");

        DocType dt = new DocType(INPUT_FORMS_ROOT, SUBMISSION_FORMS_DTD);
        Document doc = new Document(root, dt);

        formDefinitions.create(formDefinitionsEl, xlsFile, locale);
        formValuePairs.create(formValuePairsEl, xlsFile, locale);

        root.addContent(formDefinitionsEl);
        root.addContent(formValuePairsEl);

        writeDocument(doc, outputFile);
    }

    /**
     * Common method to write XML document to file with UTF-8 encoding and pretty formatting
     *
     * @param doc the XML document to write
     * @param outputFile the file where the XML will be written
     * @throws IOException if there's an I/O error
     */
    private void writeDocument(Document doc, File outputFile) throws IOException {
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            XMLOutputter outputter = new XMLOutputter();
            outputter.setFormat(Format.getPrettyFormat().setEncoding("UTF-8"));
            outputter.output(doc, out);
            out.flush();
        }
    }

    public void setSubmissionMap(InputSubmissionMap submissionMap) {
        this.submissionMap = submissionMap;
    }

    public void setI18nService(SubmissionFormGeneratorI18nService i18nService) {
        this.i18nService = i18nService;
    }

}

