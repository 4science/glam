/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.solr;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * Factory of EmbeddedSolrClient instances.
 * Wrapper for {@link org.dspace.solr.MockSolrServer}.  Possibly useful for
 * testing.
 *
 * @author mwood
 */
public class MockSolrClientFactory implements SolrClientFactory, DisposableBean {
    private static final Logger log = LogManager.getLogger();

    // Registry for all created MockSolrServer and MockSolrCloudServer instances by core name
    private final Map<String, MockSolrServer> mockSolrServers = new ConcurrentHashMap<>();
    private final Map<String, MockSolrCloudServer> mockSolrCloudServers = new ConcurrentHashMap<>();

    private static String extractCoreName(String coreUrl) {
        try {
            return Path.of(new URL(coreUrl).getPath())
                       .getFileName()
                       .toString();
        } catch (MalformedURLException ex) {
            log.warn("Unable to extract core name from URI '{}':  {}",
                     coreUrl, ex.getMessage());
        }
        return null;
    }

    @Override
    public Optional<SolrClient> getClient(String coreProperty) {
        ConfigurationService configurationService = DSpaceServicesFactory.getInstance()
                                                                         .getConfigurationService();

        String coreUrl = configurationService.getProperty(coreProperty);
        String coreName = extractCoreName(coreUrl);

        return getSolrClient(coreName);
    }

    private Optional<SolrClient> getSolrClient(String coreName) {

        ConfigurationService configurationService = DSpaceServicesFactory.getInstance()
                                                                         .getConfigurationService();

        boolean isSolrCloudEnabled = configurationService.getBooleanProperty("solr.cloud.enabled", false);
        String serverKey = coreName + "_" + (isSolrCloudEnabled ? "cloud" : "standalone");

        if (isSolrCloudEnabled) {
            return Optional.ofNullable(mockSolrCloudServers
                                           .computeIfAbsent(serverKey, key -> new MockSolrCloudServer(coreName, 2, 2))
                                           .getSolrServer());
        } else {
            return Optional.ofNullable(mockSolrServers
                                           .computeIfAbsent(serverKey, key -> new MockSolrServer(coreName))
                                           .getSolrServer());
        }
    }

    @Override
    public Optional<SolrClient> getDynamicClient(String name) {
        return getSolrClient(name);
    }

    /**
     * Remove all records from all tracked mock Solr servers.
     */
    public void resetAll() {
        mockSolrServers.values().forEach(MockSolrServer::reset);
        mockSolrCloudServers.values().forEach(MockSolrCloudServer::reset);
    }

    /**
     * Decrease the reference count for connection to the current core.
     * If now zero, shut down the connection and discard it.  If no connections
     * remain, destroy the container.
     *
     * @throws Exception passed through.
     */
    @Override
    public void destroy() throws Exception {
        mockSolrServers.values().forEach(mock -> {
            try {
                mock.destroy();
            } catch (Exception e) {
                log.error("Error destroying MockSolrServer", e);
            }
        });
        mockSolrCloudServers.values().forEach(mock -> {
            try {
                mock.destroy();
            } catch (Exception e) {
                log.error("Error destroying MockSolrCloudServer", e);
            }
        });
    }

}
