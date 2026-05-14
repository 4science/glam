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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.dspace.AbstractDSpaceTest;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;

/**
 * Unit tests for {@link AWSCredentialsProviderBuilder}.
 *
 * Covers static, session, and basic provider creation.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
public class AWSCredentialsProviderBuilderTest extends AbstractDSpaceTest {
    @Test
    public void testIrsaProvider() {
        // These are dummy values; in real use, the token file must exist and be valid.
        String roleArn = "arn:aws:iam::123456789012:role/IRSA-Test-Role";
        String roleSessionName = "IRSA-Session";
        String webIdentityTokenFile = "/tmp/fake-token.jwt";

        AwsCredentialsProvider provider =
            AWSCredentialsProviderBuilder.irsa(roleArn, roleSessionName, webIdentityTokenFile);
        assertNotNull("IRSA provider should not be null", provider);
        assertTrue(provider instanceof WebIdentityTokenFileCredentialsProvider);
    }

    @Test
    public void testStsProvider() {
        // Use dummy values; this will attempt to create a provider but not actually call AWS
        String roleArn = "arn:aws:iam::123456789012:role/STS-Test-Role";
        String sessionName = "STS-Session";
        String region = "us-east-1";
        String endpoint = null;
        Integer duration = 1200;
        String externalId = "external-id-123";

        AwsCredentialsProvider provider =
            AWSCredentialsProviderBuilder.sts(roleArn, sessionName, region, endpoint, duration, externalId);
        assertNotNull("STS provider should not be null", provider);
    }

    @Test
    public void testBuilderIrsaProvider() {
        AWSCredentialsProviderBuilder builder = AWSCredentialsProviderBuilder.builder()
            .setIrsaRole("arn:aws:iam::123456789012:role/IRSA-Test-Role")
            .setIrsaRoleSessionName("IRSA-Session")
            .setWebIdentityTokenFile("/tmp/fake-token.jwt");
        AwsCredentialsProvider provider = builder.build("irsa").get();
        assertNotNull("IRSA provider from builder should not be null", provider);
        assertTrue(provider instanceof WebIdentityTokenFileCredentialsProvider);
    }

    @Test
    public void testBuilderStsProvider() {
        AWSCredentialsProviderBuilder builder = AWSCredentialsProviderBuilder.builder()
            .setStsRole("arn:aws:iam::123456789012:role/STS-Test-Role")
            .setStsSessionName("STS-Session")
            .setStsRegion("us-east-1")
            .setStsSessionDuration(1200)
            .setStsExternalId("external-id-123");
        AwsCredentialsProvider provider = builder.build("sts").get();
        assertNotNull("STS provider from builder should not be null", provider);
    }

    @Test
    public void testBasicProviderWithValidKeys() {
        String accessKey = "AKI1234567890EXAMPLE";
        String secretKey = "abc1234567890secretEXAMPLE";
        AwsCredentialsProvider provider = AWSCredentialsProviderBuilder.basic(
            accessKey,
            secretKey
        );

        assertNotNull("Provider should not be null", provider);
        AwsCredentials credentials = provider.resolveCredentials();
        assertTrue(credentials instanceof AwsBasicCredentials);
        assertEquals(accessKey, credentials.accessKeyId());
        assertEquals(secretKey, credentials.secretAccessKey());
    }

    @Test
    public void testBasicProviderWithBlankKeysThrows() {
        assertThrows(
            IllegalArgumentException.class,
            () -> AWSCredentialsProviderBuilder.basic("", "secret")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> AWSCredentialsProviderBuilder.basic("access", "")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> AWSCredentialsProviderBuilder.basic(null, "secret")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> AWSCredentialsProviderBuilder.basic("access", null)
        );
    }

    @Test
    public void testSessionProvider() {
        String accessKey = "AKISESSION1234567890";
        String secretKey = "sessionSecretKeyEXAMPLE";
        String sessionToken = "sessionTokenEXAMPLE";
        AwsCredentialsProvider provider = AWSCredentialsProviderBuilder.session(
            accessKey,
            secretKey,
            sessionToken
        );

        assertNotNull("Provider should not be null", provider);
        AwsCredentials credentials = provider.resolveCredentials();
        assertTrue(credentials instanceof AwsSessionCredentials);
        assertEquals(accessKey, credentials.accessKeyId());
        assertEquals(secretKey, credentials.secretAccessKey());
        assertEquals(sessionToken, ((AwsSessionCredentials) credentials).sessionToken());
    }

    @Test
    public void testStaticCredentialsProvider() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
            "STATICKEY",
            "STATICSECRET"
        );
        StaticCredentialsProvider provider =
            AWSCredentialsProviderBuilder.staticCredentials(credentials);

        assertNotNull("Provider should not be null", provider);
        AwsCredentials providedCreds = provider.resolveCredentials();
        assertEquals("STATICKEY", providedCreds.accessKeyId());
        assertEquals("STATICSECRET", providedCreds.secretAccessKey());
    }

    @Test
    public void testDefaultProvider() {
        AwsCredentialsProvider provider =
            AWSCredentialsProviderBuilder.defaultProvider();
        assertNotNull("Provider should not be null", provider);
        assertTrue(provider instanceof DefaultCredentialsProvider);
    }

    @Test
    public void testBuilderStaticProviderWithSessionToken() {
        AWSCredentialsProviderBuilder builder =
            AWSCredentialsProviderBuilder.builder()
                .setAwsAccessKey("AKISTATIC123")
                .setAwsSecretKey("STATICSECRET123")
                .setAwsSessionToken("SESSIONTOKEN123");

        AwsCredentialsProvider provider = builder.build("static").get();
        assertNotNull("Provider should not be null", provider);
        AwsCredentials credentials = provider.resolveCredentials();
        assertTrue(credentials instanceof AwsSessionCredentials);
        assertEquals("AKISTATIC123", credentials.accessKeyId());
        assertEquals("STATICSECRET123", credentials.secretAccessKey());
        assertEquals("SESSIONTOKEN123", ((AwsSessionCredentials) credentials).sessionToken());
    }

    @Test
    public void testBuilderStaticProviderWithoutSessionToken() {
        AWSCredentialsProviderBuilder builder =
            AWSCredentialsProviderBuilder.builder()
                .setAwsAccessKey("AKISTATIC456")
                .setAwsSecretKey("STATICSECRET456");

        AwsCredentialsProvider provider = builder.build("static").get();
        assertNotNull("Provider should not be null", provider);
        AwsCredentials credentials = provider.resolveCredentials();
        assertTrue(credentials instanceof AwsBasicCredentials);
        assertEquals("AKISTATIC456", credentials.accessKeyId());
        assertEquals("STATICSECRET456", credentials.secretAccessKey());
    }

    @Test
    public void testBuilderDefaultProvider() {
        AWSCredentialsProviderBuilder builder = AWSCredentialsProviderBuilder.builder();
        AwsCredentialsProvider provider = builder.build("default").get();
        assertNotNull("Default provider should not be null", provider);
        assertTrue(provider instanceof DefaultCredentialsProvider);
    }

    @Test
    public void testBuilderInvalidTypeDefaultsToDefault() {
        AWSCredentialsProviderBuilder builder = AWSCredentialsProviderBuilder.builder();
        AwsCredentialsProvider provider = builder.build("invalid").get();
        assertNotNull("Provider should not be null", provider);
        assertTrue(provider instanceof DefaultCredentialsProvider);
    }

    @Test
    public void testStsProviderWithBlankRoleThrows() {
        assertThrows(
            IllegalArgumentException.class, () ->
                AWSCredentialsProviderBuilder.sts("", "session", "us-east-1", null, 1200, null)
        );
    }

    @Test
    public void testStsProviderWithInvalidDuration() {
        // This will create the provider, but log warning for invalid duration
        AwsCredentialsProvider provider =
            AWSCredentialsProviderBuilder.sts("role", "session", "us-east-1", null, 500, null); // <900
        assertNotNull("STS provider should not be null", provider);
    }

    @Test
    public void testBuilderDefaultWithKeysSetUsesStatic() {
        AWSCredentialsProviderBuilder builder = AWSCredentialsProviderBuilder.builder()
            .setAwsAccessKey("KEY")
            .setAwsSecretKey("SECRET");
        AwsCredentialsProvider provider = builder.build("default").get();
        assertNotNull("Provider should not be null", provider);
        AwsCredentials credentials = provider.resolveCredentials();
        assertTrue(credentials instanceof AwsBasicCredentials);
        assertEquals("KEY", credentials.accessKeyId());
        assertEquals("SECRET", credentials.secretAccessKey());
    }



    @Test
    public void testBuilderChaining() {
        // Test that builder methods can be chained and return the builder instance
        AWSCredentialsProviderBuilder builder = AWSCredentialsProviderBuilder.builder()
            .setIrsaRole("arn")
            .setIrsaRoleSessionName("session")
            .setWebIdentityTokenFile("file")
            .setStsRole("stsrole")
            .setStsSessionName("stssession")
            .setStsRegion("region")
            .setStsEndpoint("endpoint")
            .setStsSessionDuration(1000)
            .setStsExternalId("extid")
            .setAwsAccessKey("key")
            .setAwsSecretKey("secret")
            .setAwsSessionToken("token");

        assertEquals("arn", builder.irsaRole);
        assertEquals("session", builder.irsaRoleSessionName);
        assertEquals("file", builder.webIdentityTokenFile);
        assertEquals("stsrole", builder.stsRole);
        assertEquals("stssession", builder.stsSessionName);
        assertEquals("region", builder.stsRegion);
        assertEquals("endpoint", builder.stsEndpoint);
        assertEquals(Integer.valueOf(1000), builder.stsSessionDuration);
        assertEquals("extid", builder.stsExternalId);
        assertEquals("key", builder.awsAccessKey);
        assertEquals("secret", builder.awsSecretKey);
        assertEquals("token", builder.awsSessionToken);
        assertNotNull("Builder should not be null after chaining", builder);
        // Test that we can build a provider with the configured settings
        AwsCredentialsProvider provider = builder.build("static").get();

        assertNotNull("Provider should not be null", provider);
    }

    @Test
    public void testIrsaProviderWithNulls() {
        AwsCredentialsProvider provider = AWSCredentialsProviderBuilder.irsa(null, null, null);
        assertNotNull("IRSA provider should not be null", provider);
        assertTrue(provider instanceof WebIdentityTokenFileCredentialsProvider);
    }
}
