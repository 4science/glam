/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.dspace.storage.AWSCredentialsProviderBuilder.staticCredentials;
import static org.dspace.storage.bitstore.S3BitStoreService.CSA;
import static org.dspace.storage.bitstore.S3BitStoreService.getClientConfiguration;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectMetadata;
import io.findify.s3mock.S3Mock;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.matcher.LambdaMatcher;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.core.Utils;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
/**
 * @author Luca Giamminonni (luca.giamminonni at 4science.com)
 */
public class S3BitStoreServiceIT extends AbstractIntegrationTestWithDatabase {

    private static final String DEFAULT_BUCKET_NAME = "dspace-asset-localhost";
    public static final String S3_ENDPOINT = "http://127.0.0.1:8001";
    public static final int MAX_CONNECTIONS = 5;
    public static final int CONNECTION_TIMEOUT = 1000;

    private S3BitStoreService s3BitStoreService;

    private AmazonS3 amazonS3Client;

    private S3Mock s3Mock;

    private Collection collection;

    private File s3Directory;

    private ConfigurationService configurationService =
        DSpaceServicesFactory.getInstance().getConfigurationService();

    @Before
    public void setup() throws Exception {
        configurationService.setProperty("assetstore.s3.enabled", "true");
        s3Directory = new File(System.getProperty("java.io.tmpdir"), "s3");

        s3Mock = S3Mock.create(8001, s3Directory.getAbsolutePath());
        s3Mock.start();

        amazonS3Client = createAmazonS3Client(S3_ENDPOINT);

        s3BitStoreService = new S3BitStoreService(amazonS3Client);
        s3BitStoreService.setEnabled(
            BooleanUtils.toBoolean(
                configurationService.getProperty("assetstore.s3.enabled")
            )
        );
        s3BitStoreService.setBufferSize(22);
        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();

        collection = CollectionBuilder.createCollection(
            context,
            parentCommunity
        ).build();

        context.restoreAuthSystemState();
    }

    @After
    public void cleanUp() throws IOException {
        FileUtils.deleteDirectory(s3Directory);
        s3Mock.shutdown();
    }

    @Test
    public void testBitstreamServiceNotInitializedWhenDisabled()
        throws IOException {
        this.s3BitStoreService.setEnabled(false);

        this.s3BitStoreService.init();

        assertThat(this.s3BitStoreService.initialized, is(false));
    }

    @Test
    public void testBitstreamPutAndGetWithAlreadyPresentBucket()
        throws IOException {
        String bucketName = "testbucket";

        amazonS3Client.createBucket(bucketName);

        s3BitStoreService.setBucketName(bucketName);
        s3BitStoreService.init();

        assertThat(
            amazonS3Client.listBuckets(),
            contains(bucketNamed(bucketName))
        );

        context.turnOffAuthorisationSystem();
        String content = "Test bitstream content";
        String contentOverOneSpan = "This content span two chunks";
        String contentExactlyTwoSpans =
            "Test bitstream contentTest bitstream content";
        String contentOverOneTwoSpans =
            "Test bitstream contentThis content span three chunks";
        Bitstream bitstream = createBitstream(content);
        Bitstream bitstreamOverOneSpan = createBitstream(contentOverOneSpan);
        Bitstream bitstreamExactlyTwoSpans = createBitstream(
            contentExactlyTwoSpans
        );
        Bitstream bitstreamOverOneTwoSpans = createBitstream(
            contentOverOneTwoSpans
        );
        context.restoreAuthSystemState();

        checkGetPut(bucketName, content, bitstream);
        checkGetPut(bucketName, contentOverOneSpan, bitstreamOverOneSpan);
        checkGetPut(
            bucketName,
            contentExactlyTwoSpans,
            bitstreamExactlyTwoSpans
        );
        checkGetPut(
            bucketName,
            contentOverOneTwoSpans,
            bitstreamOverOneTwoSpans
        );
    }

