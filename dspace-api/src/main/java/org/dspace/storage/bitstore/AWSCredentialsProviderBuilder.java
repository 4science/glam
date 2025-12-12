/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import static org.dspace.storage.bitstore.AWSCredentialsProviderBuilder.AWSCredentialProviderType.IRSA;
import static org.dspace.storage.bitstore.AWSCredentialsProviderBuilder.AWSCredentialProviderType.STATIC;
import static org.dspace.storage.bitstore.AWSCredentialsProviderBuilder.AWSCredentialProviderType.STS;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

/**
 * Builder for different AWS Credentials Providers, based on the specified
 * authentication type and parameters.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class AWSCredentialsProviderBuilder {

    public static final String REGEX_SECRET = "^(.{3})(.*)(.{3})$";

    private static final Logger log = LogManager.getLogger(AWSCredentialsProviderBuilder.class);

    protected String roleArn;
    protected String roleSessionName;
    protected String webIdentityTokenFile;

    protected String stsRole;
    protected String stsSessionName;
    protected String stsRegion;
    protected String stsEndpoint;
    protected Integer stsSessionDuration;
    protected String stsExternalId;

    protected String awsAccessKey;
    protected String awsSecretKey;
    protected String awsSessionToken;

    public static AWSCredentialsProviderBuilder builder() {
        return new AWSCredentialsProviderBuilder();
    }

    public static AwsCredentialsProvider defaultProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    public static AwsCredentialsProvider irsa(
        @Nullable String roleArn,
        @Nullable String roleSessionName,
        @Nullable String webIdentityTokenFile) {
        return WebIdentityTokenFileCredentialsProvider.builder()
                                                      .roleArn(roleArn)
                                                      .roleSessionName(roleSessionName)
                                                      .webIdentityTokenFile(webIdentityTokenFile != null
                                                                                ? Path.of(webIdentityTokenFile) : null)
                                                      .build();
    }

    public static AwsCredentialsProvider sts(
        @Nonnull String stsRole,
        @Nullable String stsSessionName,
        @Nullable String stsRegion,
        @Nullable String stsEndpoint,
        @Nullable Integer stsSessionDuration,
        @Nullable String stsExternalId
    ) {

        if (StringUtils.isBlank(stsRole)) {
            throw new IllegalArgumentException("STS Role ARN is required when using STS authentication type");
        }

        stsSessionName = Optional.ofNullable(stsSessionName)
                                 .filter(StringUtils::isNotBlank)
                                 .orElse("DSpace-S3-Session");

        // Build STS client
        StsClientBuilder stsClientBuilder = StsClient.builder();

        // Configure STS region if specified
        if (StringUtils.isNotBlank(stsRegion)) {
            stsClientBuilder.region(Region.of(stsRegion));
        }

        // Configure custom STS endpoint if specified
        if (StringUtils.isNotBlank(stsEndpoint)) {
            try {
                stsClientBuilder.endpointOverride(java.net.URI.create(stsEndpoint));
            } catch (Exception e) {
                log.warn("Failed to set custom STS endpoint: {}, using default", stsEndpoint, e);
            }
        }

        // Build assume role request
        AssumeRoleRequest.Builder assumeRoleBuilder = AssumeRoleRequest.builder()
                                                                       .roleArn(stsRole)
                                                                       .roleSessionName(stsSessionName);

        // Set session duration if specified (between 900 and 43200 seconds)
        if (stsSessionDuration != null && stsSessionDuration >= 900 && stsSessionDuration <= 43200) {
            assumeRoleBuilder.durationSeconds(stsSessionDuration);
        }

        // Set external ID if specified (for third-party access)
        if (StringUtils.isNotBlank(stsExternalId)) {
            assumeRoleBuilder.externalId(stsExternalId);
        }

        // Log session duration warning if out of range
        if (stsSessionDuration != null && (stsSessionDuration < 900 || stsSessionDuration > 43200)) {
            log.warn(
                "STS session duration {} is outside valid range (900-43200 seconds), using default",
                stsSessionDuration);
        }

        StsClient client = stsClientBuilder.build();
        AssumeRoleRequest request = assumeRoleBuilder.build();

        log.info(
            "STS credentials provider configured - Role ARN: {}, Session Name: {}, Duration: {}, External ID: {}",
            stsRole,
            stsSessionName,
            stsSessionDuration != null
                ? stsSessionDuration + " seconds"
                : "default",
            StringUtils.isNotBlank(stsExternalId) ? "***" : "not set");
        return StsAssumeRoleCredentialsProvider.builder()
                                               .stsClient(client)
                                               .refreshRequest(request)
                                               .build();
    }

    public static AwsCredentialsProvider session(
        @Nonnull String awsAccessKey,
        @Nonnull String awsSecretKey,
        @Nonnull String awsSessionToken) {
        AwsSessionCredentials credentials = AwsSessionCredentials.create(
                awsAccessKey,
                awsSecretKey,
                awsSessionToken);
        log.info(
            "AmazonS3Client credentials - accessKey: {}, secretKey: {}, awsSessionToken: {}",
            credentials
                .accessKeyId()
                .replaceFirst(REGEX_SECRET, "$1***$3"),
            credentials.secretAccessKey().replaceFirst(REGEX_SECRET, "$1***$3"),
            credentials.sessionToken().replaceFirst(REGEX_SECRET, "$1***$3"));
        return StaticCredentialsProvider.create(credentials);
    }

    public static AwsCredentialsProvider basic(
        @Nonnull String awsAccessKey,
        @Nonnull String awsSecretKey
    ) {
        if (StringUtils.isBlank(awsAccessKey) || StringUtils.isBlank(awsSecretKey)) {
            throw new IllegalArgumentException(
                "AWS Access Key and Secret Key are required when using basic authentication type");
        }
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
            awsAccessKey,
            awsSecretKey);
        log.info(
            "AmazonS3Client credentials - accessKey: {}, secretKey: {}",
            credentials
                .accessKeyId()
                .replaceFirst(REGEX_SECRET, "$1***$3"),
            credentials.secretAccessKey().replaceFirst(REGEX_SECRET, "$1***$3"));
        return StaticCredentialsProvider.create(credentials);
    }

    public static StaticCredentialsProvider staticCredentials(AwsCredentials credentials) {
        return StaticCredentialsProvider.create(credentials);
    }

    public AWSCredentialsProviderBuilder setRoleArn(String roleArn) {
        this.roleArn = roleArn;
        return this;
    }

    public AWSCredentialsProviderBuilder setRoleSessionName(String roleSessionName) {
        this.roleSessionName = roleSessionName;
        return this;
    }

    public AWSCredentialsProviderBuilder setWebIdentityTokenFile(String webIdentityTokenFile) {
        this.webIdentityTokenFile = webIdentityTokenFile;
        return this;
    }

    public AWSCredentialsProviderBuilder setStsRole(String stsRole) {
        this.stsRole = stsRole;
        return this;
    }

    public AWSCredentialsProviderBuilder setStsSessionName(String stsSessionName) {
        this.stsSessionName = stsSessionName;
        return this;
    }

    public AWSCredentialsProviderBuilder setStsRegion(String stsRegion) {
        this.stsRegion = stsRegion;
        return this;
    }

    public AWSCredentialsProviderBuilder setStsEndpoint(String stsEndpoint) {
        this.stsEndpoint = stsEndpoint;
        return this;
    }

    public AWSCredentialsProviderBuilder setStsSessionDuration(Integer stsSessionDuration) {
        this.stsSessionDuration = stsSessionDuration;
        return this;
    }

    public AWSCredentialsProviderBuilder setStsExternalId(String stsExternalId) {
        this.stsExternalId = stsExternalId;
        return this;
    }

    public AWSCredentialsProviderBuilder setAwsAccessKey(String awsAccessKey) {
        this.awsAccessKey = awsAccessKey;
        return this;
    }

    public AWSCredentialsProviderBuilder setAwsSecretKey(String awsSecretKey) {
        this.awsSecretKey = awsSecretKey;
        return this;
    }

    public AWSCredentialsProviderBuilder setAwsSessionToken(String awsSessionToken) {
        this.awsSessionToken = awsSessionToken;
        return this;
    }

    public Supplier<AwsCredentialsProvider> build(String type) {
        AWSCredentialProviderType providerType = AWSCredentialProviderType.fromString(type);
        if (IRSA.equals(providerType)) {
            log.info("Using IRSA (IAM Roles for Service Accounts) credentials for S3 authentication.");
            return () -> irsa(roleArn, roleSessionName, webIdentityTokenFile);
        } else if (STS.equals(providerType)) {
            log.info("Using STS (Security Token Service) credentials for S3 authentication with role assumption.");
            return () -> sts(stsRole, stsSessionName, stsRegion, stsEndpoint, stsSessionDuration, stsExternalId);
        } else if (isStaticProvider(providerType)) {
            if (StringUtils.isNotBlank(awsSessionToken)) {
                log.warn("Using static S3 credentials with session token (not recommended for production)");
                return () -> session(awsAccessKey, awsSecretKey, awsSessionToken);
            }
            log.warn("Using static S3 credentials with access and secret keys. " +
                         "This is not recommended for production due to security risks. " +
                         "Consider using IAM roles or other secure authentication methods.");
            return () -> basic(awsAccessKey, awsSecretKey);
        } else {
            // Default to the standard AWS credentials provider chain
            log.info("Using default AWS credentials provider chain (EC2 IAM role, environment variables, etc.)");
            return AWSCredentialsProviderBuilder::defaultProvider;
        }
    }

    private boolean isStaticProvider(AWSCredentialProviderType providerType) {
        return STATIC.equals(providerType) ||
            (StringUtils.isNotBlank(awsAccessKey) && StringUtils.isNotBlank(awsSecretKey));
    }

    enum AWSCredentialProviderType {
        DEFAULT("default"),
        IRSA("irsa"),
        STS("sts"),
        STATIC("static");

        final String type;

        AWSCredentialProviderType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        boolean is(String type) {
            return this.type.equalsIgnoreCase(type);
        }

        static AWSCredentialProviderType fromString(String type) {
            for (AWSCredentialProviderType t : values()) {
                if (t.is(type)) {
                    return t;
                }
            }
            return DEFAULT;
        }

    }
}
