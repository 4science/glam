/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.submission;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.dspace.app.submissionform.script.SubmissionFormGenerator.SUBMISSION_FORM_GENERATOR_SCRIPT_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.junit.Test;

/**
 * Integration test for the `generate-submission-forms` script.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class SubmissionFormGeneratorIT extends AbstractIntegrationTestWithDatabase {

    private static final String BASE_XLS_DIR_PATH = "./target/testing/dspace/assetstore/submission/script";

    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    @Test
    public void generationSubmissionFormsTest() throws Exception {
        Set<String> stepDefinitionsExpected = Set.of("collection", "upload", "license", "orgunit", "detect-duplicate",
                "publication", "publication_indexing", "publication_bibliographic_details", "publication_references",
                "person", "cclicense", "itemAccessConditions", "extractionstep", "correction", "custom-url");

        Set<String> submissionProcessExpected = Set.of("orgunit", "publication", "person");

        String fileLocation = getXlsFilePath("submission-form-test.xls");
        String[] args = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation,
                                                                              "-d", "publication" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, admin);
        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        File exportedFile = new File("submission-forms.zip");
        exportedFile.deleteOnExit();

        assertThat(handler.getFileStream(context, "submission-forms.zip").isPresent(), is(true));

        try (InputStream fisExported = handler.getFileStream(context, "submission-forms.zip").get();
             ZipInputStream zipIn = new ZipInputStream(fisExported)) {

            Map<String, byte[]> zipContents = extractZipContents(zipIn);

            // Verify that zip contains exactly 2 files
            assertThat(zipContents.size(), is(2));
            assertThat(zipContents.containsKey("submission-forms.xml"), is(true));
            assertThat(zipContents.containsKey("item-submission.xml"), is(true));

            // Verify submission-forms.xml content
            verifySubmissionFormsXml(zipContents.get("submission-forms.xml"));
            // Verify item-submission.xml content
            verifyItemSubmissionXml(zipContents.get("item-submission.xml"), stepDefinitionsExpected,
                                                                            submissionProcessExpected);
        }
    }

    @Test
    public void generationSubmissionFormsCompareEntireFileTest() throws Exception {
        String fileLocation = getXlsFilePath("submission-form-full-test.xls");
        String[] args = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation,
                                                                              "-d", "product" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, admin);
        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        File exportedFile = new File("submission-forms.zip");
        exportedFile.deleteOnExit();

        assertThat(handler.getFileStream(context, "submission-forms.zip").isPresent(), is(true));

        String locationItemSubmission = getXlsFilePath("expected-item-submission.xml");
        String locationSubmissionForms = getXlsFilePath("expected-submission-forms.xml");
        try (InputStream fisExported = handler.getFileStream(context, "submission-forms.zip").get();
             ZipInputStream zipIn = new ZipInputStream(fisExported)) {

            Map<String, byte[]> zipContents = extractZipContents(zipIn);
            byte[] actualItemSubmission = zipContents.get("item-submission.xml");
            byte[] actualSubmissionForms = zipContents.get("submission-forms.xml");

            // Convert byte arrays to UTF-8 strings
            String actualItemSubmissionStr = new String(actualItemSubmission, UTF_8).replaceAll("\r\n", "\n");
            String actualSubmissionFormsStr = new String(actualSubmissionForms, UTF_8).replaceAll("\r\n", "\n");

            // Read expected files as UTF-8 strings
            String expectedItemSubmissionStr = Files.readString(Paths.get(locationItemSubmission), UTF_8);
            String expectedSubmissionFormsStr = Files.readString(Paths.get(locationSubmissionForms), UTF_8);

            // Compare the content
            assertThat(actualItemSubmissionStr, is(expectedItemSubmissionStr));
            assertThat(actualSubmissionFormsStr, is(expectedSubmissionFormsStr));
        }
    }

    @Test
    public void failedValidationOfItemControlledVocabularyTest() throws Exception {
        String fileLocation = getXlsFilePath("submission-forms-with-wrong-ItemControlledVocabulary.xls");
        String[] args = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation,
                                                                              "-d", "publication" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, admin);
        assertThat(handler.getInfoMessages().size(), is(3));
        assertThat(handler.getInfoMessages().get(1), is("####     Validation Failed!!!    #####"));
        assertThat(handler.getErrorMessages().size(), is(5));
        assertThat(handler.getErrorMessages().get(0),
                is("LEVEL:WARN ERROR:You have to add the element dc.source.content"));
        assertThat(handler.getErrorMessages().get(1),
                is("LEVEL:WARN ERROR:You have to add the element dc.relation.ispublishedin"));
        assertThat(handler.getErrorMessages().get(2), is("LEVEL:ERROR ERROR:The item : " +
                                    "publication:dc.relation.aggregation has Vocabulary aggregationsTree not found!!"));
        assertThat(handler.getErrorMessages().get(3),
           is("LEVEL:ERROR ERROR:The item : publication:dc.relation.fonds has Vocabulary fondsTree not found!!"));
        assertThat(handler.getErrorMessages().get(4),
                is("InputFormException: Blocking errors found, cannot proceed to XML generation."));
        assertThat(handler.getWarningMessages(), empty());
    }

    @Test
    public void passValidationOfItemControlledVocabularyTest() throws Exception {
        String fileLocation = getXlsFilePath("submission-forms-with-correct-ItemControlledVocabulary.xls");
        String[] args = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation, "-d", "publication" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, admin);

        assertThat(handler.getInfoMessages().size(), is(3));
        assertThat(handler.getInfoMessages().get(1), is("####     Validation Failed!!!    #####"));
        assertThat(handler.getErrorMessages().size(), is(3));
        assertThat(handler.getErrorMessages().get(0),
                is("LEVEL:WARN ERROR:You have to add the element dc.source.content"));
        assertThat(handler.getErrorMessages().get(1),
                is("LEVEL:WARN ERROR:You have to add the element dc.relation.ispublishedin"));
        assertThat(handler.getErrorMessages().get(2),
                is("InputFormException: Blocking errors found, cannot proceed to XML generation."));
        assertThat(handler.getWarningMessages(), empty());
    }

    @Test
    public void forceUploadWithWarningsOnlyTest() throws Exception {
        String fileLocation = getXlsFilePath("submission-forms-with-correct-ItemControlledVocabulary.xls");
        // Use -f flag to force upload even with warnings
        String[] args = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation,
                                                                              "-d", "publication",
                                                                              "-f" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, admin);

        File exportedFile = new File("submission-forms.zip");
        exportedFile.deleteOnExit();

        // With -f and only WARNING (no ERROR), the process should continue
        // Verify that validation failed initially (has warnings) but process continued
        assertThat(handler.getInfoMessages().size(), is(9));
        assertThat(handler.getInfoMessages().get(1), is("####     Validation Failed!!!    #####"));
        assertThat(handler.getInfoMessages().get(4), is("** Created: item-submission.xml **"));
        assertThat(handler.getInfoMessages().get(7), is("** Created: submission-forms.xml **"));

        // With -f flag, warnings that have fixWarn are automatically fixed and removed
        // Verify that no blocking errors occurred (process continued)
        boolean hasBlockingError = handler.getErrorMessages()
                                          .stream()
                                          .anyMatch(msg -> msg.contains("Blocking errors found"));
        assertFalse("Expected no blocking errors with -f flag and only warnings", hasBlockingError);

        // Verify that warnings with fixWarn have been automatically fixed
        // Warnings should not appear in error messages because they were fixed by -f
        boolean hasWarnings = handler.getErrorMessages()
                                     .stream()
                                     .anyMatch(msg -> msg.contains("LEVEL:WARN"));
        assertFalse("Expected no warnings in error messages as -f flag should have fixed them", hasWarnings);

        // Verify that ZIP file was generated
        assertThat(handler.getFileStream(context, "submission-forms.zip").isPresent(), is(true));

        // Verify ZIP contains expected files
        try (InputStream fisExported = handler.getFileStream(context, "submission-forms.zip").get();
             ZipInputStream zipIn = new ZipInputStream(fisExported)) {
            Map<String, byte[]> zipContents = extractZipContents(zipIn);
            assertThat(zipContents.size(), is(2));
            assertThat(zipContents.containsKey("submission-forms.xml"), is(true));
            assertThat(zipContents.containsKey("item-submission.xml"), is(true));
         }
     }

    @Test
    public void executeScriptWithoutDOptionTest() throws Exception {
        String fileLocation = getXlsFilePath("submission-form-test.xls");
        // Execute script without -d option to test reading default definition from Excel
        String[] args = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, admin);
        assertThat(handler.getInfoMessages().get(1), is("####     Validation Success!!!    ####"));
        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        File exportedFile = new File("submission-forms.zip");
        exportedFile.deleteOnExit();

        assertTrue(handler.getFileStream(context, "submission-forms.zip").isPresent());

        // Read the expected default definition from Excel file
        String expectedDefaultDefinition = readDefaultDefinitionFromExcel(fileLocation);

        // Verify that ZIP contains expected files
        try (InputStream fisExported = handler.getFileStream(context, "submission-forms.zip").get();
             ZipInputStream zipIn = new ZipInputStream(fisExported)) {
            Map<String, byte[]> zipContents = extractZipContents(zipIn);
            assertThat(zipContents.size(), is(2));
            assertTrue(zipContents.containsKey("submission-forms.xml"));
            assertTrue(zipContents.containsKey("item-submission.xml"));

            // Verify that the submission-name in item-submission.xml matches the one from Excel
            verifySubmissionNameFromExcel(zipContents.get("item-submission.xml"), expectedDefaultDefinition);
        }
    }

    private String readDefaultDefinitionFromExcel(String excelFilePath) throws Exception {
        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding("UTF-8");
        ws.setSuppressWarnings(true);
        Workbook workbook = Workbook.getWorkbook(new File(excelFilePath), ws);
        Sheet sheet = workbook.getSheet(0);
        String defaultDefinition = sheet.getRow(1)[0].getContents().trim();
        workbook.close();
        return defaultDefinition;
    }

    private void verifySubmissionNameFromExcel(byte[] itemSubmissionContent, String expectedSubmissionName)
            throws Exception {
        SAXBuilder saxBuilder = createNonValidatingSaxBuilder();
        Document document = saxBuilder.build(new ByteArrayInputStream(itemSubmissionContent));
        Element root = document.getRootElement();

        assertThat(root.getName(), is("item-submission"));

        // Verify submission-map element exists
        Element submissionMap = root.getChild("submission-map");
        assertThat(submissionMap, notNullValue());
        assertThat(submissionMap.getChildren("name-map").size(), is(1));

        // Verify that the submission-name matches the one read from Excel
        String actualSubmissionName = submissionMap.getChildren("name-map")
                                                   .get(0)
                                                   .getAttribute("submission-name")
                                                   .getValue();
        assertThat("Submission name should be read from Excel when -d option is not provided",
                   actualSubmissionName, is(expectedSubmissionName));
    }

    private Map<String, byte[]> extractZipContents(ZipInputStream zipIn) throws Exception {
        Map<String, byte[]> contents = new HashMap<>();
        ZipEntry entry;
        while ((entry = zipIn.getNextEntry()) != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            zipIn.transferTo(baos);
            contents.put(entry.getName(), baos.toByteArray());
        }
        return contents;
    }

    private SAXBuilder createNonValidatingSaxBuilder() {
        SAXBuilder saxBuilder = new SAXBuilder();
        saxBuilder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        saxBuilder.setFeature("http://xml.org/sax/features/validation", false);
        return saxBuilder;
    }

    private void verifySubmissionFormsXml(byte[] content) throws Exception {
        SAXBuilder saxBuilder = createNonValidatingSaxBuilder();
        Document document = saxBuilder.build(new ByteArrayInputStream(content));
        Element root = document.getRootElement();

        // Verify root element
        assertThat(root.getName(), is("input-forms"));

        // Verify form-definitions element exists and has content
        Element formDefinitions = root.getChild("form-definitions");
        assertThat(formDefinitions, notNullValue());
        assertThat(formDefinitions.getChildren("form").size(), is(13));

        // Verify form-value-pairs element exists
        Element formValuePairs = root.getChild("form-value-pairs");
        assertThat(formValuePairs, notNullValue());
        assertThat(formValuePairs.getChildren("value-pairs").size(), is(14));
    }

    private void verifyItemSubmissionXml(byte[] content, Set<String> stepDefinitionsExpected,
                                                         Set<String> submissionProcessExpected) throws Exception {
        SAXBuilder saxBuilder = createNonValidatingSaxBuilder();
        Document document = saxBuilder.build(new ByteArrayInputStream(content));
        Element root = document.getRootElement();

        // Verify root element
        assertThat(root.getName(), is("item-submission"));

        // Verify submission-map element exists
        Element submissionMap = root.getChild("submission-map");
        assertThat(submissionMap, notNullValue());
        assertThat(submissionMap.getChildren("name-map").size(), is(1));
        assertThat(submissionMap.getChildren("name-map").get(0).getAttribute("submission-name").getValue(),
                is("publication"));

        // Verify step-definitions element exists and has content
        Element stepDefinitions = root.getChild("step-definitions");
        assertThat(stepDefinitions, notNullValue());
        List<Element> stepDefinitionElements = stepDefinitions.getChildren("step-definition");
        assertThat(stepDefinitionElements.size(), is(15));
        for (Element stepDefElem : stepDefinitionElements) {
            String stepName = stepDefElem.getAttribute("id").getValue();
            // Check that this step name is expected
            assertTrue("StepName: " + stepName + " is absent!", stepDefinitionsExpected.contains(stepName));
        }

        // Verify submission-definitions element exists and has content
        Element submissionDefinitions = root.getChild("submission-definitions");
        assertThat(submissionDefinitions, notNullValue());
        List<Element> submissionDefinitionsElements = submissionDefinitions.getChildren("submission-process");
        assertThat(submissionDefinitionsElements.size(), is(3));
        for (Element submissionDefinitionElem : submissionDefinitionsElements) {
            String processName = submissionDefinitionElem.getAttribute("name").getValue();
            // Check that this step name is expected
            assertTrue("StepName: " + processName + " is absent!", submissionProcessExpected.contains(processName));
        }
    }

    private String getXlsFilePath(String name) {
        return new File(BASE_XLS_DIR_PATH, name).getAbsolutePath();
    }

}
