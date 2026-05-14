/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.solr;

import org.apache.commons.validator.routines.UrlValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.dspace.services.ConfigurationService;

/**
 * Common Solr client validation component that can be reused across different Solr factories.
 * Provides URL validation and connectivity testing with configurable validation queries.
 */
public class SolrClientValidator {

    private static final Logger log = LogManager.getLogger(SolrClientValidator.class);

    private final ConfigurationService configurationService;
    private final SolrValidationQuery validationQuery;
    private final String validationEnabledProperty;

    public SolrClientValidator(ConfigurationService configurationService,
                               SolrValidationQuery validationQuery,
                               String validationEnabledProperty) {
        this.configurationService = configurationService;
        this.validationQuery = validationQuery;
        this.validationEnabledProperty = validationEnabledProperty;
    }

    /**
     * Validates a Solr client by checking URL validity and performing a test query.
     *
     * @param client       the SolrClient to validate
     * @param solrServiceUrl the Solr core URL
     * @param serviceName  the name of the service (for logging purposes)
     * @return true if validation succeeds, false otherwise
     */
    public boolean validateClient(SolrClient client, String solrServiceUrl, String serviceName) {
        try {
            // URL validation for HttpSolrClient instances
            if (client instanceof HttpSolrClient) {
                UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);

                if (urlValidator.isValid(solrServiceUrl) ||
                    configurationService.getBooleanProperty(validationEnabledProperty, true)) {
                    log.debug("{} Solr URL validation passed: {}", serviceName, solrServiceUrl);
                } else {
                    log.error("Error while initializing {}, invalid url: {}", serviceName, solrServiceUrl);
                    return false;
                }
            }

            // Perform test query to verify connectivity
            SolrQuery testQuery = validationQuery.toSolrQuery();
            QueryResponse response = client.query(testQuery);

            log.debug("{} Solr validation query successful (QTime={}ms)", serviceName, response.getQTime());
            log.info("{} Solr client validation successful for property: {}", serviceName, solrServiceUrl);

            return true;
        } catch (Exception e) {
            log.warn("{} Solr client validation failed: {}", serviceName, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if validation is enabled for this validator.
     */
    public boolean isValidationEnabled() {
        return configurationService.getBooleanProperty(validationEnabledProperty, true);
    }
}
