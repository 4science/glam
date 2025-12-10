/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore.factory;

import java.text.MessageFormat;
import java.util.function.Supplier;

import org.apache.commons.lang.StringUtils;
import org.dspace.services.ConfigurationService;
import org.dspace.storage.bitstore.AWSCredentialsProviderBuilder;
import org.dspace.storage.bitstore.AWSS3ClientBuilder;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * This is a factory to create AWS S3 related configurations.
 * It can be used to create clients and credentials providers with different configurations.
 * The configurations are read from the DSpace ConfigurationService using the "assetstore.s3" prefix.
 *
 * <pre>
 *     * Example configuration properties:
 *     assetstore.s3.awsAccessKey = ACCESS_KEY
 *     assetstore.s3.awsSecretKey = SECRET_KEY
 * </pre>
 *
 * You can configure multiple S3 stores by adding a suffix to the store properties.
 * For example, for a store with suffix "store1", the properties would be:
 * <pre>
 *     assetstore.s3.store1.awsAccessKey = ACCESS_KEY_STORE1
 *     assetstore.s3.store1.awsSecretKey = SECRET_KEY_STORE1
 * </pre>
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class AWSFactory {

    protected final ConfigurationService configurationService;

    public AWSFactory(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public Supplier<AwsCredentialsProvider> provideCredentials() {
        return AWSCredentialsProviderBuilder
            .builder()
            .setStsRole(configurationService.getProperty(getStoreProperty("stsRole")))
            .setStsSessionName(configurationService.getProperty(getStoreProperty("stsSessionName")))
            .setStsRegion(configurationService.getProperty(getStoreProperty("stsRegion")))
            .setStsExternalId(configurationService.getProperty(getStoreProperty("stsExternalId")))
            .setStsSessionDuration(configurationService.getIntProperty(getStoreProperty("stsSessionDuration")))
            .setStsEndpoint(configurationService.getProperty(getStoreProperty("stsEndpoint")))
            .setRoleArn(configurationService.getProperty(getStoreProperty("irsa.role")))
            .setRoleSessionName(configurationService.getProperty(getStoreProperty("irsa.session")))
            .setWebIdentityTokenFile(configurationService.getProperty(getStoreProperty("irsa.tokenfile")))
            .setAwsAccessKey(configurationService.getProperty(getStoreProperty("awsAccessKey")))
            .setAwsSecretKey(configurationService.getProperty(getStoreProperty("awsSecretKey")))
            .setAwsSessionToken(configurationService.getProperty(getStoreProperty("awsSessionToken")))
            .build(configurationService.getProperty(getStoreProperty("awsAuthenticationType")));
    }

    public Supplier<AwsCredentialsProvider> provideCredentials(String storeSuffix) {
        return AWSCredentialsProviderBuilder
            .builder()
            .setStsRole(configurationService.getProperty(getStoreProperty(storeSuffix, "stsRole")))
            .setStsSessionName(configurationService.getProperty(getStoreProperty(storeSuffix, "stsSessionName")))
            .setStsRegion(configurationService.getProperty(getStoreProperty(storeSuffix, "stsRegion")))
            .setStsExternalId(configurationService.getProperty(getStoreProperty(storeSuffix, "stsExternalId")))
            .setStsSessionDuration(
                configurationService.getIntProperty(getStoreProperty(storeSuffix, "stsSessionDuration")))
            .setStsEndpoint(configurationService.getProperty(getStoreProperty(storeSuffix, "stsEndpoint")))
            .setRoleArn(configurationService.getProperty(getStoreProperty(storeSuffix, "irsa.role")))
            .setRoleSessionName(configurationService.getProperty(getStoreProperty(storeSuffix, "irsa.session")))
            .setWebIdentityTokenFile(configurationService.getProperty(getStoreProperty(storeSuffix, "irsa.tokenfile")))
            .setAwsAccessKey(configurationService.getProperty(getStoreProperty(storeSuffix, "awsAccessKey")))
            .setAwsSecretKey(configurationService.getProperty(getStoreProperty(storeSuffix, "awsSecretKey")))
            .setAwsSessionToken(configurationService.getProperty(getStoreProperty(storeSuffix, "awsSessionToken")))
            .build(configurationService.getProperty(getStoreProperty(storeSuffix, "awsAuthenticationType")));
    }

    public AWSS3ClientBuilder clientBuilder() {
        return AWSS3ClientBuilder.builder()
                                 .setEndpoint(getDefaultEndpoint())
                                 .setMaxConcurrency(getDefaultMaxConcurrency())
                                 .setMinPartSize(getDefaultMinPartSize())
                                 .setRegion(getDefaultRegion())
                                 .setTargetThroughput(getDefaultThroughput())
                                 .setCredentialsProvider(provideCredentials());
    }

    public AWSS3ClientBuilder clientBuilder(String storeSuffix) {
        return AWSS3ClientBuilder.builder()
                                 .setEndpoint(
                                     configurationService.getPropertyAsType(
                                         getStoreProperty(storeSuffix, "endpoint"),
                                         getDefaultEndpoint()
                                     )
                                 )
                                 .setMaxConcurrency(
                                     configurationService.getPropertyAsType(
                                         getStoreProperty(storeSuffix, "maxConcurrency"),
                                         getDefaultMaxConcurrency()
                                     )
                                 )
                                 .setMinPartSize(
                                     configurationService.getPropertyAsType(
                                         getStoreProperty(storeSuffix, "minPartSize"),
                                         getDefaultMinPartSize()
                                     )
                                 )
                                 .setRegion(
                                     configurationService.getPropertyAsType(
                                         getStoreProperty(storeSuffix, "awsRegionName"),
                                         getDefaultRegion()
                                     )
                                 )
                                 .setTargetThroughput(
                                     configurationService.getPropertyAsType(
                                         getStoreProperty(storeSuffix, "targetThroughput"),
                                         10.0
                                     )
                                 )
                                 .setCredentialsProvider(
                                     provideCredentials(storeSuffix)
                                 );
    }

    private Double getDefaultThroughput() {
        return configurationService.getPropertyAsType(
            getStoreProperty("targetThroughput"), 10.0);
    }

    private Region getDefaultRegion() {
        return Region.of(
            configurationService.getProperty(
                getStoreProperty("awsRegionName"),
                "us-east-1"
            )
        );
    }

    private long getDefaultMinPartSize() {
        return configurationService.getLongProperty(
            getStoreProperty("minPartSize"),
            8L * 1024L * 1024L
        );
    }

    private int getDefaultMaxConcurrency() {
        return configurationService.getIntProperty(
            getStoreProperty("maxConcurrency")
        );
    }

    private String getDefaultEndpoint() {
        return configurationService.getProperty(
            getStoreProperty("endpoint"),
            null
        );
    }

    private String getStoreProperty(String storeSuffix, String propertyName) {
        String storePrefix = getStorePrefix(storeSuffix);
        return storePrefix + propertyName;
    }

    private String getStoreProperty(String propertyName) {
        String storePrefix = getStorePrefix(null);
        return storePrefix + propertyName;
    }

    private String getStorePrefix(String storeSuffix) {
        if (StringUtils.isNotEmpty(storeSuffix)) {
            return MessageFormat.format("assetstore.s3.{0}.", storeSuffix);
        }
        return "assetstore.s3.";
    }

}
