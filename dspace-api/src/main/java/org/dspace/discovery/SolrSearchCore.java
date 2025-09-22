/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.dspace.solr.SolrClientFactory;

/**
 * Bean containing the SolrClient for the search core.
 *
 * @author Kevin Van de Velde (kevin at atmire dot com)
 */
public class SolrSearchCore {

    /**
     * Default HTTP method to use for all Solr Requests (we prefer POST).
     * This REQUEST_METHOD should be used in all Solr queries, e.g.
     * solSearchCore.getSolr().query(myQuery, solrSearchCore.REQUEST_METHOD);
     */
    public SolrRequest.METHOD REQUEST_METHOD = SolrRequest.METHOD.POST;

    @Inject
    @Named("searchSolrClientFactory")
    private SolrClientFactory solrClientFactory;

    /**
     * Get access to current SolrClient. If no current SolrClient exists, a new one is initialized, see initSolr().
     *
     * @return SolrClient Solr client
     */
    public SolrClient getSolr() {
        return solrClientFactory
            .getClient("discovery.search.server")
            .orElseThrow(() -> new RuntimeException("Unable to get Solr client for search core"));
    }
}
