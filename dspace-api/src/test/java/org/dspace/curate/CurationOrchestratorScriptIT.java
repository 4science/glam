/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.dspace.scripts.ProcessDSpaceRunnableHandler;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.storage.bitstore.BitStoreService;
import org.dspace.storage.bitstore.BitstreamStorageServiceImpl;
import org.dspace.storage.bitstore.S3BitStoreService;
import org.dspace.storage.bitstore.factory.StorageServiceFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Test class for the CurationOrchestratorScript class.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class CurationOrchestratorScriptIT extends AbstractIntegrationTestWithDatabase {

    public static final String BUCKET_OUTPUT = "test-bucket-output";
    public static final String BUCKET_INPUT = "test-bucket-input";

    private static S3MockContainer s3Mock = new S3MockContainer("4.8.0");
    private File s3Directory;
    private static S3AsyncClient s3AsyncClient;
    private S3BitStoreService s3BitStoreServiceMock;
    private MockedStatic<StorageServiceFactory> storageServiceFactoryMockedStatic;

    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    private Collection collection;

    @BeforeClass
    public static void setupS3() {
        s3Mock.start();
        s3AsyncClient = createAmazonS3Client("http://127.0.0.1:" + s3Mock.getHttpServerPort());
        s3AsyncClient.createBucket(b -> b.bucket(BUCKET_INPUT)).join();
        s3AsyncClient.createBucket(b -> b.bucket(BUCKET_OUTPUT)).join();
    }

    @AfterClass
    public static void cleanupS3() {
        s3Mock.close();
        s3AsyncClient.close();
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();

        s3Directory = new File(System.getProperty("java.io.tmpdir"), "s3TestDir");

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
        uploadObject(s3AsyncClient, BUCKET_OUTPUT, keyForJSON, jsonInputStream);

        String keyForPDFA = "results/Test.pdf";
        InputStream pdfaInputStream = generatePDFA("This is a PDF/A file content");
        uploadObject(s3AsyncClient, BUCKET_OUTPUT, keyForPDFA, pdfaInputStream);

        // Verify that the output JSON objects have been uploaded
        assertTrue(objectExists(s3AsyncClient, BUCKET_OUTPUT, keyForJSON));
        // The PDF/A file is uploaded to the "results/" folder inside the bucket
        assertTrue(objectExists(s3AsyncClient, BUCKET_OUTPUT, keyForPDFA));

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
        CurationOrchestratorScript curationOrchestratorScript = new CurationOrchestratorScript(this.s3AsyncClient);
        curationOrchestratorScript.initialize(args, handlerMock, admin);
        curationOrchestratorScript.run();

        publication = context.reloadEntity(publication);
        // Verify that the PDFA bundle has been created
        pdfaBudles = publication.getBundles("PDFA");
        assertEquals(1, pdfaBudles.size());
        // Verify that the PDFA bundle contains one bitstream
        List<Bitstream> convertedPDF = pdfaBudles.get(0).getBitstreams();
        assertEquals(1, convertedPDF.size());
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
        uploadObject(s3AsyncClient, BUCKET_OUTPUT, keyForJSON, jsonInputStream);

        String keyForPDFA = "results/my-output-test.pdf";
        InputStream pdfaInputStream = generatePDFA("This is a PDF/A file content 4 bitstream 1");
        uploadObject(s3AsyncClient, BUCKET_OUTPUT, keyForPDFA, pdfaInputStream);

        InputStream jsonInputStream2 = generateOutputJSON(bitstream2, "mySecondTest-output.pdf");
        String keyForJSON2 = String.format("1/%s-pdfATransformer.json", bitstream2.getID());
        uploadObject(s3AsyncClient, BUCKET_OUTPUT, keyForJSON2, jsonInputStream2);

        String keyForPDFA2 = "results/mySecondTest-output.pdf";
        InputStream pdfaInputStream2 = generatePDFA("This is a PDF/A file content 4 bitstream 2");
        uploadObject(s3AsyncClient, BUCKET_OUTPUT, keyForPDFA2, pdfaInputStream2);

        // Verify that the output JSON objects have been uploaded
        assertTrue(objectExists(s3AsyncClient, BUCKET_OUTPUT, keyForJSON));
        assertTrue(objectExists(s3AsyncClient, BUCKET_OUTPUT, keyForJSON2));
        // The PDF/A file is uploaded to the "results/" folder inside the bucket
        assertTrue(objectExists(s3AsyncClient, BUCKET_OUTPUT, keyForPDFA));
        assertTrue(objectExists(s3AsyncClient, BUCKET_OUTPUT, keyForPDFA2));

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

        CurationOrchestratorScript curationOrchestratorScript = new CurationOrchestratorScript(this.s3AsyncClient);
        curationOrchestratorScript.initialize(args, handlerMock, admin);
        curationOrchestratorScript.setS3AsyncClient(s3AsyncClient);
        curationOrchestratorScript.run();

        publication = context.reloadEntity(publication);
        // Verify that the PDFA bundle has been created
        pdfaBudles = publication.getBundles("PDFA");
        assertEquals(1, pdfaBudles.size());
        // Verify that the PDFA bundle contains one bitstream
        List<Bitstream> convertedPDF = pdfaBudles.get(0).getBitstreams();
        assertEquals(2, convertedPDF.size());
        // Verify that the new bitstreams has been created with the expected name and order
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
        CurationOrchestratorScript curationOrchestratorScript = new CurationOrchestratorScript(this.s3AsyncClient);
        curationOrchestratorScript.initialize(args, testDSpaceRunnableHandler, admin);

        String keyForJSON = String.format("%s/%s-pdfATransformer.json", curationOrchestratorScript.getProcessRandomId(),
                                                                        bitstream1.getID());
        try (InputStream jsonInputStream = generateFailedOutputJSON(bitstream1)) {
            uploadObject(s3AsyncClient, BUCKET_OUTPUT, keyForJSON, jsonInputStream);
            // Verify that the output JSON objects have been uploaded
            assertTrue(objectExists(s3AsyncClient, BUCKET_OUTPUT, keyForJSON));

            curationOrchestratorScript.setS3AsyncClient(s3AsyncClient);
            curationOrchestratorScript.run();

            publication = context.reloadEntity(publication);

            // Verify that the PDFA bundle has not been created
            pdfaBudles = publication.getBundles("PDFA");
            assertEquals(0, pdfaBudles.size());

            // Verify error messages
            List<String> errors = testDSpaceRunnableHandler.getErrorMessages();
            assertEquals(2, errors.size());
            var expectedError =
                String.format("FAILED Execution of curation-task pdfATransformer for bitstream %s with error: " +
                              "Validation error: file is not PDF/A compliant ", bitstream1.getID());
            assertEquals(expectedError, errors.get(0));
            assertEquals("RuntimeException: Some curation tasks failed. Check logs for details.", errors.get(1));
        }
    }

    @Test
    public void bitstreamWithExcludeMetadataIsNotProcessedTest() throws Exception {
        configurationService.setProperty("curation.s3.customer-id", "test-dspace-999");
        context.turnOffAuthorisationSystem();
        Item publication = ItemBuilder.createItem(context, collection)
                                      .withTitle("Publication Item test")
                                      .withAuthor("Andrea, Boldrin")
                                      .withCurationTask("pdfATransformer")
                                      .withType("content")
                                      .build();

        String pdfContent = "PDF 1 test-content";
        String pdfContent2 = "PDF 2 Test";
        String pdfContent3 = "PDF 3 Test";

        Bitstream bitstream1;
        Bitstream bitstream2;
        Bitstream bitstream3;
        try (InputStream is1 = IOUtils.toInputStream(pdfContent, "UTF-8");
             InputStream is2 = IOUtils.toInputStream(pdfContent2, "UTF-8");
             InputStream is3 = IOUtils.toInputStream(pdfContent3, "UTF-8")) {
            bitstream1 = BitstreamBuilder.createBitstream(context, publication, is1)
                                         .withName("my-test.pdf")
                                         .withMimeType("application/pdf")
                                         .withStoreNumber(4)
                                         .build();
            bitstream2 = BitstreamBuilder.createBitstream(context, publication, is2)
                                         .withName("mySecondTest.pdf")
                                         .withMimeType("application/pdf")
                                         .withMetadata("bitstream", "curation", "exclude", "pdfATransformer")
                                         .withStoreNumber(4)
                                         .build();
            bitstream3 = BitstreamBuilder.createBitstream(context, publication, is3)
                                         .withName("myThirdTest.pdf")
                                         .withMimeType("application/pdf")
                                         .withMetadata("bitstream", "curation", "exclude", "all")
                                         .withStoreNumber(4)
                                         .build();
        }
        context.commit();

        // Mock S3BitStoreService methods
        when(s3BitStoreServiceMock.getBucketName()).thenReturn("test-bucket-input");
        when(s3BitStoreServiceMock.getRelativePath(bitstream1.getInternalId())).thenReturn("relative-path/my-test");
        when(s3BitStoreServiceMock.getRelativePath(bitstream2.getInternalId())).thenReturn("relative-path/my-2-test");
        when(s3BitStoreServiceMock.getRelativePath(bitstream3.getInternalId())).thenReturn("relative-path/my-3-test");

        context.restoreAuthSystemState();

        // Ensure no PDFA bundle exists before running the script
        List<Bundle> pdfaBudles = publication.getBundles("PDFA");
        assertEquals(0, pdfaBudles.size());

        // Run the Curation Orchestrator Script
        String scriptName = "curateOrchestrator";
        String[] args = new String[] { scriptName, "-t", "pdfATransformer", "-id", publication.getID().toString() };

        ProcessDSpaceRunnableHandler handlerMock = mock(ProcessDSpaceRunnableHandler.class);
        when(handlerMock.getProcessId()).thenReturn(1);

        // Simulate the output of the serverless function by uploading the expected JSON and PDF/A files to S3
        // Only for bitstream1 since bitstream2 should not be processed
        String keyForJSON;
        try (InputStream jsonInputStream = generateOutputJSON(bitstream1, "my-output-test.pdf")) {
            keyForJSON = String.format("1/%s-pdfATransformer.json", bitstream1.getID());
            uploadObject(s3AsyncClient, BUCKET_OUTPUT, keyForJSON, jsonInputStream);
        }

        String keyForPDFA = "results/my-output-test.pdf";
        try (InputStream pdfaInputStream = generatePDFA("This is a PDF/A file content 4 bitstream 1")) {
            uploadObject(s3AsyncClient, BUCKET_OUTPUT, keyForPDFA, pdfaInputStream);
        }

        // Verify that the output JSON objects have been uploaded
        assertTrue(objectExists(s3AsyncClient, BUCKET_OUTPUT, keyForJSON));
        assertTrue(objectExists(s3AsyncClient, BUCKET_OUTPUT, keyForPDFA));

        // Run the Curation Orchestrator Script - it will upload the input JSON and process the output
        CurationOrchestratorScript curationOrchestratorScript = new CurationOrchestratorScript(s3AsyncClient);
        curationOrchestratorScript.initialize(args, handlerMock, admin);
        curationOrchestratorScript.setS3AsyncClient(s3AsyncClient);
        curationOrchestratorScript.run();

        // Verify that the JSON uploaded to S3 contains only bitstream1 (not bitstream2)
        String jsonKey = findJSONFileInBucket(s3AsyncClient, BUCKET_INPUT, "test-dspace-999");
        assertTrue("JSON file should be uploaded to S3", jsonKey != null && !jsonKey.isEmpty());

        ScheduledProcess scheduledProcess = downloadAndParseJSON(s3AsyncClient, BUCKET_INPUT, jsonKey);
        assertEquals("Should contain only 1 bitstream (bitstream2 and bitstream3 should be excluded)",
                     1, scheduledProcess.files().size());
        assertEquals("Only bitstream1 should be in the JSON",
                     bitstream1.getID(), scheduledProcess.files().get(0).uuid());

        publication = context.reloadEntity(publication);
        // Verify that the PDFA bundle has been created
        pdfaBudles = publication.getBundles("PDFA");
        assertEquals(1, pdfaBudles.size());
        // Verify that the PDFA bundle contains only one bitstream (bitstream1, not bitstream2)
        List<Bitstream> convertedPDF = pdfaBudles.get(0).getBitstreams();
        assertEquals(1, convertedPDF.size());
        // Verify that only bitstream1 has been processed
        assertEquals(bitstream1.getName(), convertedPDF.get(0).getName());
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

    private void uploadObject(S3AsyncClient s3Client, String bucketName, String key, InputStream inputStream) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                                                                .bucket(bucketName)
                                                                .key(key)
                                                                .build();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            AsyncRequestBody body = AsyncRequestBody.fromInputStream(inputStream, null, executor);
            s3Client.putObject(putObjectRequest, body).join();
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload object to S3: " + key + " caused by:" + e.getMessage(), e);
        }
    }

    private boolean objectExists(S3AsyncClient s3Client, String bucketName, String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                                                                   .bucket(bucketName)
                                                                   .key(key)
                                                                   .build();
            s3Client.headObject(headObjectRequest).join();
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Failed to check object existence: " + key, e);
        }
    }

    private String findJSONFileInBucket(S3AsyncClient s3Client, String bucketName, String prefix) {
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                                                                   .bucket(bucketName)
                                                                   .prefix(prefix)
                                                                   .build();
            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest).join();
            return listResponse.contents()
                               .stream()
                               .filter(obj -> obj.key().endsWith(".json"))
                               .map(S3Object::key)
                               .findFirst()
                               .orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find JSON file in S3 bucket: " + bucketName +
                                       " with prefix: " + prefix, e);
        }
    }

    private ScheduledProcess downloadAndParseJSON(S3AsyncClient s3Client, String bucketName, String key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                                                                 .bucket(bucketName)
                                                                 .key(key)
                                                                 .build();
            ResponseBytes<GetObjectResponse> jsonBytes = s3Client.getObject(
                getObjectRequest,
                AsyncResponseTransformer.toBytes()
            ).join();
            String jsonContent = jsonBytes.asUtf8String();
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(jsonContent, ScheduledProcess.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to download and parse JSON from S3: " + key, e);
        }
    }

    private static S3AsyncClient createAmazonS3Client(String endpoint) {
        return S3AsyncClient.crtBuilder()
                            .endpointOverride(URI.create(endpoint))
                            .credentialsProvider(AnonymousCredentialsProvider.create())
                            .region(Region.US_EAST_1)
                            .build();
    }

}
