/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.leader;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.kubernetes.commons.leader.LeaderContext;
import org.springframework.cloud.kubernetes.commons.leader.LeaderInitiator;
import org.springframework.cloud.kubernetes.commons.leader.LeaderProperties;
import org.springframework.cloud.kubernetes.commons.leader.LeaderRecordWatcher;
import org.springframework.cloud.kubernetes.commons.leader.LeadershipController;
import org.springframework.cloud.kubernetes.commons.leader.PodReadinessWatcher;
import org.springframework.cloud.kubernetes.fabric8.leader.Fabric8LeaderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.event.LeaderEventPublisher;

/**
 *
 * This configuration will attempt to create the leader election components,
 * but if Kubernetes connectivity fails (e.g., due to RBAC permissions or
 * network issues), it will log the error and continue startup without
 * leader election functionality.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
@Configuration
@ConditionalOnClass(KubernetesClient.class)
@ConditionalOnProperty(
    value = {"spring.cloud.kubernetes.leader.enabled"},
    matchIfMissing = true
)
public class GracefulKubernetesLeaderConfiguration extends Fabric8LeaderAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GracefulKubernetesLeaderConfiguration.class);

    @Value("${dspace.kubernetes.leader.graceful-fallback:true}")
    private boolean gracefulFallback;

    /**
     * Creates a LeaderInitiator that can handle Kubernetes connectivity failures gracefully.
     * If Kubernetes API access fails, it logs the error and creates a no-op leader initiator
     * that allows the application to continue startup.
     */
    @Bean(
        destroyMethod = "stop"
    )
    @Primary
    public LeaderInitiator leaderInitiator(
        KubernetesClient kubernetesClient,
        LeaderProperties leaderProperties,
        LeadershipController leadershipController,
        LeaderRecordWatcher leaderRecordWatcher,
        PodReadinessWatcher podReadinessWatcher,
        LeaderEventPublisher leaderEventPublisher
    ) {
        try {
            // Test Kubernetes connectivity by attempting a simple API call
            kubernetesClient.namespaces().list();
            log.info("Kubernetes API connectivity verified. Creating standard LeaderInitiator.");
            return new LeaderInitiator(leaderProperties, leadershipController, leaderRecordWatcher,
                                       podReadinessWatcher);
        } catch (KubernetesClientException e) {
            if (gracefulFallback) {
                log.warn(
                    "Kubernetes API access failed ({}). Creating fallback LeaderInitiator that allows startup to " +
                        "continue. " +
                        "Leader election will be disabled. Error details: {}",
                    e.getCode(), e.getMessage());
                log.debug("Full Kubernetes connectivity error:", e);
                return createFallbackLeaderInitiator(leaderProperties, leadershipController, leaderEventPublisher);
            } else {
                log.error("Kubernetes API access failed and graceful fallback is disabled. " +
                              "Set 'dspace.kubernetes.leader.graceful-fallback=true' to allow startup to continue. " +
                              "Error: {}", e.getMessage());
                throw e;
            }
        } catch (Exception e) {
            if (gracefulFallback) {
                log.warn("Unexpected error testing Kubernetes connectivity. Creating fallback LeaderInitiator. " +
                             "Leader election will be disabled. Error: {}", e.getMessage());
                log.debug("Full Kubernetes connectivity error:", e);
                return createFallbackLeaderInitiator(leaderProperties, leadershipController, leaderEventPublisher);
            } else {
                log.error("Kubernetes connectivity test failed and graceful fallback is disabled. " +
                              "Set 'dspace.kubernetes.leader.graceful-fallback=true' to allow startup to continue. " +
                              "Error: {}", e.getMessage());
                throw new RuntimeException("Kubernetes leader election initialization failed", e);
            }
        }
    }

    /**
     * Creates a fallback LeaderInitiator that always grants leadership immediately
     * and handles start/stop lifecycle gracefully without attempting Kubernetes operations.
     */
    private LeaderInitiator createFallbackLeaderInitiator(
        LeaderProperties leaderProperties,
        LeadershipController leadershipController,
        LeaderEventPublisher leaderEventPublisher
    ) {
        return new LeaderInitiator(leaderProperties, leadershipController, null, null) {
            private boolean isRunning = false;

            @Override
            public void start() {
                if (!isRunning()) {
                    log.info("Starting fallback LeaderInitiator (Kubernetes unavailable). " +
                                 "This instance will assume leadership role.");
                    isRunning = true;
                    // Grant leadership immediately since we can't coordinate with other instances
                    try {
                        Context context = new LeaderContext(null, leadershipController);
                        leaderEventPublisher.publishOnGranted(this, context, null);
                    } catch (Exception e) {
                        log.warn("Error granting leadership in fallback mode: {}", e.getMessage());
                    }
                }
            }

            @Override
            public void stop() {
                if (isRunning()) {
                    log.info("Stopping fallback LeaderInitiator");
                    try {
                        Context context = new LeaderContext(null, leadershipController);
                        leaderEventPublisher.publishOnRevoked(this, context, null);
                    } catch (Exception e) {
                        log.debug("Error revoking leadership in fallback mode: {}", e.getMessage());
                    }
                    isRunning = false;
                }
            }

            @Override
            public boolean isRunning() {
                return isRunning;
            }

            @Override
            public int getPhase() {
                return Integer.MAX_VALUE - 1000; // Start late to allow other components to initialize
            }

            @Override
            public boolean isAutoStartup() {
                return leaderProperties.isAutoStartup();
            }
        };
    }
}