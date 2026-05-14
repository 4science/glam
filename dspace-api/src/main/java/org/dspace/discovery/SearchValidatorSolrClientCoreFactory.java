/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.dspace.services.ConfigurationService;
import org.dspace.solr.BaseValidatingSolrClientFactory;
import org.dspace.solr.SolrClientFactory;
import org.dspace.solr.SolrClientValidator;
import org.dspace.storage.rdbms.DatabaseUtils;

/**
 * Factory for validating and initializing Discovery Solr clients.
 * Uses the common validation framework with discovery-specific post-validation setup.
 */
public class SearchValidatorSolrClientCoreFactory extends BaseValidatingSolrClientFactory {

    private static final Logger log = LogManager.getLogger(SearchValidatorSolrClientCoreFactory.class);

    private final IndexingService indexingService;

    public SearchValidatorSolrClientCoreFactory(SolrClientFactory baseFactory,
                                                ConfigurationService configurationService,
                                                IndexingService indexingService,
                                                SolrClientValidator validator) {
        super(baseFactory, configurationService, validator, "Discovery");
        this.indexingService = indexingService;
    }

    @Override
    protected void performPostValidationSetup(SolrClient client, String solrServiceUrl) {
        try {
            log.debug("Solr discovery URL: {}", solrServiceUrl);
            if (client instanceof HttpSolrClient) {
                ((HttpSolrClient) client).setUseMultiPartPost(true);
            } else {
                log.warn("Discovery Solr client is not an HttpSolrClient, skipping HTTP method configuration");
            }

            DatabaseUtils.checkReindexDiscovery(indexingService);

        } catch (Exception e) {
            log.warn("Error during discovery post-validation setup: {}", e.getMessage());
        }
    }
}

