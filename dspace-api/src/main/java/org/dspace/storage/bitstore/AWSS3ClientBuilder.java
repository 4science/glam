/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import java.net.URI;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3CrtAsyncClientBuilder;

/**
 * This is a builder to create configured AWS S3 Async Clients.
 * It can be used to create multiple clients with different configurations.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class AWSS3ClientBuilder {

    private static final Logger log = LogManager.getLogger(AWSS3ClientBuilder.class);
    protected String endpoint;
    protected Region region;
    protected Integer maxConcurrency;
    protected Double targetThroughput;
    protected Long minPartSize;
    protected Supplier<AwsCredentialsProvider> credentialsProvider;

    private AWSS3ClientBuilder() {}

    public static AWSS3ClientBuilder builder() {
        return new AWSS3ClientBuilder();
    }

    public AWSS3ClientBuilder setEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public AWSS3ClientBuilder setRegion(Region region) {
        this.region = region;
        return this;
    }

    public AWSS3ClientBuilder setMaxConcurrency(Integer maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
        return this;
    }

    public AWSS3ClientBuilder setTargetThroughput(Double targetThroughput) {
        this.targetThroughput = targetThroughput;
        return this;
    }

    public AWSS3ClientBuilder setMinPartSize(Long minPartSize) {
        this.minPartSize = minPartSize;
        return this;
    }

    public AWSS3ClientBuilder setCredentialsProvider(
        Supplier<AwsCredentialsProvider> credentialsProvider
    ) {
        this.credentialsProvider = credentialsProvider;
        return this;
    }

    public S3AsyncClient asyncClient() {
        S3CrtAsyncClientBuilder crtBuilder = S3AsyncClient.crtBuilder();
        if (credentialsProvider != null) {
            crtBuilder.credentialsProvider(credentialsProvider.get());
        }

        if (region != null) {
            crtBuilder.region(region);
        } else {
            try {
                crtBuilder.region(DefaultAwsRegionProviderChain.builder().build().getRegion());
            } catch (SdkClientException e) {
                // No region configured, using the default region!
                log.error("No AWS region configured, using the default region us-east-1", e);
                crtBuilder.region(Region.US_EAST_1);
            }
        }

        if (maxConcurrency != null) {
            crtBuilder.maxConcurrency(maxConcurrency);
        }

        if (StringUtils.isNotBlank(endpoint)) {
            crtBuilder.endpointOverride(URI.create(endpoint));
            crtBuilder.forcePathStyle(true);
        }

        return crtBuilder.targetThroughputInGbps(targetThroughput)
                         .minimumPartSizeInBytes(minPartSize)
                         .build();
    }
}
