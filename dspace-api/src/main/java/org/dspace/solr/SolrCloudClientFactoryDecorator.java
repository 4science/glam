/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.solr;

import java.util.Optional;

import org.apache.solr.client.solrj.SolrClient;

/**
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 **/
public class SolrCloudClientFactoryDecorator implements SolrClientFactory {

    private final SolrClientFactory baseFactory;
    private final String uniqueIdField;

    public SolrCloudClientFactoryDecorator(SolrClientFactory baseFactory, String uniqueIdField) {
        this.baseFactory = baseFactory;
        this.uniqueIdField = uniqueIdField;
    }

    @Override
    public Optional<SolrClient> getClient(String coreProperty) {
        return baseFactory
            .getClient(coreProperty)
            .map(baseClient -> new SolrCloudClientDecorator(baseClient, uniqueIdField));
    }

    @Override
    public Optional<SolrClient> getDynamicClient(String name) {
        return baseFactory
            .getDynamicClient(name)
            .map(baseClient -> new SolrCloudClientDecorator(baseClient, uniqueIdField));
    }

}
