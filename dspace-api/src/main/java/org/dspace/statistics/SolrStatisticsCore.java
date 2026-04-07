/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.statistics;

import org.apache.solr.client.solrj.SolrClient;
import org.dspace.solr.SolrClientFactory;

/**
 * Bean containing the {@link SolrClient} for the statistics core
 */
public class SolrStatisticsCore {

    private SolrClientFactory solrClientFactory;

    public void setSolrClientFactory(SolrClientFactory solrClientFactory) {
        this.solrClientFactory = solrClientFactory;
    }

    /**
     * @return The {@link SolrClient} for the Statistics core
     */
    public SolrClient getSolr() {
        return solrClientFactory
            .getClient("solr-statistics.server")
            .orElseThrow(() -> new RuntimeException("Unable to get Solr client for statistics core"));
    }

}
