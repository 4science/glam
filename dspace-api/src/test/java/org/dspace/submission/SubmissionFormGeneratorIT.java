/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.submission;

import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.dspace.app.submissionform.script.SubmissionFormGenerator.SUBMISSION_FORM_GENERATOR_SCRIPT_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static java.nio.charset.StandardCharsets.UTF_8;

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

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.content.authority.ItemControlledVocabularyService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.junit.After;
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
        assertThat(handler.getFileStream(context, "submission-forms.zip").isPresent(), is(true));

        String locationItemSubmission = getXlsFilePath("expected-item-submission.xml");
        String locationSubmissionForms = getXlsFilePath("expected-submission-forms.xml");
        try (InputStream fisExported = handler.getFileStream(context, "submission-forms.zip").get();
             ZipInputStream zipIn = new ZipInputStream(fisExported)) {

            Map<String, byte[]> zipContents = extractZipContents(zipIn);
            byte[] actualItemSubmission = zipContents.get("item-submission.xml");
            byte[] actualSubmissionForms = zipContents.get("submission-forms.xml");

            // Convert byte arrays to UTF-8 strings
            String actualItemSubmissionStr = new String(actualItemSubmission, UTF_8);
            String actualSubmissionFormsStr = new String(actualSubmissionForms, UTF_8);

            // Read expected files as UTF-8 strings
            String expectedItemSubmissionStr = Files.readString(Paths.get(locationItemSubmission), UTF_8);
            String expectedSubmissionFormsStr = Files.readString(Paths.get(locationSubmissionForms), UTF_8);

            // Compare the content
            assertThat(actualItemSubmissionStr, is(expectedItemSubmissionStr));
            assertThat(actualSubmissionFormsStr, is(expectedSubmissionFormsStr));
        }
    }

    @Test
    public void faildValidationOfItemControlledVocabularyTest() throws Exception {
        String fileLocation = getXlsFilePath("submission-forms-with-ItemControlledVocabulary.xls");
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
        assertFalse(handler.getFileStream(context, "submission-forms.zip").isPresent());
    }

    @Test
    public void passValidationOfItemControlledVocabularyTest() throws Exception {
        String[] originvocabularies = configurationService.getArrayProperty("item.controlled.vocabularies");
        configurationService.setProperty("item.controlled.vocabularies", "aggregationsTree, fondsTree");
        ItemControlledVocabularyService.reloadPluginNames();

        String fileLocation = getXlsFilePath("submission-forms-with-ItemControlledVocabulary.xls");
        String[] args = new String[] { SUBMISSION_FORM_GENERATOR_SCRIPT_NAME, "-e", fileLocation, "-d", "publication" };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        try {
            handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, admin);
        } finally {
            configurationService.setProperty("item.controlled.vocabularies", originvocabularies);
            ItemControlledVocabularyService.reloadPluginNames();
        }

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

        assertFalse(handler.getFileStream(context, "submission-forms.zip").isPresent());
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

    @After
    @Override
    public void destroy() throws Exception {
        super.destroy();
        ItemControlledVocabularyService.reloadPluginNames();
    }

}