    private void checkGetPut(
        String bucketName,
        String content,
        Bitstream bitstream
    ) throws IOException {
        s3BitStoreService.put(bitstream, toInputStream(content));
        String expectedChecksum = Utils.toHex(generateChecksum(content));

        assertThat(bitstream.getSizeBytes(), is((long) content.length()));
        assertThat(bitstream.getChecksum(), is(expectedChecksum));
        assertThat(bitstream.getChecksumAlgorithm(), is(CSA));

        InputStream inputStream = s3BitStoreService.get(bitstream);
        assertThat(IOUtils.toString(inputStream, UTF_8), is(content));

        String key = s3BitStoreService.getFullKey(bitstream.getInternalId());
        ObjectMetadata objectMetadata = amazonS3Client.getObjectMetadata(
            bucketName,
            key
        );
        assertThat(objectMetadata.getContentMD5(), is(expectedChecksum));
    }

    @Test
    public void testBitstreamPutAndGetWithoutSpecifingBucket()
        throws IOException {
        s3BitStoreService.init();

        assertThat(s3BitStoreService.getBucketName(), is(DEFAULT_BUCKET_NAME));

        assertThat(
            amazonS3Client.listBuckets(),
            contains(bucketNamed(DEFAULT_BUCKET_NAME))
        );

        context.turnOffAuthorisationSystem();
        String content = "Test bitstream content";
        Bitstream bitstream = createBitstream(content);
        context.restoreAuthSystemState();

        s3BitStoreService.put(bitstream, toInputStream(content));

        String expectedChecksum = Utils.toHex(generateChecksum(content));

        assertThat(bitstream.getSizeBytes(), is((long) content.length()));
        assertThat(bitstream.getChecksum(), is(expectedChecksum));
        assertThat(bitstream.getChecksumAlgorithm(), is(CSA));

        InputStream inputStream = s3BitStoreService.get(bitstream);
        assertThat(IOUtils.toString(inputStream, UTF_8), is(content));

        String key = s3BitStoreService.getFullKey(bitstream.getInternalId());
        ObjectMetadata objectMetadata = amazonS3Client.getObjectMetadata(
            DEFAULT_BUCKET_NAME,
            key
        );
        assertThat(objectMetadata.getContentMD5(), is(expectedChecksum));
    }

    @Test
    public void testBitstreamPutAndGetWithSubFolder() throws IOException {
        s3BitStoreService.setSubfolder("test/DSpace7/");
        s3BitStoreService.init();

        context.turnOffAuthorisationSystem();
        String content = "Test bitstream content";
        Bitstream bitstream = createBitstream(content);
        context.restoreAuthSystemState();

        s3BitStoreService.put(bitstream, toInputStream(content));

        InputStream inputStream = s3BitStoreService.get(bitstream);
        assertThat(IOUtils.toString(inputStream, UTF_8), is(content));

        String key = s3BitStoreService.getFullKey(bitstream.getInternalId());
        assertThat(key, Matchers.startsWith("test/DSpace7/"));

        ObjectMetadata objectMetadata = amazonS3Client.getObjectMetadata(
            DEFAULT_BUCKET_NAME,
            key
        );
        assertThat(objectMetadata, Matchers.notNullValue());
    }

    @Test
    public void testBitstreamDeletion() throws IOException {
        s3BitStoreService.init();

        context.turnOffAuthorisationSystem();
        String content = "Test bitstream content";
        Bitstream bitstream = createBitstream(content);
        context.restoreAuthSystemState();

        s3BitStoreService.put(bitstream, toInputStream(content));

        assertThat(s3BitStoreService.get(bitstream), Matchers.notNullValue());

        s3BitStoreService.remove(bitstream);

        IOException exception = assertThrows(IOException.class, () ->
            s3BitStoreService.get(bitstream)
        );
        assertThat(exception.getCause(), instanceOf(AmazonS3Exception.class));
        assertThat(
            ((AmazonS3Exception) exception.getCause()).getStatusCode(),
            is(404)
        );
    }

