/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.solr;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.dspace.services.ConfigurationService;

/**
 * Base class for Solr client factories that require validation.
 * Provides common caching and validation logic that can be reused across different Solr services.
 * Can be used directly for simple validation cases or extended for service-specific behavior.
 */
public class BaseValidatingSolrClientFactory implements SolrClientFactory {

    private static final Logger log = LogManager.getLogger(BaseValidatingSolrClientFactory.class);

    protected final SolrClientFactory baseFactory;
    protected final ConfigurationService configurationService;
    protected final SolrClientValidator validator;
    protected final String serviceName;

    // Cache the delegate client and its validation status
    private SolrClient cachedDelegate;
    private SolrClient validatedClient;
    private String lastCoreProperty;
    private boolean lastValidationResult = false;

    public BaseValidatingSolrClientFactory(SolrClientFactory baseFactory,
                                           ConfigurationService configurationService,
                                           SolrClientValidator validator,
                                           String serviceName) {
        this.baseFactory = baseFactory;
        this.configurationService = configurationService;
        this.validator = validator;
        this.serviceName = serviceName;
    }

    @Override
    public Optional<SolrClient> getClient(String coreProperty) {
        SolrClient currentDelegate = baseFactory.getClient(coreProperty)
                                                .orElse(null);

        boolean needsValidation = shouldRevalidate(currentDelegate, coreProperty);

        if (!needsValidation && validatedClient != null) {
            log.debug("Returning cached validated {} Solr client for property: {}", serviceName, coreProperty);
            return Optional.of(validatedClient);
        }

        cachedDelegate = currentDelegate;
        lastCoreProperty = coreProperty;

        String solrServiceUrl = configurationService.getProperty(coreProperty);

        return getValidatedSolrClient(solrServiceUrl, currentDelegate);
    }

    private Optional<SolrClient> getValidatedSolrClient(String solrServiceUrl, SolrClient currentDelegate) {
        if (validator.isValidationEnabled()) {
            log.debug("Validating {} Solr client for URL: {}", serviceName, solrServiceUrl);
            lastValidationResult = validator.validateClient(currentDelegate, solrServiceUrl, serviceName);

            if (lastValidationResult) {
                validatedClient = currentDelegate;
                performPostValidationSetup(validatedClient, solrServiceUrl);
                return Optional.of(validatedClient);
            } else {
                log.error("{} Solr client validation failed for URL: {}", serviceName, solrServiceUrl);
                validatedClient = null;
                return Optional.empty();
            }
        } else {
            log.debug("Validation disabled for {}, returning client directly for URL: {}", serviceName,
                      solrServiceUrl);
            validatedClient = currentDelegate;
            lastValidationResult = true;
            performPostValidationSetup(validatedClient, solrServiceUrl);
            return Optional.ofNullable(validatedClient);
        }
    }

    @Override
    public Optional<SolrClient> getDynamicClient(String name) {
        SolrClient currentDelegate = baseFactory.getDynamicClient(name)
                                                .orElse(null);
        String solrServerUrl = configurationService.getProperty("solr.server");
        String solrMultiCorePrefix = configurationService.getProperty("solr.multicorePrefix");
        if (solrServerUrl == null || solrMultiCorePrefix == null || name == null) {
            log.warn(
                "Solr dynamic client configuration is incomplete: solr.server='{}', solr.multicorePrefix='{}', " +
                    "name='{}'",
                solrServerUrl, solrMultiCorePrefix, name);
            return Optional.empty();
        }
        String solrServiceUrl = solrServerUrl + "/" + solrMultiCorePrefix + name;

        return getValidatedSolrClient(solrServiceUrl, currentDelegate);
    }

    /**
     * Determines if we need to revalidate based on delegate changes.
     */
    private boolean shouldRevalidate(SolrClient currentDelegate, String coreProperty) {
        // First time call
        if (cachedDelegate == null || validatedClient == null) {
            return true;
        }

        // Core property changed
        if (!coreProperty.equals(lastCoreProperty)) {
            return true;
        }

        // Delegate instance changed
        if (cachedDelegate != currentDelegate) {
            log.debug("{} Solr delegate changed from {} to {}, revalidation needed",
                      serviceName,
                      cachedDelegate.getClass().getSimpleName(),
                      currentDelegate.getClass().getSimpleName());
            return true;
        }

        // Previous validation failed
        return !lastValidationResult;
    }

    /**
     * Hook method for subclasses to perform any service-specific setup after validation.
     * Override this method if additional configuration is needed after validation succeeds.
     * Default implementation does nothing, making this class usable directly for simple cases.
     *
     * @param client       the validated SolrClient
     * @param coreProperty the core property used
     */
    protected void performPostValidationSetup(SolrClient client, String coreProperty) {
        // Default implementation does nothing - suitable for simple validation cases
    }
}
