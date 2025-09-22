/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.solr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.JettyConfig;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.cloud.MiniSolrCloudCluster;
import org.apache.zookeeper.KeeperException;
import org.dspace.AbstractDSpaceIntegrationTest;

/**
 * Factory of connections to an in-process embedded SolrCloud service.
 * Each instance of this class returns connections to collections in a SolrCloud cluster.
 * Each connection behaves as would a client connection to a remote SolrCloud service.
 *
 * <p>
 * The SolrCloud cluster is started as needed with embedded ZooKeeper.
 * There is at most one open connection to each collection; connections are shared and
 * reference-counted. When the last connection to the last collection is closed,
 * the cluster is shut down.
 *
 * <p>
 * To use this:
 * <ol>
 *   <li>{@code SolrClient mycollection = new MockSolrCloudServer("mycollection").getSolrServer();}</li>
 *   <li>{@code mycollection.this(); mycollection.that();}</li>
 *   <li>{@code mycollection.destroy();}</li>
 * </ol>
 */
public class MockSolrCloudServer {

    static {
        // Force HTTP/1.1 for embedded testing
        System.setProperty("solr.http1", "true");
        System.setProperty("solr.jetty.http2.enabled", "false");
    }

    private static final Logger log = LogManager.getLogger();

    /** Single SolrCloud client per collection name. */
    private static final ConcurrentMap<String, CloudSolrClient> clientsByCollection = new ConcurrentHashMap<>();

    /** Reference counts for each collection. */
    private static final ConcurrentMap<String, AtomicLong> usersPerCollection = new ConcurrentHashMap<>();

    /** Mini SolrCloud cluster for embedded testing. */
    private static MiniSolrCloudCluster cluster = null;

    /** Name of this connection's collection. */
    private final String collectionName;

    /** Number of shards for the collection (default 1). */
    private final int numShards;

    /** Replication factor for the collection (default 1). */
    private final int replicationFactor;

    /** This instance's collection client. */
    private CloudSolrClient collectionClient = null;

    /**
     * Wrap an instance of embedded SolrCloud.
     *
     * @param collectionName name of the collection to serve.
     */
    public MockSolrCloudServer(final String collectionName) {
        this(collectionName, 1, 1);
    }

    /**
     * Wrap an instance of embedded SolrCloud.
     *
     * @param collectionName name of the collection to serve.
     * @param numShards number of shards for the collection.
     * @param replicationFactor replication factor for the collection.
     */
    public MockSolrCloudServer(final String collectionName, final int numShards, final int replicationFactor) {
        this.collectionName = collectionName;
        this.numShards = numShards;
        this.replicationFactor = replicationFactor;
        initSolrCloudServer();
    }

    /**
     * @return the wrapped CloudSolrClient for this collection.
     */
    public SolrClient getSolrServer() {
        return collectionClient;
    }

