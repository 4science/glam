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
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
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
import org.dspace.app.submissionform.script.service.SubmissionFormGeneratorI18nService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.kernel.ServiceManager;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration test for the `generate-submission-forms` script.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class SubmissionFormGeneratorIT extends AbstractIntegrationTestWithDatabase {

    private static final String BASE_XLS_DIR_PATH = "assetstore/submission/script";

    private ConfigurationService configurationService;
    private SubmissionFormGeneratorI18nService i18nService;

    @Before
    public void before() throws Exception {
        ServiceManager sm = new DSpace().getServiceManager();
        this.configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        this.i18nService = sm.getServiceByName("submissionFormGeneratorI18nService",
                                               SubmissionFormGeneratorI18nService.class);
    }

    @After
    public void after() throws SQLException, AuthorizeException {
        File zipFile = new File("submission-forms.zip");
        if (zipFile.exists()) {
            zipFile.delete();
        }
    }

    @Test
    public void testGenerateSubmissionForms() throws Exception {
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

        // Verify that the ZIP file was created
        assertTrue(handler.getFileStream(context, "submission-forms.zip").isPresent());

        try (InputStream fisExported = handler.getFileStream(context, "submission-forms.zip").get();
             ZipInputStream zipIn = new ZipInputStream(fisExported)) {

            Map<String, byte[]> zipContents = extractZipContents(zipIn);

            // Verify that zip contains exactly 2 files
            assertThat(zipContents.size(), is(2));
            assertThat(zipContents.containsKey("submission-forms.xml"), is(true));
            assertThat(zipContents.containsKey("item-submission.xml"), is(true));

            // Verify submission-forms.xml content
            verifySubmissionFormsXml(zipContents.get("submission-forms.xml"), 13, 14);
            // Verify item-submission.xml content
            verifyItemSubmissionXml(zipContents.get("item-submission.xml"), stepDefinitionsExpected,
                                                                            submissionProcessExpected);
        }
    }

    @Test
    public void testGenerateSubmissionFormsAndCompareWithExpectedFiles() throws Exception {
        String fileLocation = getXlsFilePath("submission-form-full-test.xls");
        String[] args = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation,
                                                                              "-d", "product" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, admin);
        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        // Verify that the ZIP file was created
        assertTrue(handler.getFileStream(context, "submission-forms.zip").isPresent());

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
    public void testValidationFailsWithInvalidItemControlledVocabulary() throws Exception {
        String fileLocation = getXlsFilePath("submission-forms-with-wrong-ItemControlledVocabulary.xls");
        String[] args = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation,
                                                                              "-d", "publication" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, admin);
        assertThat(handler.getWarningMessages().size(), is(3));
        assertThat(handler.getWarningMessages().get(1), is("####     Validation Failed!!!    #####"));
        assertThat(handler.getErrorMessages().size(), is(6));
        assertThat(handler.getErrorMessages().get(0),
                is("LEVEL:WARN ERROR:You have to add the element dc.source.test"));
        assertThat(handler.getErrorMessages().get(1),
                is("LEVEL:WARN ERROR:You have to add the element dc.relation.ispublishedon"));
        assertThat(handler.getErrorMessages().get(2), is("LEVEL:ERROR ERROR:The item : " +
                                    "publication:dc.relation.aggregation has Vocabulary aggregationsTree not found!!"));
        assertThat(handler.getErrorMessages().get(3),
           is("LEVEL:ERROR ERROR:The item : publication:dc.relation.fonds has Vocabulary fondsTree not found!!"));
        assertThat(handler.getErrorMessages().get(4),
                is("Cannot proceed to XML generation due to errors in the input Excel file."));
        assertThat(handler.getInfoMessages(), empty());

        // Verify that the ZIP file was NOT generated
        assertFalse(handler.getFileStream(context, "submission-forms.zip").isPresent());
    }

    @Test
    public void testValidationFailsWithCorrectVocabularyButMissingRequiredElements() throws Exception {
        String fileLocation = getXlsFilePath("submission-forms-with-correct-ItemControlledVocabulary.xls");
        String[] args = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation, "-d", "publication" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, admin);

        assertThat(handler.getWarningMessages().size(), is(3));
        assertThat(handler.getWarningMessages().get(1), is("####     Validation Failed!!!    #####"));
        assertThat(handler.getErrorMessages().size(), is(4));
        assertThat(handler.getErrorMessages().get(0),
                is("LEVEL:WARN ERROR:You have to add the element dc.source.content"));
        assertThat(handler.getErrorMessages().get(1),
                is("LEVEL:WARN ERROR:You have to add the element dc.relation.ispublishedin"));
        assertThat(handler.getErrorMessages().get(2),
                is("Cannot proceed to XML generation due to errors in the input Excel file."));
        assertThat(handler.getInfoMessages(), empty());
        // Verify that the ZIP file was NOT generated
        assertFalse(handler.getFileStream(context, "submission-forms.zip").isPresent());
    }

    @Test
    public void testForceGenerationWithWarningsOnly() throws Exception {
        String fileLocation = getXlsFilePath("submission-forms-with-warnings.xls");

        // First execution: without -f to show validation errors
        String[] argsWithoutForce = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation,
                                                                                          "-d", "publication" };
        TestDSpaceRunnableHandler handlerWithoutForce = new TestDSpaceRunnableHandler();
        handleScript(argsWithoutForce, ScriptLauncher.getConfig(kernelImpl), handlerWithoutForce, kernelImpl, admin);

        // Verify that validation failed
        assertThat(handlerWithoutForce.getWarningMessages().size(), is(3));
        assertThat(handlerWithoutForce.getWarningMessages().get(0), is("######################################"));
        assertThat(handlerWithoutForce.getWarningMessages().get(1), is("####     Validation Failed!!!    #####"));
        assertThat(handlerWithoutForce.getWarningMessages().get(2), is("######################################"));

        // Verify that there are validation errors (warnings)
        assertThat(handlerWithoutForce.getErrorMessages().size(), is(4));
        assertThat(handlerWithoutForce.getErrorMessages().get(0),
                is("LEVEL:WARN ERROR:You have to add the element dc.content.content"));
        assertThat(handlerWithoutForce.getErrorMessages().get(1),
                is("LEVEL:WARN ERROR:You have to add the element dc.content.ispublishedin"));
        assertThat(handlerWithoutForce.getErrorMessages().get(2),
                is("Cannot proceed to XML generation due to errors in the input Excel file."));

        // Verify that the ZIP file was NOT generated
        assertFalse(handlerWithoutForce.getFileStream(context, "submission-forms.zip").isPresent());

        // Second execution: with -f to show the effect of -f option
        String[] argsWithForce = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation,
                                                                                       "-d", "publication",
                                                                                       "-f" };
        TestDSpaceRunnableHandler handlerWithForce = new TestDSpaceRunnableHandler();
        handleScript(argsWithForce, ScriptLauncher.getConfig(kernelImpl), handlerWithForce, kernelImpl, admin);

        // With -f and only WARNING (no ERROR), the process should continue
        // to Verify that validation failed initially (has warnings) but process continued
        assertThat(handlerWithForce.getWarningMessages().size(), is(3));
        assertThat(handlerWithForce.getWarningMessages().get(0), is("######################################"));
        assertThat(handlerWithForce.getWarningMessages().get(1), is("####     Validation Failed!!!    #####"));
        assertThat(handlerWithForce.getWarningMessages().get(2), is("######################################"));
        // Verify info messages about created files
        assertThat(handlerWithForce.getInfoMessages().size(), is(8));
        assertThat(handlerWithForce.getInfoMessages().get(0), is("**********************************"));
        assertThat(handlerWithForce.getInfoMessages().get(1), is("** Created: item-submission.xml **"));
        assertThat(handlerWithForce.getInfoMessages().get(2), is("**********************************"));
        assertThat(handlerWithForce.getInfoMessages().get(3), is("***********************************"));
        assertThat(handlerWithForce.getInfoMessages().get(4), is("** Created: submission-forms.xml **"));
        assertThat(handlerWithForce.getInfoMessages().get(5), is("***********************************"));
        assertThat(handlerWithForce.getInfoMessages().get(6), startsWith("Adding file to zip: /tmp/submission-forms"));
        assertThat(handlerWithForce.getInfoMessages().get(7), startsWith("Adding file to zip: /tmp/item-submission"));

        // Verify that there are NO validation errors now
        assertThat(handlerWithForce.getErrorMessages().size(), is(0));

        // Verify that ZIP file was generated
        assertTrue(handlerWithForce.getFileStream(context, "submission-forms.zip").isPresent());

        // Verify ZIP contains expected files
        try (InputStream fisExported = handlerWithForce.getFileStream(context, "submission-forms.zip").get();
             ZipInputStream zipIn = new ZipInputStream(fisExported)) {
            Map<String, byte[]> zipContents = extractZipContents(zipIn);
            assertThat(zipContents.size(), is(2));
            assertThat(zipContents.containsKey("submission-forms.xml"), is(true));
            assertThat(zipContents.containsKey("item-submission.xml"), is(true));
         }
     }

    @Test
    public void testExecuteScriptWithoutDOption() throws Exception {
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

    @Test
    public void testGenerateSubmissionFormsWithCustomOutputPath() throws Exception {
        String fileLocation = getXlsFilePath("submission-form-test.xls");
        // Create a temporary directory for custom output path
        File tempDir = Files.createTempDirectory("custom-path-test").toFile();
        tempDir.deleteOnExit();
        String customOutputPath = tempDir.getAbsolutePath() + File.separator;

        String[] args = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation,
                                                                              "-d", "publication",
                                                                              "-p", customOutputPath };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, admin);
        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        // Verify that XML files were created in the custom output path
        File zipFile = new File(customOutputPath + "submission-forms.zip");

        assertTrue("submission-forms.zip should exist in custom output path", zipFile.exists());
        assertTrue("submission-forms.zip should be readable", zipFile.canRead());
        zipFile.delete();
    }

    @Test
    public void testGenerateSubmissionFormsWithExtraLanguages() throws Exception {
        var originLanguages = List.of(configurationService.getArrayProperty("inputforms.additional-languages"));

        configurationService.setProperty("inputforms.additional-languages", "de");
        i18nService.reloadExtraLanguages();

        String fileLocation = getXlsFilePath("submission-forms-with-extra-language.xls");
        String[] args = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation, "-d", "publication" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, admin);
        assertThat(handler.getErrorMessages(), empty());
        assertThat(handler.getWarningMessages(), empty());

        // Verify that the ZIP file was created
        assertTrue(handler.getFileStream(context, "submission-forms.zip").isPresent());

        try (InputStream fisExported = handler.getFileStream(context, "submission-forms.zip").get();
             ZipInputStream zipIn = new ZipInputStream(fisExported)) {

            Map<String, byte[]> zipContents = extractZipContents(zipIn);

            // Verify that zip contains base files
            assertThat(zipContents.containsKey("submission-forms.xml"), is(true));
            assertThat(zipContents.containsKey("item-submission.xml"), is(true));

            var extraLanguages = List.of(configurationService.getArrayProperty("inputforms.additional-languages"));
            // Verify that zip contains files for each extra language
            for (String extraLanguage : extraLanguages) {
                String expectedFileName = "submission-forms_" + extraLanguage + ".xml";
                assertTrue("ZIP should contain file for extra language: " + extraLanguage,
                          zipContents.containsKey(expectedFileName));

                // Verify that the file is not empty
                byte[] extraLanguageContent = zipContents.get(expectedFileName);
                assertTrue("File for extra language " + extraLanguage + " should not be empty",
                          extraLanguageContent.length > 0);

                // Verify that the file is valid XML
                verifySubmissionFormsXml(extraLanguageContent, 2,4);

                // Read the content and compare with expected file
                // Convert byte arrays to UTF-8 strings
                String actualItemSubmissionStr = new String(extraLanguageContent, UTF_8).replaceAll("\r\n", "\n");
                // Read expected files as UTF-8 strings
                String locationSubmissionFormsDe = getXlsFilePath("expected-submission-forms_de.xml");
                String expectedSubmissionFormsStr = Files.readString(Paths.get(locationSubmissionFormsDe), UTF_8)
                                                                       .replaceAll("\r\n", "\n");

                // Compare the content
                assertThat(actualItemSubmissionStr, is(expectedSubmissionFormsStr));
            }

            // Verify total number of files: 2 base files + number of extra languages
            int expectedFileCount = 2 + extraLanguages.size();
            assertThat("ZIP should contain " + expectedFileCount + " files",
                      zipContents.size(), is(expectedFileCount));
        } finally {
            // Restore original languages configuration
            configurationService.setProperty("inputforms.additional-languages", originLanguages);
            i18nService.reloadExtraLanguages();
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

    private void verifySubmissionFormsXml(byte[] content, int totForm, int totValuePairs) throws Exception {
        SAXBuilder saxBuilder = createNonValidatingSaxBuilder();
        Document document = saxBuilder.build(new ByteArrayInputStream(content));
        Element root = document.getRootElement();

        // Verify root element
        assertThat(root.getName(), is("input-forms"));

        // Verify form-definitions element exists and has content
        Element formDefinitions = root.getChild("form-definitions");
        assertThat(formDefinitions, notNullValue());
        assertThat(formDefinitions.getChildren("form").size(), is(totForm));

        // Verify form-value-pairs element exists
        Element formValuePairs = root.getChild("form-value-pairs");
        assertThat(formValuePairs, notNullValue());
        assertThat(formValuePairs.getChildren("value-pairs").size(), is(totValuePairs));
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

    private String getXlsFilePathEX(String name) {
        return new File(BASE_XLS_DIR_PATH, name).getAbsolutePath();
    }

    private String getXlsFilePath(String filename) {
        var dspaceDir = configurationService.getProperty("dspace.dir");
        return String.format("%s/%s/%s", dspaceDir, BASE_XLS_DIR_PATH , filename);
    }

}
