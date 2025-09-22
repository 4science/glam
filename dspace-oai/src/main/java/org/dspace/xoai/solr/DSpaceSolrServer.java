/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.xoai.solr;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.dspace.solr.SolrClientFactory;

/**
 * @author Lyncode Development Team (dspace at lyncode dot com)
 */
public class DSpaceSolrServer {
    private static final Logger log = LogManager.getLogger(DSpaceSolrServer.class);

    @Inject
    @Named("oaiSolrClientFactory")
    private SolrClientFactory solrClientFactory;

    /**
     * Default constructor
     */
    private DSpaceSolrServer() {
    }

    public SolrClient getServer() throws SolrServerException {
        return solrClientFactory
            .getClient("oai.solr.url")
            .orElseThrow(() -> new SolrServerException("Unable to get Solr client for OAI core"));
    }
}
