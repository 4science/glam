/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.curationorchestrator;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.findify.s3mock.S3Mock;
import org.apache.commons.io.IOUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.curate.CurationOrchestratorScript;
import org.dspace.services.ConfigurationService;
import org.dspace.storage.bitstore.service.BitstreamStorageService;
import org.dspace.storage.bitstore.factory.StorageServiceFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.commons.io.FileUtils;
import java.io.IOException;

/**
 * Test class for the Curation Orchestrator Script.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class CurationOrchestratorScriptIT extends AbstractIntegrationTestWithDatabase {

    private static final String DEFAULT_BUCKET_NAME = "dspace-asset-localhost";
    private static final String S3_ENDPOINT = "http://127.0.0.1:8001";
    public static final int MAX_CONNECTIONS = 5;
    public static final int CONNECTION_TIMEOUT = 1000;

    @Autowired
    private ConfigurationService configurationService;

    private S3Mock s3Mock;
    private File s3Directory;
    private AmazonS3 amazonS3Client;
    private BitstreamStorageService bitstreamStorageService;

    private Collection collection;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();

        configurationService.setProperty("assetstore.s3.enabled", "true");
        s3Directory = new File(System.getProperty("java.io.tmpdir"), "s3");
        s3Mock = S3Mock.create(8001, s3Directory.getAbsolutePath());
        s3Mock.start();

        amazonS3Client = createAmazonS3Client(S3_ENDPOINT);
        amazonS3Client.createBucket(DEFAULT_BUCKET_NAME);
        
        // Usa il BitstreamStorageService esistente dal factory
        bitstreamStorageService = StorageServiceFactory.getInstance().getBitstreamStorageService();
        
        // Configura le proprietà per S3
        configurationService.setProperty("assetstore.s3.bucketName", DEFAULT_BUCKET_NAME);
        configurationService.setProperty("assetstore.s3.endpoint", S3_ENDPOINT);
        configurationService.setProperty("curation.s3.send-report.enabled", false);

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
    }

    @Test
    public void launchCurationOrchestratorScriptForItemIT() throws Exception {
        context.turnOffAuthorisationSystem();
        Item publication = ItemBuilder.createItem(context, collection)
                                      .withTitle("Publication Item test")
                                      .withAuthor("Amlinger, Carolin")
                                      .withCurationTask("pdfaTransformer")
                                      .withType("content")
                                      .build();

        try (InputStream is = IOUtils.toInputStream("Content for Bitstream 1", StandardCharsets.UTF_8)) {
            BitstreamBuilder.createBitstream(context, publication, is)
                            .withName("test.pdf")
                            .withMimeType("application/pdf")
                            .build();
        }
        context.restoreAuthSystemState();

        String scriptName = "curateOrchestrator";
        String[] args = new String[] { scriptName, "-t", "pdfaTransformer", "-id", publication.getID().toString() };

        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();
        CurationOrchestratorScript curationOrchestratorScript = new CurationOrchestratorScript();
        curationOrchestratorScript.initialize(args, handler, admin);
        curationOrchestratorScript.setS3Client(amazonS3Client);
        curationOrchestratorScript.setBitstreamStorageService(bitstreamStorageService);
        curationOrchestratorScript.run();
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
