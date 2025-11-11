/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.findify.s3mock.S3Mock;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.curate.CurationOrchestratorScript;
import org.dspace.scripts.ProcessDSpaceRunnableHandler;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.storage.bitstore.factory.StorageServiceFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Test class for the CurationOrchestratorScript class.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class CurationOrchestratorScriptIT extends AbstractIntegrationTestWithDatabase {

    private static final String S3_ENDPOINT = "http://127.0.0.1:8001";
    public static final int MAX_CONNECTIONS = 5;
    public static final int CONNECTION_TIMEOUT = 1000;
    public static final String BUCKET_OUTPUT = "test-bucket-output";
    public static final String BUCKET_INPUT = "test-bucket-input";

    private S3Mock s3Mock;
    private File s3Directory;
    private AmazonS3 amazonS3Client;
    private S3BitStoreService s3BitStoreServiceMock;
    private MockedStatic<StorageServiceFactory> storageServiceFactoryMockedStatic;

    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    private Collection collection;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();

        s3Directory = new File(System.getProperty("java.io.tmpdir"), "s3TestDir");
        s3Mock = S3Mock.create(8001, s3Directory.getAbsolutePath());
        s3Mock.start();

        amazonS3Client = createAmazonS3Client(S3_ENDPOINT);
        amazonS3Client.createBucket(BUCKET_INPUT);
        amazonS3Client.createBucket(BUCKET_OUTPUT);

        storageServiceFactoryMockedStatic = Mockito.mockStatic(StorageServiceFactory.class);
        StorageServiceFactory storageServiceFactory = mock(StorageServiceFactory.class);
        storageServiceFactoryMockedStatic.when(StorageServiceFactory::getInstance).thenReturn(storageServiceFactory);

        BitstreamStorageServiceImpl bitstreamStorageServiceMock = mock(BitstreamStorageServiceImpl.class);
        when(storageServiceFactory.getBitstreamStorageService()).thenReturn(bitstreamStorageServiceMock);

        s3BitStoreServiceMock = mock(S3BitStoreService.class);
        Map<Integer, BitStoreService> stores = new HashMap<>();
        stores.put(4, s3BitStoreServiceMock);

        when(bitstreamStorageServiceMock.getStores()).thenReturn(stores);

        configurationService.setProperty("curation.s3.bucketName-output", BUCKET_OUTPUT);
        configurationService.setProperty("curation.s3.bucketName-input", BUCKET_INPUT);
        configurationService.setProperty("curation.s3.customer-id", "test-dspace-id");

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        this.collection = CollectionBuilder.createCollection(context, parentCommunity)
                                           .withEntityType("Publication")
                                           .withName("Collection Publications")
                                           .build();
        context.restoreAuthSystemState();
    }

    @After
    public void cleanUp() throws IOException {
        if (s3Mock != null) {
            s3Mock.shutdown();
        }
        if (s3Directory != null && s3Directory.exists()) {
            FileUtils.deleteDirectory(s3Directory);
        }
        if (storageServiceFactoryMockedStatic != null) {
            storageServiceFactoryMockedStatic.close();
        }
    }

    @Test
    public void launchCurationOrchestratorScriptForItemWithSinglePdfTest() throws Exception {
        context.turnOffAuthorisationSystem();
        Item publication = ItemBuilder.createItem(context, collection)
                                      .withTitle("Publication Item test")
                                      .withAuthor("Amlinger, Carolin")
                                      .withCurationTask("pdfATransformer")
                                      .withType("content")
                                      .build();

        String pdfContent = "PDF test content";
        String txtContent = "This is a text file";

        Bitstream bitstream;
        try (InputStream is = IOUtils.toInputStream(pdfContent, "UTF-8");
             InputStream is2 = IOUtils.toInputStream(txtContent, "UTF-8")) {
            bitstream = BitstreamBuilder.createBitstream(context, publication, is)
                                        .withName("test.pdf")
                                        .withMimeType("application/pdf")
                                        .withStoreNumber(4)
                                        .build();
            BitstreamBuilder.createBitstream(context, publication, is2)
                            .withName("test.txt")
                            .withStoreNumber(4)
                            .withMimeType("text/plain")
                            .build();
        }
        context.commit();

        // Simulate the output of the serverless function by uploading the expected JSON and PDF/A files to S3
        InputStream jsonInputStream = generateOutputJSON(bitstream, "Test.pdf");
        String keyForJSON = String.format("1/%s-pdfATransformer.json", bitstream.getID());
        amazonS3Client.putObject(BUCKET_OUTPUT, keyForJSON, jsonInputStream, new ObjectMetadata());

        String keyForPDFA = "results/Test.pdf";
        InputStream pdfaInputStream = generatePDFA("This is a PDF/A file content");
        amazonS3Client.putObject(BUCKET_OUTPUT, keyForPDFA, pdfaInputStream, new ObjectMetadata());

        // Verify that the output JSON objects have been uploaded
        assertTrue(amazonS3Client.doesObjectExist(BUCKET_OUTPUT, keyForJSON));
        // The PDF/A file is uploaded to the "results/" folder inside the bucket
        assertTrue(amazonS3Client.doesObjectExist(BUCKET_OUTPUT, keyForPDFA));

        // Mock S3BitStoreService methods
        when(s3BitStoreServiceMock.getBucketName()).thenReturn("test-bucket-input");
        when(s3BitStoreServiceMock.getRelativePath(bitstream.getInternalId())).thenReturn("relative-path/test.pdf");

        context.restoreAuthSystemState();

        // Ensure no PDFA bundle exists before running the script
        List<Bundle> pdfaBudles = publication.getBundles("PDFA");
        assertEquals(0, pdfaBudles.size());

        // Run the Curation Orchestrator Script
        String scriptName = "curateOrchestrator";
        String[] args = new String[] { scriptName, "-t", "pdfATransformer", "-id", publication.getID().toString() };

        ProcessDSpaceRunnableHandler handlerMock = mock(ProcessDSpaceRunnableHandler.class);
        when(handlerMock.getProcessId()).thenReturn(1);
        CurationOrchestratorScript curationOrchestratorScript = new CurationOrchestratorScript();
        curationOrchestratorScript.initialize(args, handlerMock, admin);
        curationOrchestratorScript.setS3Client(amazonS3Client);
        curationOrchestratorScript.run();

        publication = context.reloadEntity(publication);
        // Verify that the PDFA bundle has been created
        pdfaBudles = publication.getBundles("PDFA");
        assertEquals(1, pdfaBudles.size());
        // Verify that the PDFA bundle contains one bitstream
        List<Bitstream> convertedPDF = pdfaBudles.get(0).getBitstreams();
        assertEquals(1, convertedPDF.size());
        // Verify that the new bitstream has been created with the expected SequenceID
        assertEquals(bitstream.getSequenceID(), convertedPDF.get(0).getSequenceID());
        // Verify that the new bitstream has been created with the expected name
        assertEquals(bitstream.getName(), convertedPDF.get(0).getName());
    }

    @Test
    public void launchCurationOrchestratorScriptForItemWithMultiplePdfTest() throws Exception {
        context.turnOffAuthorisationSystem();
        Item publication = ItemBuilder.createItem(context, collection)
                                      .withTitle("Publication Item test")
                                      .withAuthor("Andrea, Boldrin")
                                      .withCurationTask("pdfATransformer")
                                      .withType("content")
                                      .build();

        String pdfContent = "PDF 1 test-content";
        String txtContent = "This is a text file";
        String pdfContent2 = "PDF 2 Test";

        Bitstream bitstream1;
        Bitstream bitstream2;
        try (InputStream is1 = IOUtils.toInputStream(pdfContent, "UTF-8");
             InputStream is2 = IOUtils.toInputStream(txtContent, "UTF-8");
             InputStream is3 = IOUtils.toInputStream(pdfContent2, "UTF-8")) {
            bitstream1 = BitstreamBuilder.createBitstream(context, publication, is1)
                                         .withName("my-test.pdf")
                                         .withMimeType("application/pdf")
                                         .withStoreNumber(4)
                                         .build();
            BitstreamBuilder.createBitstream(context, publication, is2)
                            .withName("test.txt")
                            .withStoreNumber(4)
                            .withMimeType("text/plain")
                            .build();
            bitstream2 = BitstreamBuilder.createBitstream(context, publication, is3)
                                         .withName("mySecondTest.pdf")
                                         .withMimeType("application/pdf")
                                         .withStoreNumber(4)
                                         .build();
        }
        context.commit();

        // Simulate the output of the serverless function by uploading the expected JSON and PDF/A files to S3
        InputStream jsonInputStream = generateOutputJSON(bitstream1, "my-output-test.pdf");
        String keyForJSON = String.format("1/%s-pdfATransformer.json", bitstream1.getID());
        amazonS3Client.putObject(BUCKET_OUTPUT, keyForJSON, jsonInputStream, new ObjectMetadata());

        String keyForPDFA = "results/my-output-test.pdf";
        InputStream pdfaInputStream = generatePDFA("This is a PDF/A file content 4 bitstream 1");
        amazonS3Client.putObject(BUCKET_OUTPUT, keyForPDFA, pdfaInputStream, new ObjectMetadata());

        InputStream jsonInputStream2 = generateOutputJSON(bitstream2, "mySecondTest-output.pdf");
        String keyForJSON2 = String.format("1/%s-pdfATransformer.json", bitstream2.getID());
        amazonS3Client.putObject(BUCKET_OUTPUT, keyForJSON2, jsonInputStream2, new ObjectMetadata());

        String keyForPDFA2 = "results/mySecondTest-output.pdf";
        InputStream pdfaInputStream2 = generatePDFA("This is a PDF/A file content 4 bitstream 2");
        amazonS3Client.putObject(BUCKET_OUTPUT, keyForPDFA2, pdfaInputStream2, new ObjectMetadata());

        // Verify that the output JSON objects have been uploaded
        assertTrue(amazonS3Client.doesObjectExist(BUCKET_OUTPUT, keyForJSON));
        assertTrue(amazonS3Client.doesObjectExist(BUCKET_OUTPUT, keyForJSON2));
        // The PDF/A file is uploaded to the "results/" folder inside the bucket
        assertTrue(amazonS3Client.doesObjectExist(BUCKET_OUTPUT, keyForPDFA));
        assertTrue(amazonS3Client.doesObjectExist(BUCKET_OUTPUT, keyForPDFA2));

        // Mock S3BitStoreService methods
        when(s3BitStoreServiceMock.getBucketName()).thenReturn("test-bucket-input", "test-bucket-input");
        when(s3BitStoreServiceMock.getRelativePath(bitstream1.getInternalId())).thenReturn("relative-path/my-test.pdf");
        when(s3BitStoreServiceMock.getRelativePath(bitstream2.getInternalId()))
        .thenReturn("relative-path/mySecondTest.pdf");

        context.restoreAuthSystemState();

        // Ensure no PDFA bundle exists before running the script
        List<Bundle> pdfaBudles = publication.getBundles("PDFA");
        assertEquals(0, pdfaBudles.size());

        // Run the Curation Orchestrator Script
        String scriptName = "curateOrchestrator";
        String[] args = new String[] { scriptName, "-t", "pdfATransformer", "-id", publication.getID().toString() };

        ProcessDSpaceRunnableHandler handlerMock = mock(ProcessDSpaceRunnableHandler.class);
        when(handlerMock.getProcessId()).thenReturn(1, 1);

        CurationOrchestratorScript curationOrchestratorScript = new CurationOrchestratorScript();
        curationOrchestratorScript.initialize(args, handlerMock, admin);
        curationOrchestratorScript.setS3Client(amazonS3Client);
        curationOrchestratorScript.run();

        publication = context.reloadEntity(publication);
        // Verify that the PDFA bundle has been created
        pdfaBudles = publication.getBundles("PDFA");
        assertEquals(1, pdfaBudles.size());
        // Verify that the PDFA bundle contains one bitstream
        List<Bitstream> convertedPDF = pdfaBudles.get(0).getBitstreams();
        assertEquals(2, convertedPDF.size());
        // Verify that the new bitstreams has been created with the expected SequenceID
        assertEquals(bitstream1.getSequenceID(), convertedPDF.get(0).getSequenceID());
        assertEquals(bitstream2.getSequenceID(), convertedPDF.get(1).getSequenceID());
        // Verify that the new bitstreams has been created with the expected name
        assertEquals(bitstream1.getName(), convertedPDF.get(0).getName());
        assertEquals(bitstream2.getName(), convertedPDF.get(1).getName());
    }

    @Test
    public void launchCurationOrchestratorScriptForItemWithNoPDFaGeneratedTest() throws Exception {
        context.turnOffAuthorisationSystem();
        Item publication = ItemBuilder.createItem(context, collection)
                                      .withTitle("Publication Item test")
                                      .withAuthor("Andrea, Boldrin")
                                      .withCurationTask("pdfATransformer")
                                      .withType("content")
                                      .build();

        String pdfContent = "PDF 1 test-content";
        Bitstream bitstream1;
        try (InputStream is1 = IOUtils.toInputStream(pdfContent, "UTF-8")) {
            bitstream1 = BitstreamBuilder.createBitstream(context, publication, is1)
                                         .withName("my-test.pdf")
                                         .withMimeType("application/pdf")
                                         .withStoreNumber(4)
                                         .build();
        }
        context.commit();

        // Mock S3BitStoreService methods
        when(s3BitStoreServiceMock.getBucketName()).thenReturn("test-bucket-input");
        when(s3BitStoreServiceMock.getRelativePath(bitstream1.getInternalId())).thenReturn("relative-path/my-test.pdf");

        context.restoreAuthSystemState();

        // Ensure no PDFA bundle exists before running the script
        List<Bundle> pdfaBudles = publication.getBundles("PDFA");
        assertEquals(0, pdfaBudles.size());

        // Run the Curation Orchestrator Script
        String scriptName = "curateOrchestrator";
        String[] args = new String[] { scriptName, "-t", "pdfATransformer", "-id", publication.getID().toString() };

        TestDSpaceRunnableHandler testDSpaceRunnableHandler = new TestDSpaceRunnableHandler();
        CurationOrchestratorScript curationOrchestratorScript = new CurationOrchestratorScript();
        curationOrchestratorScript.initialize(args, testDSpaceRunnableHandler, admin);

        String keyForJSON = String.format("%s/%s-pdfATransformer.json", curationOrchestratorScript.getProcessRundomId(),
                                                                        bitstream1.getID());
        try (InputStream jsonInputStream = generateFailedOutputJSON(bitstream1)) {
            amazonS3Client.putObject(BUCKET_OUTPUT, keyForJSON, jsonInputStream, new ObjectMetadata());
            // Verify that the output JSON objects have been uploaded
            assertTrue(amazonS3Client.doesObjectExist(BUCKET_OUTPUT, keyForJSON));

            curationOrchestratorScript.setS3Client(amazonS3Client);
            curationOrchestratorScript.run();

            publication = context.reloadEntity(publication);

            // Verify that the PDFA bundle has not been created
            pdfaBudles = publication.getBundles("PDFA");
            assertEquals(0, pdfaBudles.size());

            // Verify error messages
            List<String> errors = testDSpaceRunnableHandler.getErrorMessages();
            assertEquals(3, errors.size());
            assertEquals("Serverless tasks failed:1", errors.get(0));
            var expectedError = String.format("FAILED: Serverless task:pdfATransformer, with error:Validation error:" +
                            " file is not PDF/A compliant , for bitstreams: , and origin bitstream:%s .",
                    bitstream1.getID());
            assertEquals(expectedError, errors.get(1));
            assertEquals("RuntimeException: Some curation tasks failed. Check logs for details.", errors.get(2));
        }
    }

    private InputStream generateOutputJSON(Bitstream bitstream, String name) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();
        json.put("output_path", "results/" + name);
        json.put("error", "");
        json.put("uuid", bitstream.getID().toString());
        json.put("status", "success");
        return new ByteArrayInputStream(mapper.writeValueAsBytes(json));
    }

    private InputStream generateFailedOutputJSON(Bitstream bitstream) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();
        json.put("output_path", "");
        json.put("error", "Validation error: file is not PDF/A compliant");
        json.put("uuid", bitstream.getID().toString());
        json.put("status", "failed");
        return new ByteArrayInputStream(mapper.writeValueAsBytes(json));
    }

    private InputStream generatePDFA(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private AmazonS3 createAmazonS3Client(String endpoint) {
        return AmazonS3ClientBuilder.standard()
                .withPathStyleAccessEnabled(true)
                .withEndpointConfiguration(
                    new com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration(
                        endpoint, Regions.DEFAULT_REGION.getName()))
                .withCredentials(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()))
                .withClientConfiguration(createClientConfiguration())
                .build();
    }

    private ClientConfiguration createClientConfiguration() {
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setMaxConnections(MAX_CONNECTIONS);
        clientConfiguration.setConnectionTimeout(CONNECTION_TIMEOUT);
        return clientConfiguration;
    }

}
