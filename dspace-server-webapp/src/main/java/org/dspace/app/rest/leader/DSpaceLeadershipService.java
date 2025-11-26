/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.leader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;
import org.springframework.stereotype.Service;

/**
 * Main service for checking leadership status in DSpace.
 * This service automatically delegates to the appropriate implementation
 * based on configuration (LeaderElectionService or NoLeaderElectionService).
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 */
@Service
public class DSpaceLeadershipService {

    private static final Logger log = LoggerFactory.getLogger(DSpaceLeadershipService.class);

    private boolean isLeader;

    /**
     * Called when this pod becomes the leader.
     * Enables scheduled task execution.
     */
    @EventListener
    public void onGrantedEvent(OnGrantedEvent event) {
        this.isLeader = true;
        log.info("DSpace pod became leader - enabling scheduled tasks");
    }


    /**
     * Check if this instance should execute scheduled tasks.
     * This is the primary method that should be used by scheduled task methods.
     *
     * @return true if this instance should execute scheduled tasks
     */
    public boolean isLeader() {
        return isLeader;
    }

    /**
     * Called when this pod loses leadership.
     * Disables scheduled task execution.
     */
    @EventListener
    public void onRevokedEvent(OnRevokedEvent event) {
        this.isLeader = false;
        log.info("DSpace pod lost leadership - disabling scheduled tasks");
    }
}