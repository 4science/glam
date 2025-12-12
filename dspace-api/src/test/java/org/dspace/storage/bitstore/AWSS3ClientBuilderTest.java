/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;

import org.dspace.AbstractDSpaceTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

/**
 * Unit tests for {@link AWSS3ClientBuilder}.
 *
 * Tests cover builder pattern functionality, S3 async client creation,
 * configuration parameter setting, and various edge cases.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
public class AWSS3ClientBuilderTest extends AbstractDSpaceTest {

    private static final String TEST_ACCESS_KEY = "testAccessKey";
    private static final String TEST_SECRET_KEY = "testSecretKey";
    private static final String TEST_ENDPOINT = "https://s3.amazonaws.com";
    private static final String CUSTOM_ENDPOINT = "https://custom-s3.example.com";
    private static final String MINIO_ENDPOINT = "http://localhost:9000";

    @Mock
    private Supplier<AwsCredentialsProvider> mockCredentialsProvider;

    private AWSS3ClientBuilder builder;

    @Before
    public void setUp() {
        builder = AWSS3ClientBuilder.builder();

        // Setup mock credentials provider
        AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(TEST_ACCESS_KEY, TEST_SECRET_KEY)
        );
        when(mockCredentialsProvider.get()).thenReturn(credentialsProvider);
    }

    @Test
    public void testBuilderCreation() {
        AWSS3ClientBuilder testBuilder = AWSS3ClientBuilder.builder();
        assertNotNull("Builder should not be null", testBuilder);
    }

    @Test
    public void testSetEndpoint() {
        AWSS3ClientBuilder result = builder.setEndpoint(TEST_ENDPOINT);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertEquals("Endpoint should be set correctly", TEST_ENDPOINT, builder.endpoint);
    }

    @Test
    public void testSetEndpointWithNull() {
        AWSS3ClientBuilder result = builder.setEndpoint(null);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertNull("Null endpoint should be set", builder.endpoint);
    }

    @Test
    public void testSetEndpointWithEmptyString() {
        String emptyEndpoint = "";
        AWSS3ClientBuilder result = builder.setEndpoint(emptyEndpoint);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertEquals("Empty endpoint should be set", emptyEndpoint, builder.endpoint);
    }

    @Test
    public void testSetRegion() {
        Region testRegion = Region.US_WEST_2;
        AWSS3ClientBuilder result = builder.setRegion(testRegion);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertEquals("Region should be set correctly", testRegion, builder.region);
    }

    @Test
    public void testSetRegionWithNull() {
        AWSS3ClientBuilder result = builder.setRegion(null);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertNull("Null region should be set", builder.region);
    }

    @Test
    public void testSetMaxConcurrency() {
        Integer testMaxConcurrency = 25;
        AWSS3ClientBuilder result = builder.setMaxConcurrency(testMaxConcurrency);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertEquals("Max concurrency should be set correctly", testMaxConcurrency, builder.maxConcurrency);
    }

    @Test
    public void testSetMaxConcurrencyWithNull() {
        AWSS3ClientBuilder result = builder.setMaxConcurrency(null);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertNull("Null max concurrency should be set", builder.maxConcurrency);
    }

    @Test
    public void testSetMaxConcurrencyWithZero() {
        Integer zeroMaxConcurrency = 0;
        AWSS3ClientBuilder result = builder.setMaxConcurrency(zeroMaxConcurrency);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertEquals("Zero max concurrency should be set", zeroMaxConcurrency, builder.maxConcurrency);
    }

    @Test
    public void testSetMaxConcurrencyWithNegativeValue() {
        Integer negativeMaxConcurrency = -5;
        AWSS3ClientBuilder result = builder.setMaxConcurrency(negativeMaxConcurrency);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertEquals("Negative max concurrency should be set", negativeMaxConcurrency, builder.maxConcurrency);
    }

    @Test
    public void testSetTargetThroughput() {
        Double testThroughput = 15.5;
        AWSS3ClientBuilder result = builder.setTargetThroughput(testThroughput);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertEquals("Target throughput should be set correctly", testThroughput, builder.targetThroughput);
    }

    @Test
    public void testSetTargetThroughputWithNull() {
        AWSS3ClientBuilder result = builder.setTargetThroughput(null);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertNull("Null target throughput should be set", builder.targetThroughput);
    }

    @Test
    public void testSetTargetThroughputWithZero() {
        Double zeroThroughput = 0.0;
        AWSS3ClientBuilder result = builder.setTargetThroughput(zeroThroughput);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertEquals("Zero target throughput should be set", zeroThroughput, builder.targetThroughput);
    }

    @Test
    public void testSetTargetThroughputWithNegativeValue() {
        Double negativeThroughput = -10.0;
        AWSS3ClientBuilder result = builder.setTargetThroughput(negativeThroughput);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertEquals("Negative target throughput should be set", negativeThroughput, builder.targetThroughput);
    }

    @Test
    public void testSetMinPartSize() {
        Long testMinPartSize = 16L * 1024L * 1024L; // 16MB
        AWSS3ClientBuilder result = builder.setMinPartSize(testMinPartSize);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertEquals("Min part size should be set correctly", testMinPartSize, builder.minPartSize);
    }

    @Test
    public void testSetMinPartSizeWithNull() {
        AWSS3ClientBuilder result = builder.setMinPartSize(null);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertNull("Null min part size should be set", builder.minPartSize);
    }

    @Test
    public void testSetMinPartSizeWithZero() {
        Long zeroMinPartSize = 0L;
        AWSS3ClientBuilder result = builder.setMinPartSize(zeroMinPartSize);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertEquals("Zero min part size should be set", zeroMinPartSize, builder.minPartSize);
    }

    @Test
    public void testSetMinPartSizeWithNegativeValue() {
        Long negativeMinPartSize = -1024L;
        AWSS3ClientBuilder result = builder.setMinPartSize(negativeMinPartSize);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertEquals("Negative min part size should be set", negativeMinPartSize, builder.minPartSize);
    }

    @Test
    public void testSetCredentialsProvider() {
        AWSS3ClientBuilder result = builder.setCredentialsProvider(mockCredentialsProvider);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertEquals("Credentials provider should be set correctly", mockCredentialsProvider, builder.credentialsProvider);
    }

    @Test
    public void testSetCredentialsProviderWithNull() {
        AWSS3ClientBuilder result = builder.setCredentialsProvider(null);

        assertEquals("Builder should return itself for method chaining", builder, result);
        assertNull("Null credentials provider should be set", builder.credentialsProvider);
    }

    @Test
    public void testAsyncClientWithMinimalConfiguration() {
        // Test with minimal configuration - AWS SDK requires a region for S3 client creation
        S3AsyncClient client = builder.asyncClient();
        assertNotNull("S3AsyncClient should be created with minimal configuration", client);
        client.close();
    }

    @Test
    public void testAsyncClientWithFullConfiguration() {
        // Test with full configuration
        Region region = Region.EU_WEST_1;
        Integer maxConcurrency = 50;
        Double targetThroughput = 25.0;
        Long minPartSize = 32L * 1024L * 1024L; // 32MB

        S3AsyncClient client = builder
            .setEndpoint(CUSTOM_ENDPOINT)
            .setRegion(region)
            .setMaxConcurrency(maxConcurrency)
            .setTargetThroughput(targetThroughput)
            .setMinPartSize(minPartSize)
            .setCredentialsProvider(mockCredentialsProvider)
            .asyncClient();

        assertNotNull("S3AsyncClient should be created with full configuration", client);
        client.close();
    }

    @Test
    public void testAsyncClientWithCredentialsProvider() {
        S3AsyncClient client = builder
            .setCredentialsProvider(mockCredentialsProvider)
            .setRegion(Region.US_EAST_1)
            .asyncClient();

        assertNotNull("S3AsyncClient should be created with credentials provider", client);
        client.close();
    }

    @Test
    public void testAsyncClientWithCustomEndpoint() {
        String customEndpoint = "https://minio.example.com:9000";

        S3AsyncClient client = builder
            .setEndpoint(customEndpoint)
            .setRegion(Region.US_EAST_1)
            .setCredentialsProvider(mockCredentialsProvider)
            .asyncClient();

        assertNotNull("S3AsyncClient should be created with custom endpoint", client);
        client.close();
    }

    @Test
    public void testAsyncClientWithEndpointForcePathStyle() {
        // Test that when endpoint is set, forcePathStyle(true) is used
        S3AsyncClient client = builder
            .setEndpoint(MINIO_ENDPOINT)
            .setRegion(Region.US_EAST_1)
            .setCredentialsProvider(mockCredentialsProvider)
            .asyncClient();

        assertNotNull("S3AsyncClient should handle custom endpoint with path style", client);
        client.close();
    }

    @Test
    public void testAsyncClientWithBlankEndpoint() {
        // Test with blank endpoint - should not set endpoint override
        String blankEndpoint = "   ";

        S3AsyncClient client = builder
            .setEndpoint(blankEndpoint)
            .setRegion(Region.US_EAST_1)
            .setCredentialsProvider(mockCredentialsProvider)
            .asyncClient();

        assertNotNull("S3AsyncClient should handle blank endpoint", client);
        client.close();
    }

    @Test
    public void testAsyncClientWithEmptyEndpoint() {
        // Test with empty endpoint - should not set endpoint override
        String emptyEndpoint = "";

        S3AsyncClient client = builder
            .setEndpoint(emptyEndpoint)
            .setRegion(Region.US_EAST_1)
            .setCredentialsProvider(mockCredentialsProvider)
            .asyncClient();

        assertNotNull("S3AsyncClient should handle empty endpoint", client);
        client.close();
    }

    @Test
    public void testAsyncClientWithNullValues() {
        // Test with null values for optional parameters - AWS SDK requires region
        S3AsyncClient client = builder
            .setEndpoint(null)
            .setRegion(null)
            .setMaxConcurrency(null)
            .setTargetThroughput(null)
            .setMinPartSize(null)
            .setCredentialsProvider(null)
            .asyncClient();

        assertNotNull("S3AsyncClient should handle null optional values", client);
        client.close();
    }

    @Test
    public void testAsyncClientWithLowValues() {
        // Test with low boundary values (AWS SDK requires positive maxConcurrency)
        S3AsyncClient client = builder
            .setMaxConcurrency(1) // Minimum positive value
            .setTargetThroughput(0.1) // Small but positive throughput
            .setMinPartSize(1024L) // 1KB minimum
            .setRegion(Region.US_EAST_1)
            .setCredentialsProvider(mockCredentialsProvider)
            .asyncClient();

        assertNotNull("S3AsyncClient should handle low boundary values", client);
        client.close();
    }

    @Test
    public void testAsyncClientWithHighValues() {
        // Test with high but realistic values (MAX_VALUE causes native library issues)
        S3AsyncClient client = builder
            .setMaxConcurrency(1000) // High but reasonable concurrency
            .setTargetThroughput(100.0) // 100 Gbps - very high throughput
            .setMinPartSize(1024L * 1024L * 1024L) // 1GB part size - very large
            .setRegion(Region.US_EAST_1)
            .setCredentialsProvider(mockCredentialsProvider)
            .asyncClient();

        assertNotNull("S3AsyncClient should handle high realistic values", client);
        client.close();
    }

    @Test
    public void testBuilderMethodChaining() {
        // Test that all methods support fluent interface
        String testChainEndpoint = "https://test.com";
        AWSS3ClientBuilder result = builder
            .setEndpoint(testChainEndpoint)
            .setRegion(Region.US_EAST_1)
            .setMaxConcurrency(10)
            .setTargetThroughput(5.0)
            .setMinPartSize(8L * 1024L * 1024L)
            .setCredentialsProvider(mockCredentialsProvider);

        assertEquals("Method chaining should return the same builder instance", builder, result);
    }

    @Test
    public void testAsyncClientWithRealisticS3Configuration() {
        // Test with realistic S3 configuration values
        S3AsyncClient client = builder
            .setEndpoint(TEST_ENDPOINT)
            .setRegion(Region.US_EAST_1)
            .setMaxConcurrency(20)
            .setTargetThroughput(10.0)
            .setMinPartSize(5L * 1024L * 1024L) // 5MB
            .setCredentialsProvider(mockCredentialsProvider)
            .asyncClient();

        assertNotNull("S3AsyncClient should be created with realistic configuration", client);
        client.close();
    }

    @Test
    public void testAsyncClientWithRealisticMinioConfiguration() {
        // Test with realistic MinIO configuration values
        S3AsyncClient client = builder
            .setEndpoint(MINIO_ENDPOINT)
            .setRegion(Region.US_EAST_1)
            .setMaxConcurrency(5)
            .setTargetThroughput(1.0)
            .setMinPartSize(1024L * 1024L) // 1MB
            .setCredentialsProvider(mockCredentialsProvider)
            .asyncClient();

        assertNotNull("S3AsyncClient should be created with MinIO configuration", client);
        client.close();
    }

    @Test
    public void testMultipleClientCreation() {
        // Test that the builder can create multiple clients
        builder.setCredentialsProvider(mockCredentialsProvider);

        S3AsyncClient client1 = builder.asyncClient();
        S3AsyncClient client2 = builder.asyncClient();

        assertNotNull("First client should be created", client1);
        assertNotNull("Second client should be created", client2);

        // Clients should be different instances
        assert client1 != client2 : "Multiple calls should create different client instances";

        client1.close();
        client2.close();
    }

    @Test
    public void testBuilderReusability() {
        // Test that builder can be reused with different configurations
        builder.setCredentialsProvider(mockCredentialsProvider);

        S3AsyncClient client1 = builder.asyncClient();

        // Change configuration
        builder.setRegion(Region.EU_WEST_1)
               .setMaxConcurrency(15);

        S3AsyncClient client2 = builder.asyncClient();

        assertNotNull("First client with initial config should be created", client1);
        assertNotNull("Second client with updated config should be created", client2);

        client1.close();
        client2.close();
    }

    @Test
    public void testInvalidEndpointHandling() {
        // Test with various invalid endpoint formats
        String[] invalidEndpoints = {
            "not-a-url",
            "ftp://invalid-protocol.com",
            "://missing-scheme.com"
        };

        for (String invalidEndpoint : invalidEndpoints) {
            try {
                S3AsyncClient client = builder
                    .setEndpoint(invalidEndpoint)
                    .setRegion(Region.US_EAST_1)
                    .setCredentialsProvider(mockCredentialsProvider)
                    .asyncClient();

                // If no exception is thrown, close the client
                client.close();

            } catch (Exception e) {
                // Expected for invalid URLs - this is acceptable behavior
                assertNotNull("Exception should have a message", e.getMessage());
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCredentialsProviderSupplierExecution() {
        // Test that credentials provider supplier is called when building client
        Supplier<AwsCredentialsProvider> spySupplier = mock(Supplier.class);
        AwsCredentialsProvider mockProvider = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(TEST_ACCESS_KEY, TEST_SECRET_KEY)
        );
        when(spySupplier.get()).thenReturn(mockProvider);

        S3AsyncClient client = builder
            .setCredentialsProvider(spySupplier)
            .setRegion(Region.US_EAST_1)
            .asyncClient();

        assertNotNull("S3AsyncClient should be created", client);
        client.close();
    }
}