    @Test
    public void testAbout() throws IOException {
        s3BitStoreService.init();

        context.turnOffAuthorisationSystem();
        String content = "Test bitstream content";
        Bitstream bitstream = createBitstream(content);
        context.restoreAuthSystemState();

        s3BitStoreService.put(bitstream, toInputStream(content));

        Map<String, Object> about = s3BitStoreService.about(
            bitstream,
            List.of()
        );
        assertThat(about.size(), is(0));

        about = s3BitStoreService.about(bitstream, List.of("size_bytes"));
        assertThat(about, hasEntry("size_bytes", 22L));
        assertThat(about.size(), is(1));

        about = s3BitStoreService.about(
            bitstream,
            List.of("size_bytes", "modified")
        );
        assertThat(about, hasEntry("size_bytes", 22L));
        assertThat(about, hasEntry(is("modified"), Matchers.notNullValue()));
        assertThat(about.size(), is(2));

        String expectedChecksum = Utils.toHex(generateChecksum(content));

        about = s3BitStoreService.about(
            bitstream,
            List.of("size_bytes", "modified", "checksum")
        );
        assertThat(about, hasEntry("size_bytes", 22L));
        assertThat(about, hasEntry(is("modified"), Matchers.notNullValue()));
        assertThat(about, hasEntry("checksum", expectedChecksum));
        assertThat(about.size(), is(3));

        about = s3BitStoreService.about(
            bitstream,
            List.of("size_bytes", "modified", "checksum", "checksum_algorithm")
        );
        assertThat(about, hasEntry("size_bytes", 22L));
        assertThat(about, hasEntry(is("modified"), Matchers.notNullValue()));
        assertThat(about, hasEntry("checksum", expectedChecksum));
        assertThat(about, hasEntry("checksum_algorithm", CSA));
        assertThat(about.size(), is(4));
    }

    @Test
    public void handleRegisteredIdentifierPrefixInS3() {
        String trueBitStreamId = "012345";
        String registeredBitstreamId =
            s3BitStoreService.REGISTERED_FLAG + trueBitStreamId;
        // Should be detected as registered bitstream
        assertTrue(
            this.s3BitStoreService.isRegisteredBitstream(registeredBitstreamId)
        );
    }

    @Test
    public void stripRegisteredBitstreamPrefixWhenCalculatingPath() {
        // Set paths and IDs
        String s3Path = "UNIQUE_S3_PATH/test/bitstream.pdf";
        String registeredBitstreamId =
            s3BitStoreService.REGISTERED_FLAG + s3Path;
        // Paths should be equal, since the getRelativePath method should strip the registered -R prefix
        String relativeRegisteredPath = this.s3BitStoreService.getRelativePath(
            registeredBitstreamId
        );
        assertEquals(s3Path, relativeRegisteredPath);
    }

    @Test
    public void givenBitStreamIdentifierLongerThanPossibleWhenIntermediatePathIsComputedThenIsSplittedAndTruncated() {
        String path = "01234567890123456789";
        String computedPath = this.s3BitStoreService.getIntermediatePath(path);
        String expectedPath =
            "01" +
            File.separator +
            "23" +
            File.separator +
            "45" +
            File.separator;
        assertThat(computedPath, equalTo(expectedPath));
    }

    @Test
    public void givenBitStreamIdentifierShorterThanAFolderLengthWhenIntermediatePathIsComputedThenIsSingleFolder() {
        String path = "0";
        String computedPath = this.s3BitStoreService.getIntermediatePath(path);
        String expectedPath = "0" + File.separator;
        assertThat(computedPath, equalTo(expectedPath));
    }

    @Test
    public void givenPartialBitStreamIdentifierWhenIntermediatePathIsComputedThenIsCompletlySplitted() {
        String path = "01234";
        String computedPath = this.s3BitStoreService.getIntermediatePath(path);
        String expectedPath =
            "01" +
            File.separator +
            "23" +
            File.separator +
            "4" +
            File.separator;
        assertThat(computedPath, equalTo(expectedPath));
    }

    @Test
    public void givenMaxLengthBitStreamIdentifierWhenIntermediatePathIsComputedThenIsSplittedAllAsSubfolder() {
        String path = "012345";
        String computedPath = this.s3BitStoreService.getIntermediatePath(path);
        String expectedPath =
            "01" +
            File.separator +
            "23" +
            File.separator +
            "45" +
            File.separator;
        assertThat(computedPath, equalTo(expectedPath));
    }

