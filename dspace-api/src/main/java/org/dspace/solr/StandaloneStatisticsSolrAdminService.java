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
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CoreAdminParams;
import org.apache.solr.common.util.NamedList;
import org.dspace.services.ConfigurationService;

/**
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 **/
public class StandaloneStatisticsSolrAdminService implements SolrAdminService {

    private static final Logger log = LogManager.getLogger(StandaloneStatisticsSolrAdminService.class);

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
            .orElseThrow(() -> new RuntimeException("Cannot get base core SolrClient"));

        CoreAdminRequest coresRequest = new CoreAdminRequest();
        coresRequest.setAction(CoreAdminParams.CoreAdminAction.STATUS);
        CoreAdminResponse coresResponse = coresRequest.process(baseClient);

        NamedList<Object> response = coresResponse.getResponse();
        NamedList<Object> coreStatuses = (NamedList<Object>) response.get("status");

        List<String> shards = new ArrayList<>();
        for (Map.Entry<String, Object> entry : coreStatuses) {
            String coreName = entry.getKey();
            if (coreName.startsWith(configSetName)) {
                shards.add(coreName);
            }
        }
        return shards;
    }

    @Override
    public SolrClient createShard(String shardName) throws SolrServerException, IOException {
        SolrClient currentClient = solrClientFactory
            .getDynamicClient(shardName)
            .orElseThrow(() -> new RuntimeException("Unable to get Solr client for statistics core"));

        try {
            SolrPingResponse ping = currentClient.ping();
            log.debug("Ping of Solr Core {} returned with Status {}",
                      shardName, ping.getStatus());
            return currentClient;
        } catch (IOException | SolrException | SolrServerException e) {
            log.debug("Ping of Solr Core {} failed with {}.  New Core Will be Created",
                      shardName, e.getClass().getName());
        }

        CoreAdminRequest.Create create = new CoreAdminRequest.Create();
        create.setCoreName(shardName);
        String configSetName = configurationService
            .getProperty("solr-statistics.configset", "statistics");
        create.setConfigSet(configSetName);
        create.setInstanceDir(shardName);

        SolrClient solrServer = solrClientFactory
            .getDynamicClient(shardName)
            .orElseThrow(() -> new RuntimeException("Unable to get Solr client for statistics core"));
        create.process(solrServer);
        log.info("Created core with name: {} from configset {}", shardName, configSetName);
        return solrServer;
    }
}