    /**
     * Ensure that this instance's collection client is available.
     */
    protected void initSolrCloudServer() {
        collectionClient = getOrCreateClientForCollection(collectionName);

        // Increment reference count
        usersPerCollection.computeIfAbsent(collectionName, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Get or create the single CloudSolrClient for a collection.
     * This ensures exactly one client per collection across all MockSolrCloudServer instances.
     *
     * @param collectionName name of the collection
     * @return the single CloudSolrClient for this collection
     */
    private synchronized CloudSolrClient getOrCreateClientForCollection(final String collectionName) {
        return clientsByCollection.computeIfAbsent(collectionName, name -> {
            try {
                // Initialize cluster if needed
                initSolrCloudCluster();

                // Create ONE client for this collection
                String zkHost = cluster.getZkClient().getZkServerAddress();
                CloudSolrClient client = new CloudSolrClient.Builder(List.of(zkHost), Optional.empty())
                    .withConnectionTimeout(10000000)
                    .withSocketTimeout(15000000)
                    .build();
                client.setDefaultCollection(name);

                // Create collection if it doesn't exist
                if (!collectionExists(name)) {
                    createCollection(name);
                }

                // Start with an empty index
                client.deleteByQuery("*:*");
                client.commit();

                log.info("Created single CloudSolrClient for collection: {}", name);
                return client;

            } catch (Exception e) {
                log.error("Failed to create client for collection: {}", name, e);
                throw new RuntimeException("Cannot create client for collection: " + name, e);
            }
        });
    }

    /**
     * Check if a collection exists in the cluster.
     */
    private boolean collectionExists(String collectionName) {
        try {
            List<String> collections = CollectionAdminRequest.listCollections(cluster.getSolrClient());
            return collections.contains(collectionName);
        } catch (Exception e) {
            log.warn("Error checking if collection exists: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create a collection in the SolrCloud cluster.
     */
    private void createCollection(String collectionName)
        throws SolrServerException, IOException, InterruptedException, KeeperException {

        // Upload default configset if not already present
        Path solrDir = Paths.get(AbstractDSpaceIntegrationTest.getDspaceDir(), "solr");
        Path configDir = solrDir.resolve("configsets").resolve(collectionName).resolve("conf");
        if (configDir.toFile().exists()) {
            cluster.uploadConfigSet(configDir, collectionName);
        }

        // Calculate maxShardsPerNode for proper distribution
        int totalReplicas = numShards * replicationFactor;
        int numNodes = cluster.getJettySolrRunners().size();
        int maxShardsPerNode = Math.max(1, (int) Math.ceil((double) totalReplicas / numNodes));

        log.info("Creating collection {} with {} shards, replication factor {}, maxShardsPerNode {}",
                 collectionName, numShards, replicationFactor, maxShardsPerNode);

        // Create collection
        CollectionAdminRequest.Create createRequest = CollectionAdminRequest
            .createCollection(collectionName, collectionName, numShards, replicationFactor)
            .setMaxShardsPerNode(maxShardsPerNode);

        createRequest.process(cluster.getSolrClient());

        log.info("Successfully created collection {}", collectionName);
    }

    /**
     * Remove all records from this collection - SolrCloud compatible version.
     */
    public void reset() {
        try {
            if (collectionClient == null) {
                log.warn("Collection client is null, cannot reset collection '{}'", collectionName);
                return;
            }

            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.deleteByQuery("*:*");
            updateRequest.setCommitWithin(1);
            updateRequest.process(collectionClient);

            // Additional hard commit for immediate consistency
            collectionClient.commit(true, true, true);

        } catch (Exception ex) {
            log.error("Exception while clearing '{}' collection", collectionName, ex);
            throw new RuntimeException("Failed to reset collection: " + collectionName, ex);
        }
    }

    /**
     * Decrease the reference count for this collection.
     */
    public void destroy() throws Exception {
        if (collectionClient == null) {
            return; // Already cleaned up
        }

        // Synchronize to prevent race conditions during cleanup
        synchronized (MockSolrCloudServer.class) {
            AtomicLong userCount = usersPerCollection.get(collectionName);
            if (userCount != null) {
                long remainingUsers = userCount.decrementAndGet();

                if (remainingUsers <= 0) {
                    // Last user - close and remove the client
                    CloudSolrClient clientToClose = clientsByCollection.remove(collectionName);
                    if (clientToClose != null) {
                        try {
                            clientToClose.close();
                            log.info("Closed CloudSolrClient for collection: {}", collectionName);
                        } catch (IOException e) {
                            log.warn("Error closing client for collection: {}", collectionName, e);
                        }
                    }
                    usersPerCollection.remove(collectionName);
                }

                // If no collections remain, destroy the cluster
                if (clientsByCollection.isEmpty() && cluster != null) {
                    destroyCluster();
                }
            } else {
                log.debug("Collection {} already cleaned up by cleanupAll()", collectionName);
            }
        }

        collectionClient = null;
    }

    /**
     * Ensure that a SolrCloud cluster is allocated with embedded ZooKeeper.
     */
    private static synchronized void initSolrCloudCluster() {
        if (cluster == null) {
            try {
                // Set ZooKeeper system properties BEFORE creating cluster
                System.setProperty("zookeeper.globalOutstandingLimit", "100000");
                System.setProperty("zookeeper.maxCnxns", "10000");
                System.setProperty("znode.container.checkIntervalMs", "60000");
                System.setProperty("zookeeper.admin.enableServer", "false");

                // Create unique temporary directory for each test run
                Path baseDir = createTemporaryDirectory();

                // Create cluster with 2 nodes
                JettyConfig jettyConfig = JettyConfig.builder()
                                                     .setPort(0) // Use random ports
                                                     .build();

                log.info("Initializing SolrCloud cluster with 2 servers in directory {}",
                         baseDir.toAbsolutePath().toString());

                cluster = new MiniSolrCloudCluster(2, baseDir, jettyConfig);

                // Register shutdown hook for cleanup
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        cleanupAll();
                    } catch (Exception e) {
                        log.warn("Error during shutdown cleanup", e);
                    }
                }));

                log.info("SolrCloud cluster initialized with ZooKeeper at: {}",
                         cluster.getZkClient().getZkServerAddress());
            } catch (Exception e) {
                log.error("Failed to initialize SolrCloud cluster", e);
                throw new RuntimeException("Cannot start SolrCloud cluster", e);
            }
        }
    }

    /**
     * Force cleanup of all clients and cluster - make it synchronized and idempotent.
     */
    public static synchronized void cleanupAll() {
        if (clientsByCollection.isEmpty() && cluster == null) {
            log.debug("Already cleaned up, skipping cleanupAll()");
            return;
        }

        // Close all clients
        for (String collectionName : clientsByCollection.keySet()) {
            CloudSolrClient client = clientsByCollection.remove(collectionName);
            if (client != null) {
                try {
                    client.close();
                    log.info("Force closed client for collection: {}", collectionName);
                } catch (IOException e) {
                    log.warn("Error force closing client for collection: {}", collectionName, e);
                }
            }
        }

        clientsByCollection.clear();
        usersPerCollection.clear();

        destroyCluster();
    }

    /**
     * Create a unique temporary directory for the SolrCloud cluster.
     */
    private static Path createTemporaryDirectory() throws IOException {
        String tempDirProperty = System.getProperty("java.io.tmpdir");
        Path tempDir = Paths.get(tempDirProperty);

        String uniqueId = String.format("solrcloud-test-%d-%d",
                                        System.currentTimeMillis(),
                                        (int)(Math.random() * 10000));

        Path baseDir = tempDir.resolve(uniqueId);

        if (Files.exists(baseDir)) {
            deleteDirectory(baseDir);
        }

        Files.createDirectories(baseDir);
        log.info("Created temporary SolrCloud directory: {}", baseDir.toAbsolutePath());

        return baseDir;
    }

    /**
     * Recursively delete a directory and all its contents.
     */
    private static void deleteDirectory(Path directory) {
        if (Files.exists(directory)) {
            try {
                Files.walk(directory)
                     .sorted(java.util.Comparator.reverseOrder())
                     .map(Path::toFile)
                     .forEach(File::delete);
                log.info("Cleaned up temporary directory: {}", directory.toAbsolutePath());
            } catch (IOException e) {
                log.warn("Failed to clean up temporary directory: {}", directory.toAbsolutePath(), e);
            }
        }
    }

    /**
     * Discard the embedded SolrCloud cluster.
     */
    private static synchronized void destroyCluster() {
        if (cluster != null) {
            try {
                // Get the base directory before shutdown
                Path baseDir = cluster.getBaseDir();
                cluster.shutdown();
                cluster = null;

                // Clean up the temporary directory
                deleteDirectory(baseDir);
                log.info("SolrCloud cluster destroyed and cleaned up");
            } catch (Exception e) {
                log.error("Error shutting down SolrCloud cluster", e);
            }
        }
    }
}