    @Test
    public void givenBitStreamIdentifierWhenIntermediatePathIsComputedThenNotEndingDoubleSlash()
        throws IOException {
        StringBuilder path = new StringBuilder("01");
        String computedPath = this.s3BitStoreService.getIntermediatePath(
            path.toString()
        );
        int slashes = computeSlashes(path.toString());
        assertThat(computedPath, Matchers.endsWith(File.separator));
        assertThat(
            computedPath.split(File.separator).length,
            Matchers.equalTo(slashes)
        );

        path.append("2");
        computedPath = this.s3BitStoreService.getIntermediatePath(
            path.toString()
        );
        assertThat(
            computedPath,
            Matchers.not(Matchers.endsWith(File.separator + File.separator))
        );

        path.append("3");
        computedPath = this.s3BitStoreService.getIntermediatePath(
            path.toString()
        );
        assertThat(
            computedPath,
            Matchers.not(Matchers.endsWith(File.separator + File.separator))
        );

        path.append("4");
        computedPath = this.s3BitStoreService.getIntermediatePath(
            path.toString()
        );
        assertThat(
            computedPath,
            Matchers.not(Matchers.endsWith(File.separator + File.separator))
        );

        path.append("56789");
        computedPath = this.s3BitStoreService.getIntermediatePath(
            path.toString()
        );
        assertThat(
            computedPath,
            Matchers.not(Matchers.endsWith(File.separator + File.separator))
        );
    }

    @Test
    public void givenBitStreamIdentidierWhenIntermediatePathIsComputedThenMustBeSplitted()
        throws IOException {
        StringBuilder path = new StringBuilder("01");
        String computedPath = this.s3BitStoreService.getIntermediatePath(
            path.toString()
        );
        int slashes = computeSlashes(path.toString());
        assertThat(computedPath, Matchers.endsWith(File.separator));
        assertThat(
            computedPath.split(File.separator).length,
            Matchers.equalTo(slashes)
        );

        path.append("2");
        computedPath = this.s3BitStoreService.getIntermediatePath(
            path.toString()
        );
        slashes = computeSlashes(path.toString());
        assertThat(computedPath, Matchers.endsWith(File.separator));
        assertThat(
            computedPath.split(File.separator).length,
            Matchers.equalTo(slashes)
        );

        path.append("3");
        computedPath = this.s3BitStoreService.getIntermediatePath(
            path.toString()
        );
        slashes = computeSlashes(path.toString());
        assertThat(computedPath, Matchers.endsWith(File.separator));
        assertThat(
            computedPath.split(File.separator).length,
            Matchers.equalTo(slashes)
        );

        path.append("4");
        computedPath = this.s3BitStoreService.getIntermediatePath(
            path.toString()
        );
        slashes = computeSlashes(path.toString());
        assertThat(computedPath, Matchers.endsWith(File.separator));
        assertThat(
            computedPath.split(File.separator).length,
            Matchers.equalTo(slashes)
        );

        path.append("56789");
        computedPath = this.s3BitStoreService.getIntermediatePath(
            path.toString()
        );
        slashes = computeSlashes(path.toString());
        assertThat(computedPath, Matchers.endsWith(File.separator));
        assertThat(
            computedPath.split(File.separator).length,
            Matchers.equalTo(slashes)
        );
    }

    @Test
    public void givenBitStreamIdentifierWithSlashesWhenSanitizedThenSlashesMustBeRemoved() {
        String sInternalId = new StringBuilder("01")
            .append(File.separator)
            .append("22")
            .append(File.separator)
            .append("33")
            .append(File.separator)
            .append("4455")
            .toString();
        String computedPath = this.s3BitStoreService.sanitizeIdentifier(
            sInternalId
        );
        assertThat(
            computedPath,
            Matchers.not(Matchers.startsWith(File.separator))
        );
        assertThat(
            computedPath,
            Matchers.not(Matchers.endsWith(File.separator))
        );
        assertThat(
            computedPath,
            Matchers.not(Matchers.containsString(File.separator))
        );
    }

    @Test
    public void testDoNotInitializeConfigured() throws Exception {
        String assetstores3enabledOldValue = configurationService.getProperty(
            "assetstore.s3.enabled"
        );
        configurationService.setProperty("assetstore.s3.enabled", "false");
        s3BitStoreService = new S3BitStoreService(amazonS3Client);
        s3BitStoreService.init();
        assertFalse(s3BitStoreService.isInitialized());
        assertFalse(s3BitStoreService.isEnabled());
        configurationService.setProperty(
            "assetstore.s3.enabled",
            assetstores3enabledOldValue
        );
    }

    private byte[] generateChecksum(String content) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(content.getBytes());
            return m.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private AmazonS3 createAmazonS3Client(String endpoint) {
        return S3BitStoreService.amazonClientBuilderBy(
            () -> Regions.DEFAULT_REGION,
            () -> staticCredentials(new AnonymousAWSCredentials()),
            getClientConfiguration(MAX_CONNECTIONS, CONNECTION_TIMEOUT),
            endpoint
        ).get();
    }

