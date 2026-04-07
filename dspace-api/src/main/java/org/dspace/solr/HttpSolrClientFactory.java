/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.solr;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.dspace.service.impl.HttpConnectionPoolService;
import org.dspace.services.ConfigurationService;

/**
 * Factory of HtmlSolrClient instances.
 *
 * @author mwood
 */
public class HttpSolrClientFactory implements SolrClientFactory {

    private static final Logger log = LogManager.getLogger(HttpSolrClientFactory.class);


    @Inject
    protected ConfigurationService configurationService;

    @Inject
    @Named("solrHttpConnectionPoolService")
    protected HttpConnectionPoolService httpConnectionPoolService;

    Map<String, SolrClient> solrClients = new ConcurrentHashMap<>();

    @Override
    public Optional<SolrClient> getClient(String coreProperty) {
        String solrService = configurationService.getProperty(coreProperty);
        if (solrService == null) {
            log.warn("{} is not configured", coreProperty);
            return Optional.empty();
        }
        SolrClient client = solrClients.computeIfAbsent(coreProperty, key ->
            new HttpSolrClient.Builder(solrService)
                .withHttpClient(httpConnectionPoolService.getClient())
                .build()
        );
        return Optional.of(client);
    }

    public Optional<SolrClient> getDynamicClient(String name) {

        String solrServerUrl = configurationService.getProperty("solr.server");
        String solrMultiCorePrefix = configurationService.getProperty("solr.multicorePrefix");
        if (solrServerUrl == null || solrMultiCorePrefix == null || name == null) {
            log.warn(
                "Solr dynamic client configuration is incomplete: solr.server='{}', solr.multicorePrefix='{}', " +
                    "name='{}'",
                solrServerUrl, solrMultiCorePrefix, name);
            return Optional.empty();
        }
        String solrService = solrServerUrl + "/" + solrMultiCorePrefix + name;

        SolrClient client = solrClients.computeIfAbsent(name, key ->
            new HttpSolrClient.Builder(solrService)
                .withHttpClient(httpConnectionPoolService.getClient())
                .build()
        );
        return Optional.of(client);
    }
}
