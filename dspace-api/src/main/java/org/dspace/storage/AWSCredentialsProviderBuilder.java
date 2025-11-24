/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage;

import static org.dspace.storage.AWSCredentialsProviderBuilder.AWSCredentialProviderType.IRSA;
import static org.dspace.storage.AWSCredentialsProviderBuilder.AWSCredentialProviderType.STATIC;
import static org.dspace.storage.AWSCredentialsProviderBuilder.AWSCredentialProviderType.STS;

import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;

/**
 * Builder for different AWS Credentials Providers, based on the specified authentication type and parameters.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class AWSCredentialsProviderBuilder {

    public static final String REGEX_SECRET = "^(.{3})(.*)(.{3})$";

    private static final Logger log = LogManager.getLogger(AWSCredentialsProviderBuilder.class);

    @Value("${assetstore.s3.irsa.role}")
    protected String roleArn;
    @Value("${assetstore.s3.irsa.session}")
    protected String roleSessionName;
    @Value("${assetstore.s3.irsa.tokenfile}")
    protected String webIdentityTokenFile;

    @Value("${assetstore.s3.stsRoleArn}")
    protected String stsRole;
    @Value("${assetstore.s3.stsSessionName}")
    protected String stsSessionName;
    @Value("${assetstore.s3.stsRegion}")
    protected String stsRegion;
    @Value("${assetstore.s3.stsEndpoint}")
    protected String stsEndpoint;
    @Value("${assetstore.s3.stsSessionDuration}")
    protected Integer stsSessionDuration;
    @Value("${assetstore.s3.stsExternalid}")
    protected String stsExternalId;

    protected String awsAccessKey;
    protected String awsSecretKey;
    protected String awsSessionToken;

    public static AWSCredentialsProviderBuilder builder() {
        return new AWSCredentialsProviderBuilder();
    }

    public static AWSCredentialsProvider defaultProvider() {
        log.info("Using default AWS credentials provider chain (EC2 IAM role, environment variables, etc.)");
        return new DefaultAWSCredentialsProviderChain();
    }

    public static AWSCredentialsProvider irsa(
        @Nullable String roleArn,
        @Nullable String roleSessionName,
        @Nullable String webIdentityTokenFile
    ) {
        return WebIdentityTokenCredentialsProvider
            .builder()
            .roleArn(roleArn)
            .roleSessionName(roleSessionName)
            .webIdentityTokenFile(webIdentityTokenFile)
            .build();
    }

    public static AWSCredentialsProvider sts(
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

        stsSessionName =
            Optional.ofNullable(stsSessionName).filter(StringUtils::isNotBlank).orElse("DSpace-S3-Session");

        // Build STS client
        AWSSecurityTokenServiceClientBuilder stsClientBuilder =
            AWSSecurityTokenServiceClientBuilder.standard();

        // Configure STS region if specified
        if (StringUtils.isNotBlank(stsRegion)) {
            stsClientBuilder.withRegion(stsRegion);
        }

        // Configure custom STS endpoint if specified
        if (StringUtils.isNotBlank(stsEndpoint)) {
            stsClientBuilder.withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(stsEndpoint, stsRegion)
            );
        }

        AWSSecurityTokenService stsClient = stsClientBuilder.build();

        // Build STS credentials provider
        STSAssumeRoleSessionCredentialsProvider.Builder credentialsBuilder =
            new STSAssumeRoleSessionCredentialsProvider.Builder(
                stsRole,
                stsSessionName
            ).withStsClient(stsClient);

        // Set session duration if specified (between 900 and 43200 seconds)
        if (stsSessionDuration != null) {
            int duration = stsSessionDuration;
            if (duration < 900 || duration > 43200) {
                log.warn(
                    "STS session duration {} is outside valid range (900-43200 seconds), using default",
                    duration
                );
            } else {
                credentialsBuilder.withRoleSessionDurationSeconds(duration);
            }
        }

        // Set external ID if specified (for third-party access)
        if (StringUtils.isNotBlank(stsExternalId)) {
            credentialsBuilder.withExternalId(stsExternalId);
        }

        log.info(
            "STS credentials provider configured - Role ARN: {}, Session Name: {}, Duration: {}, External ID: {}",
            stsRole,
            stsSessionName,
            stsSessionDuration != null
                ? stsSessionDuration + " seconds"
                : "default",
            StringUtils.isNotBlank(stsExternalId) ? "***" : "not set"
        );

        return credentialsBuilder.build();
    }

    public static AWSCredentialsProvider session(
        @Nonnull String awsAccessKey,
        @Nonnull String awsSecretKey,
        @Nonnull String awsSessionToken
    ) {
        BasicSessionCredentials credentials =
            new BasicSessionCredentials(
                awsAccessKey,
                awsSecretKey,
                awsSessionToken
            );
        log.info(
            "AmazonS3Client credentials - accessKey: {}, secretKey: {}, awsSessionToken: {}",
            credentials
                .getAWSAccessKeyId()
                .replaceFirst(REGEX_SECRET, "$1***$3"),
            credentials.getAWSSecretKey().replaceFirst(REGEX_SECRET, "$1***$3"),
            credentials.getSessionToken().replaceFirst(REGEX_SECRET, "$1***$3")
        );
        return new AWSStaticCredentialsProvider(credentials);
    }

    public static AWSCredentialsProvider basic(
        @Nonnull String awsAccessKey,
        @Nonnull String awsSecretKey
    ) {
        if (StringUtils.isBlank(awsAccessKey) || StringUtils.isBlank(awsSecretKey)) {
            throw new IllegalArgumentException(
                "AWS Access Key and Secret Key are required when using basic authentication type"
            );
        }
        BasicAWSCredentials credentials = new BasicAWSCredentials(
            awsAccessKey,
            awsSecretKey);
        log.info(
            "AmazonS3Client credentials - accessKey: {}, secretKey: {}",
            credentials
                .getAWSAccessKeyId()
                .replaceFirst(REGEX_SECRET, "$1***$3"),
            credentials.getAWSSecretKey().replaceFirst(REGEX_SECRET, "$1***$3")
        );
        return new AWSStaticCredentialsProvider(credentials);
    }

    public static AWSStaticCredentialsProvider staticCredentials(AWSCredentials credentials) {
        return new AWSStaticCredentialsProvider(credentials);
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

    public String getRoleArn() {
        return roleArn;
    }

    public String getRoleSessionName() {
        return roleSessionName;
    }

    public String getWebIdentityTokenFile() {
        return webIdentityTokenFile;
    }

    public String getStsRole() {
        return stsRole;
    }

    public String getStsSessionName() {
        return stsSessionName;
    }

    public String getStsRegion() {
        return stsRegion;
    }

    public String getStsEndpoint() {
        return stsEndpoint;
    }

    public Integer getStsSessionDuration() {
        return stsSessionDuration;
    }

    public String getStsExternalId() {
        return stsExternalId;
    }

    public String getAwsAccessKey() {
        return awsAccessKey;
    }

    public String getAwsSecretKey() {
        return awsSecretKey;
    }

    public String getAwsSessionToken() {
        return awsSessionToken;
    }

    public Supplier<AWSCredentialsProvider> build(String type) {
        AWSCredentialProviderType providerType =
            AWSCredentialProviderType.fromString(type);
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
            log.warn("Using static S3 credentials with access and secret keys. This is not recommended for production" +
                         "due to security risks. Consider using IAM roles or other secure authentication methods.");
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