    private Item createItem() {
        return ItemBuilder.createItem(context, collection)
            .withTitle("Test item")
            .build();
    }

    private Bitstream createBitstream(String content) {
        try {
            return BitstreamBuilder.createBitstream(
                context,
                createItem(),
                toInputStream(content)
            ).build();
        } catch (SQLException | AuthorizeException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Matcher<? super Bucket> bucketNamed(String name) {
        return LambdaMatcher.matches(bucket -> bucket.getName().equals(name));
    }

    private InputStream toInputStream(String content) {
        return IOUtils.toInputStream(content, UTF_8);
    }

    private int computeSlashes(String internalId) {
        int minimum = internalId.length();
        int slashesPerLevel = minimum / S3BitStoreService.digitsPerLevel;
        int odd = Math.min(1, minimum % S3BitStoreService.digitsPerLevel);
        int slashes = slashesPerLevel + odd;
        return Math.min(slashes, S3BitStoreService.directoryLevels);
    }

    @Test
    public void testIRSAAuthenticationTypeConfiguration() throws IOException {
        // Test IRSA authentication type
        s3BitStoreService.setAwsAuthenticationType("irsa");
        s3BitStoreService.init();

        assertThat(s3BitStoreService.getAwsAuthenticationType(), is("irsa"));
        assertTrue(s3BitStoreService.isInitialized());
    }

    @Test
    public void testStaticAuthenticationTypeConfiguration() throws IOException {
        // Test static authentication type with credentials
        s3BitStoreService.setAwsAuthenticationType("static");
        s3BitStoreService.setAwsAccessKey("testAccessKey");
        s3BitStoreService.setAwsSecretKey("testSecretKey");
        s3BitStoreService.init();

        assertThat(s3BitStoreService.getAwsAuthenticationType(), is("static"));
        assertTrue(s3BitStoreService.isInitialized());
    }

    @Test
    public void testDefaultAuthenticationTypeConfiguration()
        throws IOException {
        // Test default authentication type (should use DefaultAWSCredentialsProviderChain)
        s3BitStoreService.setAwsAuthenticationType("default");
        s3BitStoreService.init();

        assertThat(s3BitStoreService.getAwsAuthenticationType(), is("default"));
        assertTrue(s3BitStoreService.isInitialized());
    }

    @Test
    public void testNullAuthenticationTypeDefaultsToChain() throws IOException {
        // Test null authentication type (should use DefaultAWSCredentialsProviderChain)
        s3BitStoreService.setAwsAuthenticationType(null);
        s3BitStoreService.init();

        assertTrue(s3BitStoreService.isInitialized());
    }

    @Test
    public void testEmptyAuthenticationTypeDefaultsToChain()
        throws IOException {
        // Test empty authentication type (should use DefaultAWSCredentialsProviderChain)
        s3BitStoreService.setAwsAuthenticationType("");
        s3BitStoreService.init();

        assertTrue(s3BitStoreService.isInitialized());
    }

    @Test
    public void testSTSAuthenticationTypeConfiguration() throws IOException {
        // Test STS authentication type with role ARN
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );
        s3BitStoreService.setStsSessionName("DSpace-Test-Session");
        s3BitStoreService.setStsSessionDuration(3600); // 1 hour
        s3BitStoreService.init();

        assertThat(s3BitStoreService.getAwsAuthenticationType(), is("sts"));
        assertThat(
            s3BitStoreService.getStsRoleArn(),
            is("arn:aws:iam::123456789012:role/TestRole")
        );
        assertThat(
            s3BitStoreService.getStsSessionName(),
            is("DSpace-Test-Session")
        );
        assertThat(s3BitStoreService.getStsSessionDuration(), is(3600));
        assertTrue(s3BitStoreService.isInitialized());
    }

    @Test
    public void testSTSAuthenticationWithExternalId() throws IOException {
        // Test STS authentication with external ID for cross-account access
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/CrossAccountRole"
        );
        s3BitStoreService.setStsExternalId("unique-external-id-123");
        s3BitStoreService.setStsSessionDuration(7200); // 2 hours
        s3BitStoreService.init();

        assertThat(
            s3BitStoreService.getStsExternalId(),
            is("unique-external-id-123")
        );
        assertThat(s3BitStoreService.getStsSessionDuration(), is(7200));
        assertTrue(s3BitStoreService.isInitialized());
    }

