/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.solr;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.solr.client.solrj.SolrServerException;
import org.dspace.services.ConfigurationService;

/**
 * Factory for SolrAdminService implementations.
 * Returns Standalone or Cloud Solr admin service depending on configuration.
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 **/
public class SolrAdminServiceFactory {

    @Inject
    @Named("cloudStatisticsSolrAdminService")
    private SolrAdminService cloudService;
    @Inject
    @Named("standaloneStatisticsSolrAdminService")
    private SolrAdminService standaloneService;
    @Inject
    private ConfigurationService configurationService;

    /**
     * Returns the appropriate SolrAdminService depending on config.
     *
     * @return SolrAdminService implementation
     * @throws IOException
     * @throws SolrServerException
     */
    public SolrAdminService getSolrAdminService() {
        boolean solrCloudEnabled = configurationService.getBooleanProperty("solr.cloud.enabled", false);
        if (solrCloudEnabled) {
            return cloudService;
        } else {
            return standaloneService;
        }
    }
}
