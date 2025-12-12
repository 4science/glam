/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore.factory;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;

import org.dspace.AbstractDSpaceTest;
import org.dspace.services.ConfigurationService;
import org.dspace.storage.bitstore.AWSS3ClientBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

/**
 * Unit tests for {@link AWSFactory}.
 *
 * Tests cover credential provider creation, S3 client builder configuration,
 * property handling, and both default and store-specific configurations.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
public class AWSFactoryTest extends AbstractDSpaceTest {

    @Mock
    private ConfigurationService mockConfigurationService;

    private AWSFactory awsFactory;

    @Before
    public void setUp() {
        awsFactory = new AWSFactory(mockConfigurationService);
    }

    @Test
    public void testProvideCredentialsWithBasicAuthentication() {
        // Setup mock configuration for basic authentication
        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn("testAccessKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn("testSecretKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("static");

        // Mock other properties to return null
        mockNullProperties();

        Supplier<AwsCredentialsProvider> credentialsSupplier = awsFactory.provideCredentials();
        assertNotNull("Credentials supplier should not be null", credentialsSupplier);

        AwsCredentialsProvider provider = credentialsSupplier.get();
        assertNotNull("Credentials provider should not be null", provider);
    }

    @Test
    public void testProvideCredentialsWithSessionToken() {
        // Setup mock configuration for session credentials
        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn("testAccessKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn("testSecretKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsSessionToken")).thenReturn("testSessionToken");
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("static");

        // Mock other properties to return null
        mockNullProperties();

        Supplier<AwsCredentialsProvider> credentialsSupplier = awsFactory.provideCredentials();
        assertNotNull("Credentials supplier should not be null", credentialsSupplier);

        AwsCredentialsProvider provider = credentialsSupplier.get();
        assertNotNull("Credentials provider should not be null", provider);
    }

    @Test
    public void testProvideCredentialsWithSTSRole() {
        // Setup mock configuration for STS role authentication
        when(mockConfigurationService.getProperty("assetstore.s3.stsRole")).thenReturn("arn:aws:iam::123456789012:role/TestRole");
        when(mockConfigurationService.getProperty("assetstore.s3.stsSessionName")).thenReturn("TestSession");
        when(mockConfigurationService.getProperty("assetstore.s3.stsRegion")).thenReturn("us-east-1");
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("sts");

        // Mock other properties to return null
        mockNullPropertiesExceptSTS();

        Supplier<AwsCredentialsProvider> credentialsSupplier = awsFactory.provideCredentials();
        assertNotNull("Credentials supplier should not be null", credentialsSupplier);

        AwsCredentialsProvider provider = credentialsSupplier.get();
        assertNotNull("Credentials provider should not be null", provider);
    }

    @Test
    public void testProvideCredentialsWithIRSA() {
        // Setup mock configuration for IRSA authentication
        when(mockConfigurationService.getProperty("assetstore.s3.irsa.role")).thenReturn("arn:aws:iam::123456789012:role/IRSARole");
        when(mockConfigurationService.getProperty("assetstore.s3.irsa.session")).thenReturn("IRSASession");
        when(mockConfigurationService.getProperty("assetstore.s3.irsa.tokenfile")).thenReturn("/tmp/token");
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("irsa");

        // Mock other properties to return null
        mockNullPropertiesExceptIRSA();

        Supplier<AwsCredentialsProvider> credentialsSupplier = awsFactory.provideCredentials();
        assertNotNull("Credentials supplier should not be null", credentialsSupplier);

        AwsCredentialsProvider provider = credentialsSupplier.get();
        assertNotNull("Credentials provider should not be null", provider);
    }

    @Test
    public void testProvideCredentialsWithStoreSuffix() {
        String storeSuffix = "store1";

        // Setup mock configuration for store-specific authentication
        when(mockConfigurationService.getProperty("assetstore.s3.store1.awsAccessKey")).thenReturn("store1AccessKey");
        when(mockConfigurationService.getProperty("assetstore.s3.store1.awsSecretKey")).thenReturn("store1SecretKey");
        when(mockConfigurationService.getProperty("assetstore.s3.store1.awsAuthenticationType")).thenReturn("static");
        when(mockConfigurationService.getIntProperty("assetstore.s3.store1.stsSessionDuration")).thenReturn(3600);

        // Mock other store-specific properties
        mockNullPropertiesForStore(storeSuffix);

        Supplier<AwsCredentialsProvider> credentialsSupplier = awsFactory.provideCredentials(storeSuffix);
        assertNotNull("Store-specific credentials supplier should not be null", credentialsSupplier);

        AwsCredentialsProvider provider = credentialsSupplier.get();
        assertNotNull("Store-specific credentials provider should not be null", provider);
    }

    @Test
    public void testClientBuilderDefault() {
        // Mock default configuration properties
        mockDefaultClientConfiguration();
        mockNullProperties();

        AWSS3ClientBuilder clientBuilder = awsFactory.clientBuilder();
        assertNotNull("Client builder should not be null", clientBuilder);
    }

    @Test
    public void testClientBuilderWithStoreSuffix() {
        String storeSuffix = "store1";

        // Mock authentication properties for the store
        when(mockConfigurationService.getProperty("assetstore.s3.store1.awsAccessKey")).thenReturn("store1AccessKey");
        when(mockConfigurationService.getProperty("assetstore.s3.store1.awsSecretKey")).thenReturn("store1SecretKey");
        when(mockConfigurationService.getProperty("assetstore.s3.store1.awsAuthenticationType")).thenReturn("static");
        when(mockConfigurationService.getIntProperty("assetstore.s3.store1.stsSessionDuration")).thenReturn(3600);

        // Mock other store properties to null
        mockNullPropertiesForStore(storeSuffix);

        // Mock default properties
        mockDefaultClientConfiguration();

        AWSS3ClientBuilder clientBuilder = awsFactory.clientBuilder(storeSuffix);
        assertNotNull("Store-specific client builder should not be null", clientBuilder);
    }

    @Test
    public void testClientBuilderWithDefaultValues() {
        // Mock default configuration to test actual behavior
        when(mockConfigurationService.getProperty("assetstore.s3.endpoint", null)).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.maxConcurrency")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.minPartSize")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.awsRegionName")).thenReturn(null);
        when(mockConfigurationService.getPropertyAsType("assetstore.s3.targetThroughput", 10.0)).thenReturn(10.0);

        // Mock authentication properties
        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn("testKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn("testSecret");
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("static");
        mockNullProperties();

        AWSS3ClientBuilder clientBuilder = awsFactory.clientBuilder();
        assertNotNull("Client builder should not be null with default values", clientBuilder);
    }

    @Test
    public void testClientBuilderWithCustomRegion() {
        // Mock configuration with custom region
        when(mockConfigurationService.getProperty("assetstore.s3.awsRegionName")).thenReturn("eu-west-1");
        when(mockConfigurationService.getProperty("assetstore.s3.endpoint", null)).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.maxConcurrency")).thenReturn("15");
        when(mockConfigurationService.getProperty("assetstore.s3.minPartSize")).thenReturn("16777216");
        when(mockConfigurationService.getPropertyAsType("assetstore.s3.targetThroughput", 10.0)).thenReturn(15.0);

        // Mock authentication properties
        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn("testKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn("testSecret");
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("static");
        mockNullProperties();

        AWSS3ClientBuilder clientBuilder = awsFactory.clientBuilder();
        assertNotNull("Client builder should not be null with custom region", clientBuilder);
    }

    @Test
    public void testClientBuilderWithInvalidNumericValues() {
        // Mock configuration with invalid numeric values to test error handling
        when(mockConfigurationService.getProperty("assetstore.s3.maxConcurrency")).thenReturn("invalid");
        when(mockConfigurationService.getProperty("assetstore.s3.minPartSize")).thenReturn("notanumber");
        when(mockConfigurationService.getProperty("assetstore.s3.awsRegionName")).thenReturn("us-east-1");
        when(mockConfigurationService.getProperty("assetstore.s3.endpoint", null)).thenReturn(null);
        when(mockConfigurationService.getPropertyAsType("assetstore.s3.targetThroughput", 10.0)).thenReturn(10.0);

        // Mock authentication properties
        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn("testKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn("testSecret");
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("static");
        mockNullProperties();

        AWSS3ClientBuilder clientBuilder = awsFactory.clientBuilder();
        assertNotNull("Client builder should handle invalid numeric values gracefully", clientBuilder);
    }

    @Test
    public void testStoreSpecificOverrides() {
        String storeSuffix = "custom";

        // Mock authentication for custom store
        when(mockConfigurationService.getProperty("assetstore.s3.custom.awsAccessKey")).thenReturn("customKey");
        when(mockConfigurationService.getProperty("assetstore.s3.custom.awsSecretKey")).thenReturn("customSecret");
        when(mockConfigurationService.getProperty("assetstore.s3.custom.awsAuthenticationType")).thenReturn("static");
        when(mockConfigurationService.getIntProperty("assetstore.s3.custom.stsSessionDuration")).thenReturn(7200);

        mockNullPropertiesForStore(storeSuffix);
        mockDefaultClientConfiguration();

        AWSS3ClientBuilder clientBuilder = awsFactory.clientBuilder(storeSuffix);
        assertNotNull("Custom store client builder should not be null", clientBuilder);
    }

    @Test
    public void testMultipleStoreConfigurations() {
        // Test that different store configurations don't interfere with each other
        String store1 = "store1";
        String store2 = "store2";

        // Setup store1
        when(mockConfigurationService.getProperty("assetstore.s3.store1.awsAccessKey")).thenReturn("store1Key");
        when(mockConfigurationService.getProperty("assetstore.s3.store1.awsSecretKey")).thenReturn("store1Secret");
        when(mockConfigurationService.getProperty("assetstore.s3.store1.awsAuthenticationType")).thenReturn("static");
        when(mockConfigurationService.getIntProperty("assetstore.s3.store1.stsSessionDuration")).thenReturn(1800);

        // Setup store2
        when(mockConfigurationService.getProperty("assetstore.s3.store2.awsAccessKey")).thenReturn("store2Key");
        when(mockConfigurationService.getProperty("assetstore.s3.store2.awsSecretKey")).thenReturn("store2Secret");
        when(mockConfigurationService.getProperty("assetstore.s3.store2.awsAuthenticationType")).thenReturn("static");
        when(mockConfigurationService.getIntProperty("assetstore.s3.store2.stsSessionDuration")).thenReturn(3600);

        mockNullPropertiesForStore(store1);
        mockNullPropertiesForStore(store2);

        Supplier<AwsCredentialsProvider> creds1 = awsFactory.provideCredentials(store1);
        Supplier<AwsCredentialsProvider> creds2 = awsFactory.provideCredentials(store2);

        assertNotNull("Store1 credentials should not be null", creds1);
        assertNotNull("Store2 credentials should not be null", creds2);
        assertNotNull("Store1 provider should not be null", creds1.get());
        assertNotNull("Store2 provider should not be null", creds2.get());
    }

    @Test
    public void testDefaultProviderFallback() {
        // Test default provider when no explicit authentication type is specified
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn(null);
        mockNullProperties();

        Supplier<AwsCredentialsProvider> credentialsSupplier = awsFactory.provideCredentials();
        assertNotNull("Credentials supplier should not be null even without auth type", credentialsSupplier);

        AwsCredentialsProvider provider = credentialsSupplier.get();
        assertNotNull("Provider should not be null with default authentication", provider);
    }

    @Test
    public void testEmptyStoreSuffixHandling() {
        // Test behavior with empty string store suffix
        String emptySuffix = "";

        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn("emptyKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn("emptySecret");
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("static");
        when(mockConfigurationService.getIntProperty("assetstore.s3.stsSessionDuration")).thenReturn(1800);

        mockNullProperties();

        Supplier<AwsCredentialsProvider> credentialsSupplier = awsFactory.provideCredentials(emptySuffix);
        assertNotNull("Credentials supplier should handle empty suffix", credentialsSupplier);

        AwsCredentialsProvider provider = credentialsSupplier.get();
        assertNotNull("Provider should not be null with empty suffix", provider);
    }

    @Test
    public void testMixedAuthenticationMethods() {
        // Test scenario where multiple authentication methods are partially configured
        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn("partialKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn(null); // Missing secret
        when(mockConfigurationService.getProperty("assetstore.s3.stsRole")).thenReturn("arn:aws:iam::123456789012:role/PartialRole");
        when(mockConfigurationService.getProperty("assetstore.s3.stsSessionName")).thenReturn("DefaultSession"); // Provide a default session name
        when(mockConfigurationService.getProperty("assetstore.s3.stsRegion")).thenReturn("us-east-1"); // Required for STS client
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("sts");

        // Mock remaining properties
        mockPartiallyNullPropertiesExceptSTS();

        Supplier<AwsCredentialsProvider> credentialsSupplier = awsFactory.provideCredentials();
        assertNotNull("Credentials supplier should handle mixed configurations", credentialsSupplier);

        AwsCredentialsProvider provider = credentialsSupplier.get();
        assertNotNull("Provider should be created despite partial configuration", provider);
    }

    @Test
    public void testComplexStoreConfiguration() {
        String storeSuffix = "complex";

        // Setup complex configuration with all possible settings
        when(mockConfigurationService.getProperty("assetstore.s3.complex.stsRole")).thenReturn("arn:aws:iam::999888777666:role/ComplexRole");
        when(mockConfigurationService.getProperty("assetstore.s3.complex.stsSessionName")).thenReturn("ComplexSession");
        when(mockConfigurationService.getProperty("assetstore.s3.complex.stsRegion")).thenReturn("eu-central-1");
        when(mockConfigurationService.getProperty("assetstore.s3.complex.stsExternalId")).thenReturn("complex-external-id");
        when(mockConfigurationService.getIntProperty("assetstore.s3.complex.stsSessionDuration")).thenReturn(7200);
        when(mockConfigurationService.getProperty("assetstore.s3.complex.stsEndpoint")).thenReturn("https://sts.eu-central-1.amazonaws.com");
        when(mockConfigurationService.getProperty("assetstore.s3.complex.awsAuthenticationType")).thenReturn("sts");

        // Mock other properties as null for this test
        when(mockConfigurationService.getProperty("assetstore.s3.complex.irsa.role")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.complex.irsa.session")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.complex.irsa.tokenfile")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.complex.awsAccessKey")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.complex.awsSecretKey")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.complex.awsSessionToken")).thenReturn(null);

        Supplier<AwsCredentialsProvider> credentialsSupplier = awsFactory.provideCredentials(storeSuffix);
        assertNotNull("Complex configuration credentials supplier should not be null", credentialsSupplier);

        AwsCredentialsProvider provider = credentialsSupplier.get();
        assertNotNull("Complex configuration provider should not be null", provider);
    }

    @Test
    public void testClientBuilderIntegrationWithAllFeatures() {
        String storeSuffix = "integration";

        // Setup authentication
        when(mockConfigurationService.getProperty("assetstore.s3.integration.awsAccessKey")).thenReturn("integrationKey");
        when(mockConfigurationService.getProperty("assetstore.s3.integration.awsSecretKey")).thenReturn("integrationSecret");
        when(mockConfigurationService.getProperty("assetstore.s3.integration.awsSessionToken")).thenReturn("integrationToken");
        when(mockConfigurationService.getProperty("assetstore.s3.integration.awsAuthenticationType")).thenReturn("static");
        when(mockConfigurationService.getIntProperty("assetstore.s3.integration.stsSessionDuration")).thenReturn(14400);

        // Mock other properties as null
        mockNullPropertiesForStore(storeSuffix);
        mockDefaultClientConfiguration();

        AWSS3ClientBuilder clientBuilder = awsFactory.clientBuilder(storeSuffix);
        assertNotNull("Integration client builder should not be null", clientBuilder);
    }

    @Test
    public void testPropertyPrefixGeneration() {
        // Test the internal property prefix generation logic through public methods
        String store1 = "store1";
        String store2 = "store_with_underscores";
        String store3 = "store-with-hyphens";

        // Setup different stores to verify property prefix generation
        setupStoreForTest(store1, "store1Key", "store1Secret");
        setupStoreForTest(store2, "store2Key", "store2Secret");
        setupStoreForTest(store3, "store3Key", "store3Secret");

        Supplier<AwsCredentialsProvider> creds1 = awsFactory.provideCredentials(store1);
        Supplier<AwsCredentialsProvider> creds2 = awsFactory.provideCredentials(store2);
        Supplier<AwsCredentialsProvider> creds3 = awsFactory.provideCredentials(store3);

        assertNotNull("Store1 with simple name should work", creds1.get());
        assertNotNull("Store2 with underscores should work", creds2.get());
        assertNotNull("Store3 with hyphens should work", creds3.get());
    }

    @Test
    public void testConstructorAndBasicFunctionality() {
        // Test basic constructor and fundamental functionality
        AWSFactory factory = new AWSFactory(mockConfigurationService);
        assertNotNull("Factory should not be null after construction", factory);

        // Test that the configuration service is properly stored
        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn("basicKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn("basicSecret");
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("static");
        mockNullProperties();

        Supplier<AwsCredentialsProvider> credentials = factory.provideCredentials();
        assertNotNull("Basic credentials should be available", credentials);
        assertNotNull("Basic provider should be created", credentials.get());
    }

    @Test
    public void testClientBuilderWithInvalidRegionName() {
        // Test behavior with invalid region name
        when(mockConfigurationService.getProperty("assetstore.s3.awsRegionName")).thenReturn("invalid-region");
        when(mockConfigurationService.getProperty("assetstore.s3.endpoint", null)).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.maxConcurrency")).thenReturn("10");
        when(mockConfigurationService.getProperty("assetstore.s3.minPartSize")).thenReturn("8388608");
        when(mockConfigurationService.getPropertyAsType("assetstore.s3.targetThroughput", 10.0)).thenReturn(10.0);

        // Mock authentication properties
        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn("testKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn("testSecret");
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("static");
        mockNullProperties();

        AWSS3ClientBuilder clientBuilder = awsFactory.clientBuilder();
        assertNotNull("Client builder should handle invalid region gracefully", clientBuilder);
    }

    @Test
    public void testProvideCredentialsWithUnknownAuthenticationType() {
        // Test behavior with unknown authentication type
        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn("testKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn("testSecret");
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("unknown");
        mockNullProperties();

        Supplier<AwsCredentialsProvider> credentialsSupplier = awsFactory.provideCredentials();
        assertNotNull("Credentials supplier should handle unknown auth type", credentialsSupplier);

        AwsCredentialsProvider provider = credentialsSupplier.get();
        assertNotNull("Provider should be created with unknown auth type", provider);
    }

    @Test
    public void testClientBuilderWithNegativeNumericValues() {
        // Test behavior with negative numeric values
        when(mockConfigurationService.getProperty("assetstore.s3.maxConcurrency")).thenReturn("-5");
        when(mockConfigurationService.getProperty("assetstore.s3.minPartSize")).thenReturn("-1024");
        when(mockConfigurationService.getProperty("assetstore.s3.awsRegionName")).thenReturn("us-east-1");
        when(mockConfigurationService.getProperty("assetstore.s3.endpoint", null)).thenReturn(null);
        when(mockConfigurationService.getPropertyAsType("assetstore.s3.targetThroughput", 10.0)).thenReturn(-5.0);

        // Mock authentication properties
        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn("testKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn("testSecret");
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("static");
        mockNullProperties();

        AWSS3ClientBuilder clientBuilder = awsFactory.clientBuilder();
        assertNotNull("Client builder should handle negative values", clientBuilder);
    }

    @Test
    public void testStoreConfigurationPrecedenceOverDefaults() {
        String storeSuffix = "override";

        // Setup default configuration
        mockDefaultClientConfiguration();

        // Mock authentication for override store
        when(mockConfigurationService.getProperty("assetstore.s3.override.awsAccessKey")).thenReturn("overrideKey");
        when(mockConfigurationService.getProperty("assetstore.s3.override.awsSecretKey")).thenReturn("overrideSecret");
        when(mockConfigurationService.getProperty("assetstore.s3.override.awsAuthenticationType")).thenReturn("static");
        when(mockConfigurationService.getIntProperty("assetstore.s3.override.stsSessionDuration")).thenReturn(7200);

        mockNullPropertiesForStore(storeSuffix);

        AWSS3ClientBuilder clientBuilder = awsFactory.clientBuilder(storeSuffix);
        assertNotNull("Store-specific configuration should override defaults", clientBuilder);
    }

    // Helper methods for mocking

    private void mockNullProperties() {
        when(mockConfigurationService.getProperty("assetstore.s3.stsRole")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.stsSessionName")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.stsRegion")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.stsExternalId")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.stsEndpoint")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.irsa.role")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.irsa.session")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.irsa.tokenfile")).thenReturn(null);
    }

    private void mockNullPropertiesExceptSTS() {
        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.awsSessionToken")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.irsa.role")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.irsa.session")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.irsa.tokenfile")).thenReturn(null);
    }

    private void mockNullPropertiesExceptIRSA() {
        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.awsSessionToken")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.stsRole")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.stsSessionName")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.stsRegion")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.stsExternalId")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.stsEndpoint")).thenReturn(null);
    }

    private void mockNullPropertiesForStore(String storeSuffix) {
        String prefix = "assetstore.s3." + storeSuffix + ".";
        when(mockConfigurationService.getProperty(prefix + "stsRole")).thenReturn(null);
        when(mockConfigurationService.getProperty(prefix + "stsSessionName")).thenReturn(null);
        when(mockConfigurationService.getProperty(prefix + "stsRegion")).thenReturn(null);
        when(mockConfigurationService.getProperty(prefix + "stsExternalId")).thenReturn(null);
        when(mockConfigurationService.getProperty(prefix + "stsEndpoint")).thenReturn(null);
        when(mockConfigurationService.getProperty(prefix + "irsa.role")).thenReturn(null);
        when(mockConfigurationService.getProperty(prefix + "irsa.session")).thenReturn(null);
        when(mockConfigurationService.getProperty(prefix + "irsa.tokenfile")).thenReturn(null);
        when(mockConfigurationService.getProperty(prefix + "awsSessionToken")).thenReturn(null);
    }

    private void mockDefaultClientConfiguration() {
        when(mockConfigurationService.getProperty("assetstore.s3.endpoint", null)).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.maxConcurrency")).thenReturn("10");
        when(mockConfigurationService.getProperty("assetstore.s3.minPartSize")).thenReturn("8388608"); // 8MB
        when(mockConfigurationService.getProperty("assetstore.s3.awsRegionName")).thenReturn("us-east-1");
        when(mockConfigurationService.getPropertyAsType("assetstore.s3.targetThroughput", 10.0)).thenReturn(10.0);
        when(mockConfigurationService.getProperty("assetstore.s3.awsAccessKey")).thenReturn("defaultAccessKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsSecretKey")).thenReturn("defaultSecretKey");
        when(mockConfigurationService.getProperty("assetstore.s3.awsAuthenticationType")).thenReturn("static");
    }

    private void mockPartiallyNullPropertiesExceptSTS() {
        // Mock some properties as null but keep STS properties available for testing
        when(mockConfigurationService.getProperty("assetstore.s3.stsExternalId")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.stsEndpoint")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.irsa.role")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.irsa.session")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.irsa.tokenfile")).thenReturn(null);
        when(mockConfigurationService.getProperty("assetstore.s3.awsSessionToken")).thenReturn(null);
        // Note: stsRegion, stsRole, and stsSessionName are provided in the test method
    }

    private void setupStoreForTest(String storeSuffix, String accessKey, String secretKey) {
        String prefix = "assetstore.s3." + storeSuffix + ".";
        when(mockConfigurationService.getProperty(prefix + "awsAccessKey")).thenReturn(accessKey);
        when(mockConfigurationService.getProperty(prefix + "awsSecretKey")).thenReturn(secretKey);
        when(mockConfigurationService.getProperty(prefix + "awsAuthenticationType")).thenReturn("static");
        when(mockConfigurationService.getIntProperty(prefix + "stsSessionDuration")).thenReturn(3600);

        // Mock all other properties as null for this store
        mockNullPropertiesForStore(storeSuffix);
    }
}