    @Test
    public void testSTSAuthenticationWithCustomEndpoint() throws IOException {
        // Test STS authentication with custom endpoint and region
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );
        s3BitStoreService.setStsEndpoint("https://sts.us-west-2.amazonaws.com");
        s3BitStoreService.setStsRegion("us-west-2");
        s3BitStoreService.init();

        assertThat(
            s3BitStoreService.getStsEndpoint(),
            is("https://sts.us-west-2.amazonaws.com")
        );
        assertThat(s3BitStoreService.getStsRegion(), is("us-west-2"));
        assertTrue(s3BitStoreService.isInitialized());
    }

    @Test
    public void testSTSAuthenticationValidateSessionDuration()
        throws IOException {
        // Test STS authentication with various session durations
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );

        // Test minimum valid duration
        s3BitStoreService.setStsSessionDuration(900); // 15 minutes
        s3BitStoreService.init();
        assertThat(s3BitStoreService.getStsSessionDuration(), is(900));
        assertTrue(s3BitStoreService.isInitialized());

        // Reset for next test
        s3BitStoreService = new S3BitStoreService(amazonS3Client);
        s3BitStoreService.setEnabled(true);
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );

        // Test maximum valid duration
        s3BitStoreService.setStsSessionDuration(43200); // 12 hours
        s3BitStoreService.init();
        assertThat(s3BitStoreService.getStsSessionDuration(), is(43200));
        assertTrue(s3BitStoreService.isInitialized());
    }

    @Test
    public void testSTSAuthenticationInvalidSessionDuration()
        throws IOException {
        // Test STS authentication with invalid session duration (should log warning but still work)
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );
        s3BitStoreService.setStsSessionDuration(300); // Too short (less than 900)
        s3BitStoreService.init();

        // Should still initialize successfully, but duration will be ignored
        assertTrue(s3BitStoreService.isInitialized());
        assertThat(s3BitStoreService.getStsSessionDuration(), is(300)); // Value is stored but ignored in provider
    }

    @Test
    public void testSTSAuthenticationDefaultSessionName() throws IOException {
        // Test STS authentication with default session name
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );
        // Don't set session name - should use default
        s3BitStoreService.init();

        // Session name should be null (will use default "DSpace-S3-Session" in provider)
        assertThat(s3BitStoreService.getStsSessionName(), Matchers.nullValue());
        assertTrue(s3BitStoreService.isInitialized());
    }

    @Test
    public void testSTSAuthenticationGetterSetters() {
        // Test all STS-related getters and setters
        String roleArn = "arn:aws:iam::123456789012:role/TestRole";
        String sessionName = "TestSession";
        Integer sessionDuration = 3600;
        String externalId = "external-123";
        String endpoint = "https://sts.us-east-1.amazonaws.com";
        String region = "us-east-1";

        s3BitStoreService.setStsRoleArn(roleArn);
        s3BitStoreService.setStsSessionName(sessionName);
        s3BitStoreService.setStsSessionDuration(sessionDuration);
        s3BitStoreService.setStsExternalId(externalId);
        s3BitStoreService.setStsEndpoint(endpoint);
        s3BitStoreService.setStsRegion(region);

        assertThat(s3BitStoreService.getStsRoleArn(), is(roleArn));
        assertThat(s3BitStoreService.getStsSessionName(), is(sessionName));
        assertThat(
            s3BitStoreService.getStsSessionDuration(),
            is(sessionDuration)
        );
        assertThat(s3BitStoreService.getStsExternalId(), is(externalId));
        assertThat(s3BitStoreService.getStsEndpoint(), is(endpoint));
        assertThat(s3BitStoreService.getStsRegion(), is(region));
    }

    @Test
    public void testSTSAuthenticationWithBucketOperations() throws IOException {
        // Test STS authentication with actual S3 operations
        String bucketName = "sts-test-bucket";

        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );
        s3BitStoreService.setStsSessionName("DSpace-STS-Test");
        s3BitStoreService.setBucketName(bucketName);

        amazonS3Client.createBucket(bucketName);
        s3BitStoreService.init();

        assertThat(
            amazonS3Client.listBuckets(),
            contains(bucketNamed(bucketName))
        );
        assertThat(s3BitStoreService.getBucketName(), is(bucketName));
        assertTrue(s3BitStoreService.isInitialized());

        // Test bitstream operations with STS credentials
        context.turnOffAuthorisationSystem();
        String content = "STS test content";
        Bitstream bitstream = createBitstream(content);
        context.restoreAuthSystemState();

        // This should work with STS credentials
        s3BitStoreService.put(bitstream, toInputStream(content));
        String expectedChecksum = Utils.toHex(generateChecksum(content));

        InputStream inputStream = s3BitStoreService.get(bitstream);
        assertThat(IOUtils.toString(inputStream, UTF_8), is(content));

        String key = s3BitStoreService.getFullKey(bitstream.getInternalId());
        ObjectMetadata objectMetadata = amazonS3Client.getObjectMetadata(
            bucketName,
            key
        );
        assertThat(objectMetadata.getContentMD5(), is(expectedChecksum));
    }

    @Test
    public void testSTSAuthenticationRoleArnValidation() {
        // Test various role ARN formats
        s3BitStoreService.setAwsAuthenticationType("sts");

        // Valid role ARN format
        String validRoleArn = "arn:aws:iam::123456789012:role/ValidRole";
        s3BitStoreService.setStsRoleArn(validRoleArn);
        assertThat(s3BitStoreService.getStsRoleArn(), is(validRoleArn));

        // Test with role ARN containing path
        String roleArnWithPath =
            "arn:aws:iam::123456789012:role/path/to/role/ValidRole";
        s3BitStoreService.setStsRoleArn(roleArnWithPath);
        assertThat(s3BitStoreService.getStsRoleArn(), is(roleArnWithPath));
    }

    @Test
    public void testSTSAuthenticationSessionNameValidation() {
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );

        // Test valid session names
        String[] validSessionNames = {
            "DSpace-Session",
            "DSpace_Session_123",
            "DSpace.Session@domain.com",
            "Session+With=Special,Characters-123",
        };

        for (String sessionName : validSessionNames) {
            s3BitStoreService.setStsSessionName(sessionName);
            assertThat(s3BitStoreService.getStsSessionName(), is(sessionName));
        }
    }

    @Test
    public void testSTSAuthenticationSessionDurationBoundaryValues()
        throws IOException {
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );

        // Test minimum boundary (900 seconds)
        s3BitStoreService.setStsSessionDuration(900);
        s3BitStoreService.init();
        assertThat(s3BitStoreService.getStsSessionDuration(), is(900));
        assertTrue(s3BitStoreService.isInitialized());

        // Reset service
        s3BitStoreService = new S3BitStoreService(amazonS3Client);
        s3BitStoreService.setEnabled(true);
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );

        // Test maximum boundary (43200 seconds)
        s3BitStoreService.setStsSessionDuration(43200);
        s3BitStoreService.init();
        assertThat(s3BitStoreService.getStsSessionDuration(), is(43200));
        assertTrue(s3BitStoreService.isInitialized());
    }

    @Test
    public void testSTSAuthenticationExternalIdHandling() throws IOException {
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );

        // Test with various external ID formats
        String[] externalIds = {
            "simple-external-id",
            "complex.external@id_with+special=characters",
            "12345-abcde-67890-fghij",
            "external-id-with-very-long-name-that-should-still-work",
        };

        for (String externalId : externalIds) {
            s3BitStoreService.setStsExternalId(externalId);
            s3BitStoreService.init();

            assertThat(s3BitStoreService.getStsExternalId(), is(externalId));
            assertTrue(s3BitStoreService.isInitialized());

            // Reset for next iteration
            s3BitStoreService = new S3BitStoreService(amazonS3Client);
            s3BitStoreService.setEnabled(true);
            s3BitStoreService.setAwsAuthenticationType("sts");
            s3BitStoreService.setStsRoleArn(
                "arn:aws:iam::123456789012:role/TestRole"
            );
        }
    }

    @Test
    public void testSTSAuthenticationEndpointConfiguration()
        throws IOException {
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );

        // Test various STS endpoint formats
        String[] endpoints = {
            "https://sts.amazonaws.com",
            "https://sts.us-east-1.amazonaws.com",
            "https://sts.eu-west-1.amazonaws.com",
            "https://sts.ap-southeast-1.amazonaws.com",
        };

        for (String endpoint : endpoints) {
            s3BitStoreService.setStsEndpoint(endpoint);
            s3BitStoreService.init();

            assertThat(s3BitStoreService.getStsEndpoint(), is(endpoint));
            assertTrue(s3BitStoreService.isInitialized());

            // Reset for next iteration
            s3BitStoreService = new S3BitStoreService(amazonS3Client);
            s3BitStoreService.setEnabled(true);
            s3BitStoreService.setAwsAuthenticationType("sts");
            s3BitStoreService.setStsRoleArn(
                "arn:aws:iam::123456789012:role/TestRole"
            );
        }
    }

    @Test
    public void testSTSAuthenticationRegionConfiguration() throws IOException {
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );

        // Test various AWS regions
        String[] regions = {
            "us-east-1",
            "us-west-2",
            "eu-west-1",
            "eu-central-1",
            "ap-southeast-1",
            "ap-northeast-1",
        };

        for (String region : regions) {
            s3BitStoreService.setStsRegion(region);
            s3BitStoreService.init();

            assertThat(s3BitStoreService.getStsRegion(), is(region));
            assertTrue(s3BitStoreService.isInitialized());

            // Reset for next iteration
            s3BitStoreService = new S3BitStoreService(amazonS3Client);
            s3BitStoreService.setEnabled(true);
            s3BitStoreService.setAwsAuthenticationType("sts");
            s3BitStoreService.setStsRoleArn(
                "arn:aws:iam::123456789012:role/TestRole"
            );
        }
    }

    @Test
    public void testSTSAuthenticationCompleteConfiguration()
        throws IOException {
        // Test with all STS parameters configured
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/DSpaceTestRole"
        );
        s3BitStoreService.setStsSessionName("DSpace-Complete-Test-Session");
        s3BitStoreService.setStsSessionDuration(7200);
        s3BitStoreService.setStsExternalId("dspace-test-external-id");
        s3BitStoreService.setStsEndpoint("https://sts.us-west-2.amazonaws.com");
        s3BitStoreService.setStsRegion("us-west-2");
        s3BitStoreService.setBucketName("dspace-sts-test-bucket");
        s3BitStoreService.setAwsRegionName("us-west-2");

        s3BitStoreService.init();

        // Verify all configurations are set correctly
        assertThat(s3BitStoreService.getAwsAuthenticationType(), is("sts"));
        assertThat(
            s3BitStoreService.getStsRoleArn(),
            is("arn:aws:iam::123456789012:role/DSpaceTestRole")
        );
        assertThat(
            s3BitStoreService.getStsSessionName(),
            is("DSpace-Complete-Test-Session")
        );
        assertThat(s3BitStoreService.getStsSessionDuration(), is(7200));
        assertThat(
            s3BitStoreService.getStsExternalId(),
            is("dspace-test-external-id")
        );
        assertThat(
            s3BitStoreService.getStsEndpoint(),
            is("https://sts.us-west-2.amazonaws.com")
        );
        assertThat(s3BitStoreService.getStsRegion(), is("us-west-2"));
        assertThat(
            s3BitStoreService.getBucketName(),
            is("dspace-sts-test-bucket")
        );
        assertThat(s3BitStoreService.getAwsRegionName(), is("us-west-2"));
        assertTrue(s3BitStoreService.isInitialized());
    }

    @Test
    public void testSTSAuthenticationFallbackToS3Region() throws IOException {
        // Reset service to ensure clean state
        s3BitStoreService = new S3BitStoreService(null);
        s3BitStoreService.setEnabled(true);
        // Test STS authentication falls back to S3 region when STS region is not specified
        s3BitStoreService.setAwsAuthenticationType("sts");
        s3BitStoreService.setStsRoleArn(
            "arn:aws:iam::123456789012:role/TestRole"
        );
        s3BitStoreService.setAwsRegionName("eu-west-1");
        // Don't set STS region - should use S3 region

        s3BitStoreService.init();

        assertThat(s3BitStoreService.getAwsRegionName(), is("eu-west-1"));
        assertThat(s3BitStoreService.getStsRegion(), Matchers.nullValue());
        assertTrue(s3BitStoreService.isInitialized());
    }
}
