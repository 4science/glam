/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.xoai.services.impl.solr;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.solr.SolrClientFactory;
import org.dspace.xoai.services.api.solr.SolrServerResolver;

public class DSpaceSolrServerResolver implements SolrServerResolver {

    private final SolrClientFactory solrClientFactory = DSpaceServicesFactory.getInstance()
            .getServiceManager()
            .getServiceByName("oaiSolrClientFactory",
                    SolrClientFactory.class);

    @Override
    public SolrClient getServer() throws SolrServerException {
        return solrClientFactory
            .getClient("oai.solr.url")
            .orElseThrow(() -> new SolrServerException("Unable to get Solr client for OAI core"));
    }
}
