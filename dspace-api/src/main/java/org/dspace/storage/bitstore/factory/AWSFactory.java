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
            .setStsRole(configurationService.getProperty(getAssetstoreProperty("sts.role")))
            .setStsSessionName(configurationService.getProperty(getAssetstoreProperty("sts.sessionname")))
            .setStsRegion(configurationService.getProperty(getAssetstoreProperty("sts.region")))
            .setStsExternalId(configurationService.getProperty(getAssetstoreProperty("sts.externalid")))
            .setStsSessionDuration(getPropertyAsType("sts.sessionduration", null))
            .setStsEndpoint(configurationService.getProperty(getAssetstoreProperty("sts.endpoint")))
            .setIrsaRole(configurationService.getProperty(getAssetstoreProperty("irsa.role")))
            .setIrsaRoleSessionName(configurationService.getProperty(getAssetstoreProperty("irsa.session")))
            .setWebIdentityTokenFile(configurationService.getProperty(getAssetstoreProperty("irsa.tokenfile")))
            .setAwsAccessKey(configurationService.getProperty(getAssetstoreProperty("awsAccessKey")))
            .setAwsSecretKey(configurationService.getProperty(getAssetstoreProperty("awsSecretKey")))
            .setAwsSessionToken(configurationService.getProperty(getAssetstoreProperty("awsSessionToken")))
            .build(configurationService.getProperty(getAssetstoreProperty("awsAuthenticationType")));
    }

    public Supplier<AwsCredentialsProvider> provideCredentials(String storeSuffix) {
        return AWSCredentialsProviderBuilder
            .builder()
            .setStsRole(configurationService.getProperty(getAssetstoreProperty(storeSuffix, "sts.role")))
            .setStsSessionName(configurationService.getProperty(getAssetstoreProperty(storeSuffix, "sts.sessionname")))
            .setStsRegion(configurationService.getProperty(getAssetstoreProperty(storeSuffix, "sts.region")))
            .setStsExternalId(configurationService.getProperty(getAssetstoreProperty(storeSuffix, "sts.externalid")))
            .setStsSessionDuration(
                configurationService.getIntProperty(getAssetstoreProperty(storeSuffix, "sts.sessionduration")))
            .setStsEndpoint(configurationService.getProperty(getAssetstoreProperty(storeSuffix, "sts.endpoint")))
            .setIrsaRole(configurationService.getProperty(getAssetstoreProperty(storeSuffix, "irsa.role")))
            .setIrsaRoleSessionName(
                configurationService.getProperty(getAssetstoreProperty(storeSuffix, "irsa.session")))
            .setWebIdentityTokenFile(configurationService.getProperty(
                getAssetstoreProperty(storeSuffix, "irsa.tokenfile")))
            .setAwsAccessKey(configurationService.getProperty(getAssetstoreProperty(storeSuffix, "awsAccessKey")))
            .setAwsSecretKey(configurationService.getProperty(getAssetstoreProperty(storeSuffix, "awsSecretKey")))
            .setAwsSessionToken(configurationService.getProperty(getAssetstoreProperty(storeSuffix, "awsSessionToken")))
            .build(configurationService.getProperty(getAssetstoreProperty(storeSuffix, "awsAuthenticationType")));
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
                                         getAssetstoreProperty(storeSuffix, "endpoint"),
                                         getDefaultEndpoint()
                                     )
                                 )
                                 .setMaxConcurrency(
                                     configurationService.getPropertyAsType(
                                         getAssetstoreProperty(storeSuffix, "maxConcurrency"),
                                         getDefaultMaxConcurrency()
                                     )
                                 )
                                 .setMinPartSize(
                                     configurationService.getPropertyAsType(
                                         getAssetstoreProperty(storeSuffix, "minPartSize"),
                                         getDefaultMinPartSize()
                                     )
                                 )
                                 .setRegion(
                                     configurationService.getPropertyAsType(
                                         getAssetstoreProperty(storeSuffix, "awsRegionName"),
                                         getDefaultRegion()
                                     )
                                 )
                                 .setTargetThroughput(
                                     configurationService.getPropertyAsType(
                                         getAssetstoreProperty(storeSuffix, "targetThroughput"),
                                         10.0
                                     )
                                 )
                                 .setCredentialsProvider(
                                     provideCredentials(storeSuffix)
                                 );
    }

    private <T> T getPropertyAsType(String propertyName, T value) {

        String property = configurationService.getProperty(propertyName);

        if (StringUtils.isBlank(property)) {
            return value;
        }

        return configurationService.getPropertyAsType(propertyName, value);

    }

    private Double getDefaultThroughput() {
        return configurationService.getPropertyAsType(
            getAssetstoreProperty("targetThroughput"), 10.0);
    }

    private Region getDefaultRegion() {
        String awsRegionName =
            configurationService.getProperty(
                getAssetstoreProperty("awsRegionName")
            );

        if (StringUtils.isEmpty(awsRegionName)) {
            return null;
        }

        return Region.of(awsRegionName);
    }

    private Long getDefaultMinPartSize() {
        String minPartSize = configurationService.getProperty(getAssetstoreProperty("minPartSize"));
        long defaultMinPart = 8L * 1024L * 1024L;
        if (StringUtils.isBlank(minPartSize)) {
            return defaultMinPart;
        }
        long l = defaultMinPart;
        try {
            l = Long.parseLong(minPartSize);
        } catch (Exception e) {
            // ignore
        }
        return l;
    }

    private Integer getDefaultMaxConcurrency() {

        String maxConcurrency = configurationService.getProperty(getAssetstoreProperty("maxConcurrency"));
        int defaultConcurrency = 10;
        if (StringUtils.isBlank(maxConcurrency)) {
            return defaultConcurrency;
        }

        int i = defaultConcurrency;
        try {
            i = Integer.parseInt(maxConcurrency);
        } catch (Exception e) {
            // ignore
        }
        return i;
    }

    private String getDefaultEndpoint() {
        return configurationService.getProperty(
            getAssetstoreProperty("endpoint"),
            null
        );
    }

    private String getAssetstoreProperty(String storeSuffix, String propertyName) {
        String storePrefix = getStorePrefix(storeSuffix);
        return storePrefix + propertyName;
    }

    private String getAssetstoreProperty(String propertyName) {
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
