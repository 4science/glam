/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import org.dspace.AbstractDSpaceTest;
import org.dspace.storage.AWSCredentialsProviderBuilder.AWSCredentialProviderType;
import org.junit.Test;

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

        AWSCredentialsProvider provider =
            AWSCredentialsProviderBuilder.irsa(roleArn, roleSessionName, webIdentityTokenFile);
        assertNotNull("IRSA provider should not be null", provider);
        assertTrue(provider instanceof WebIdentityTokenCredentialsProvider);
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

        AWSCredentialsProvider provider =
            AWSCredentialsProviderBuilder.sts(roleArn, sessionName, region, endpoint, duration, externalId);
        assertNotNull("STS provider should not be null", provider);
        assertTrue(provider instanceof STSAssumeRoleSessionCredentialsProvider);
    }

    @Test
    public void testBuilderIrsaProvider() {
        AWSCredentialsProviderBuilder builder = AWSCredentialsProviderBuilder.builder()
            .setRoleArn("arn:aws:iam::123456789012:role/IRSA-Test-Role")
            .setRoleSessionName("IRSA-Session")
            .setWebIdentityTokenFile("/tmp/fake-token.jwt");
        AWSCredentialsProvider provider = builder.build("irsa").get();
        assertNotNull("IRSA provider from builder should not be null", provider);
        assertTrue(provider instanceof WebIdentityTokenCredentialsProvider);
    }

    @Test
    public void testBuilderStsProvider() {
        AWSCredentialsProviderBuilder builder = AWSCredentialsProviderBuilder.builder()
            .setStsRole("arn:aws:iam::123456789012:role/STS-Test-Role")
            .setStsSessionName("STS-Session")
            .setStsRegion("us-east-1")
            .setStsSessionDuration(1200)
            .setStsExternalId("external-id-123");
        AWSCredentialsProvider provider = builder.build("sts").get();
        assertNotNull("STS provider from builder should not be null", provider);
        assertTrue(provider instanceof STSAssumeRoleSessionCredentialsProvider);
    }

    @Test
    public void testBasicProviderWithValidKeys() {
        String accessKey = "AKI1234567890EXAMPLE";
        String secretKey = "abc1234567890secretEXAMPLE";
        AWSCredentialsProvider provider = AWSCredentialsProviderBuilder.basic(
            accessKey,
            secretKey
        );

        assertNotNull("Provider should not be null", provider);
        AWSCredentials credentials = provider.getCredentials();
        assertTrue(credentials instanceof BasicAWSCredentials);
        assertEquals(accessKey, credentials.getAWSAccessKeyId());
        assertEquals(secretKey, credentials.getAWSSecretKey());
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
        AWSCredentialsProvider provider = AWSCredentialsProviderBuilder.session(
            accessKey,
            secretKey,
            sessionToken
        );

        assertNotNull("Provider should not be null", provider);
        AWSCredentials credentials = provider.getCredentials();
        assertTrue(credentials instanceof BasicSessionCredentials);
        assertEquals(accessKey, credentials.getAWSAccessKeyId());
        assertEquals(secretKey, credentials.getAWSSecretKey());
        assertEquals(sessionToken, ((BasicSessionCredentials) credentials).getSessionToken());
    }

    @Test
    public void testStaticCredentialsProvider() {
        BasicAWSCredentials credentials = new BasicAWSCredentials(
            "STATICKEY",
            "STATICSECRET"
        );
        AWSStaticCredentialsProvider provider =
            AWSCredentialsProviderBuilder.staticCredentials(credentials);

        assertNotNull("Provider should not be null", provider);
        AWSCredentials providedCreds = provider.getCredentials();
        assertEquals("STATICKEY", providedCreds.getAWSAccessKeyId());
        assertEquals("STATICSECRET", providedCreds.getAWSSecretKey());
    }

    @Test
    public void testDefaultProvider() {
        AWSCredentialsProvider provider =
            AWSCredentialsProviderBuilder.defaultProvider();
        assertNotNull("Provider should not be null", provider);
        assertTrue(provider instanceof DefaultAWSCredentialsProviderChain);
    }

    @Test
    public void testBuilderStaticProviderWithSessionToken() {
        AWSCredentialsProviderBuilder builder =
            AWSCredentialsProviderBuilder.builder()
                .setAwsAccessKey("AKISTATIC123")
                .setAwsSecretKey("STATICSECRET123")
                .setAwsSessionToken("SESSIONTOKEN123");

        AWSCredentialsProvider provider = builder.build("static").get();
        assertNotNull("Provider should not be null", provider);
        AWSCredentials credentials = provider.getCredentials();
        assertTrue(credentials instanceof BasicSessionCredentials);
        assertEquals("AKISTATIC123", credentials.getAWSAccessKeyId());
        assertEquals("STATICSECRET123", credentials.getAWSSecretKey());
        assertEquals("SESSIONTOKEN123", ((BasicSessionCredentials) credentials).getSessionToken());
    }

    @Test
    public void testBuilderStaticProviderWithoutSessionToken() {
        AWSCredentialsProviderBuilder builder =
            AWSCredentialsProviderBuilder.builder()
                .setAwsAccessKey("AKISTATIC456")
                .setAwsSecretKey("STATICSECRET456");

        AWSCredentialsProvider provider = builder.build("static").get();
        assertNotNull("Provider should not be null", provider);
        AWSCredentials credentials = provider.getCredentials();
        assertTrue(credentials instanceof BasicAWSCredentials);
        assertEquals("AKISTATIC456", credentials.getAWSAccessKeyId());
        assertEquals("STATICSECRET456", credentials.getAWSSecretKey());
    }

    @Test
    public void testBuilderDefaultProvider() {
        AWSCredentialsProviderBuilder builder = AWSCredentialsProviderBuilder.builder();
        AWSCredentialsProvider provider = builder.build("default").get();
        assertNotNull("Default provider should not be null", provider);
        assertTrue(provider instanceof DefaultAWSCredentialsProviderChain);
    }

    @Test
    public void testBuilderInvalidTypeDefaultsToDefault() {
        AWSCredentialsProviderBuilder builder = AWSCredentialsProviderBuilder.builder();
        AWSCredentialsProvider provider = builder.build("invalid").get();
        assertNotNull("Provider should not be null", provider);
        assertTrue(provider instanceof DefaultAWSCredentialsProviderChain);
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
        AWSCredentialsProvider provider =
            AWSCredentialsProviderBuilder.sts("role", "session", "us-east-1", null, 500, null); // <900
        assertNotNull("STS provider should not be null", provider);
        assertTrue(provider instanceof STSAssumeRoleSessionCredentialsProvider);
    }

    @Test
    public void testBuilderDefaultWithKeysSetUsesStatic() {
        AWSCredentialsProviderBuilder builder = AWSCredentialsProviderBuilder.builder()
            .setAwsAccessKey("KEY")
            .setAwsSecretKey("SECRET");
        AWSCredentialsProvider provider = builder.build("default").get();
        assertNotNull("Provider should not be null", provider);
        AWSCredentials credentials = provider.getCredentials();
        assertTrue(credentials instanceof BasicAWSCredentials);
        assertEquals("KEY", credentials.getAWSAccessKeyId());
        assertEquals("SECRET", credentials.getAWSSecretKey());
    }

    @Test
    public void testEnumFromStringInvalid() {
        AWSCredentialProviderType type = AWSCredentialProviderType.fromString("invalid");
        assertEquals(AWSCredentialProviderType.DEFAULT, type);
    }

    @Test
    public void testGetters() {
        AWSCredentialsProviderBuilder builder = AWSCredentialsProviderBuilder.builder()
            .setRoleArn("arn")
            .setRoleSessionName("session")
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
        assertEquals("arn", builder.getRoleArn());
        assertEquals("session", builder.getRoleSessionName());
        assertEquals("file", builder.getWebIdentityTokenFile());
        assertEquals("stsrole", builder.getStsRole());
        assertEquals("stssession", builder.getStsSessionName());
        assertEquals("region", builder.getStsRegion());
        assertEquals("endpoint", builder.getStsEndpoint());
        assertEquals(Integer.valueOf(1000), builder.getStsSessionDuration());
        assertEquals("extid", builder.getStsExternalId());
        assertEquals("key", builder.getAwsAccessKey());
        assertEquals("secret", builder.getAwsSecretKey());
        assertEquals("token", builder.getAwsSessionToken());
    }

    @Test
    public void testIrsaProviderWithNulls() {
        AWSCredentialsProvider provider = AWSCredentialsProviderBuilder.irsa(null, null, null);
        assertNotNull("IRSA provider should not be null", provider);
        assertTrue(provider instanceof WebIdentityTokenCredentialsProvider);
    }
}
