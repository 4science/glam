/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.leader;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import org.dspace.AbstractUnitTest;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.cloud.kubernetes.commons.leader.LeaderInitiator;
import org.springframework.cloud.kubernetes.commons.leader.LeaderProperties;
import org.springframework.cloud.kubernetes.commons.leader.LeaderRecordWatcher;
import org.springframework.cloud.kubernetes.commons.leader.LeadershipController;
import org.springframework.cloud.kubernetes.commons.leader.PodReadinessWatcher;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.event.LeaderEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Test for GracefulKubernetesLeaderConfiguration
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
public class GracefulKubernetesLeaderConfigurationTest extends AbstractUnitTest {

    @Mock
    private KubernetesClient kubernetesClient;

    @Mock
    private LeaderProperties leaderProperties;

    @Mock
    private LeadershipController leadershipController;

    @Mock
    private LeaderRecordWatcher leaderRecordWatcher;

    @Mock
    private PodReadinessWatcher podReadinessWatcher;

    @Mock
    private LeaderEventPublisher leaderEventPublisher;

    @Test
    public void testSuccessfulKubernetesConnection() {
        // Given
        GracefulKubernetesLeaderConfiguration config = new GracefulKubernetesLeaderConfiguration();
        ReflectionTestUtils.setField(config, "gracefulFallback", true);

        // Mock successful connection
        when(kubernetesClient.namespaces()).thenReturn(mock(NonNamespaceOperation.class));

        // When
        LeaderInitiator initiator = config.leaderInitiator(kubernetesClient, leaderProperties,
                                                           leadershipController, leaderRecordWatcher,
                                                           podReadinessWatcher, leaderEventPublisher);

        // Then
        assertNotNull(initiator);
        verify(kubernetesClient).namespaces();
    }

    @Test
    public void testKubernetesConnectionFailureWithGracefulFallback() {
        // Given
        GracefulKubernetesLeaderConfiguration config = new GracefulKubernetesLeaderConfiguration();
        ReflectionTestUtils.setField(config, "gracefulFallback", true);

        // Mock connection failure (403 Forbidden)
        when(kubernetesClient.namespaces()).thenThrow(new KubernetesClientException("Forbidden", 403, null));
        when(leaderProperties.isAutoStartup()).thenReturn(true);

        // When
        LeaderInitiator initiator = config.leaderInitiator(kubernetesClient, leaderProperties,
                                                           leadershipController, leaderRecordWatcher,
                                                           podReadinessWatcher, leaderEventPublisher);

        // Then
        assertNotNull(initiator);
        assertTrue(initiator.isAutoStartup());
        verify(kubernetesClient).namespaces();

        initiator.start();
        verify(leaderEventPublisher).publishOnGranted(eq(initiator), any(Context.class), isNull());

        initiator.stop();
        verify(leaderEventPublisher).publishOnRevoked(eq(initiator), any(Context.class), isNull());

    }

    @Test
    public void testKubernetesConnectionFailureWithoutGracefulFallback() {
        // Given
        GracefulKubernetesLeaderConfiguration config = new GracefulKubernetesLeaderConfiguration();
        ReflectionTestUtils.setField(config, "gracefulFallback", false);

        // Mock connection failure (403 Forbidden)
        KubernetesClientException exception = new KubernetesClientException("Forbidden", 403, null);
        when(kubernetesClient.namespaces()).thenThrow(exception);

        // When & Then
        assertThrows(KubernetesClientException.class, () -> {
            config.leaderInitiator(kubernetesClient, leaderProperties,
                                   leadershipController, leaderRecordWatcher, podReadinessWatcher,
                                   leaderEventPublisher);
        });
        verify(kubernetesClient).namespaces();
    }

    @Test
    public void testGenericExceptionWithGracefulFallback() {
        // Given
        GracefulKubernetesLeaderConfiguration config = new GracefulKubernetesLeaderConfiguration();
        ReflectionTestUtils.setField(config, "gracefulFallback", true);

        // Mock generic connection failure
        when(kubernetesClient.namespaces()).thenThrow(new RuntimeException("Network timeout"));
        when(leaderProperties.isAutoStartup()).thenReturn(true);

        // When
        LeaderInitiator initiator = config.leaderInitiator(kubernetesClient, leaderProperties,
                                                           leadershipController, leaderRecordWatcher,
                                                           podReadinessWatcher, leaderEventPublisher);

        // Then
        assertNotNull(initiator);
        assertTrue(initiator.isAutoStartup());
        verify(kubernetesClient).namespaces();

        initiator.start();
        verify(leaderEventPublisher).publishOnGranted(eq(initiator), any(Context.class), isNull());

        initiator.stop();
        verify(leaderEventPublisher).publishOnRevoked(eq(initiator), any(Context.class), isNull());
    }

    @Test
    public void testGenericExceptionWithoutGracefulFallback() {
        // Given
        GracefulKubernetesLeaderConfiguration config = new GracefulKubernetesLeaderConfiguration();
        ReflectionTestUtils.setField(config, "gracefulFallback", false);

        // Mock generic connection failure
        when(kubernetesClient.namespaces()).thenThrow(new RuntimeException("Network timeout"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            config.leaderInitiator(kubernetesClient, leaderProperties,
                                   leadershipController, leaderRecordWatcher, podReadinessWatcher,
                                   leaderEventPublisher);
        });
        verify(kubernetesClient).namespaces();
    }
}