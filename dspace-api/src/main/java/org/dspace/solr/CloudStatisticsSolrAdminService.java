/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.solr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.response.CollectionAdminResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.common.SolrException;
import org.dspace.services.ConfigurationService;

/**
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 **/
public class CloudStatisticsSolrAdminService implements SolrAdminService {

    private static final Logger log = LogManager.getLogger(CloudStatisticsSolrAdminService.class);

    @Inject
    @Named("statisticsClientFactory")
    private SolrClientFactory solrClientFactory;
    @Inject
    private ConfigurationService configurationService;


    @Override
    public List<String> listShards() throws SolrServerException, IOException {
        String configSetName = configurationService
            .getProperty("solr-statistics.configset", "statistics");
        SolrClient baseClient = solrClientFactory
            .getDynamicClient(configSetName)
            .orElseThrow(() -> new RuntimeException("Cannot get base collection SolrClient"));

        CollectionAdminRequest.List listRequest = new CollectionAdminRequest.List();
        CollectionAdminResponse listResponse = listRequest.process(baseClient);

        List<String> allCollections = (List<String>) listResponse.getResponse().get("collections");
        List<String> shards = new ArrayList<>();
        for (String collection : allCollections) {
            if (collection.startsWith(configSetName)) {
                shards.add(collection);
            }
        }
        return shards;
    }

    @Override
    public SolrClient createShard(String shardName) throws SolrServerException, IOException {
        List<String> shards = listShards();
        if (shards.contains(shardName)) {
            Optional<SolrClient> currentClientOpt = solrClientFactory
                .getDynamicClient(shardName);
            if (currentClientOpt.isPresent()) {
                try {
                    SolrClient currentClient = currentClientOpt.get();
                    SolrPingResponse ping = currentClient.ping();
                    log.debug("Ping of Solr Collection {} returned with Status {}",
                              shardName, ping.getStatus());
                    return currentClient;
                } catch (IOException | SolrException | SolrServerException e) {
                    log.debug("Ping of Solr Collection {} failed with {}.  New Core Will be Created",
                              shardName, e.getClass().getName());
                }
            }
        }

        String configSetName = configurationService
            .getProperty("solr-statistics.configset", "statistics");
        // TODO make shards and replicas configurable
        int numShards = 2;
        int replicationFactor = 2;
        int maxShardsPerNode = 2;

        CollectionAdminRequest.Create createRequest = CollectionAdminRequest.createCollection(
            shardName,
            configSetName,
            numShards,
            replicationFactor
        ).setMaxShardsPerNode(maxShardsPerNode);

        SolrClient solrClient = solrClientFactory
            .getDynamicClient(configSetName)
            .orElseThrow(() -> new RuntimeException("Unable to get Solr client for statistics collection"));

        createRequest.process(solrClient);
        log.info("Created collection with name: {} from configset {}", shardName, configSetName);

        return solrClientFactory
            .getDynamicClient(shardName)
            .orElseThrow(() -> new RuntimeException("Unable to get Solr client for " + shardName + " collection"));
    }
}

