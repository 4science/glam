/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.dspace.leader.DSpaceLeadershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Aspect to ensure that only the leader node in a clustered DSpace environment
 * executes scheduled tasks annotated with @Scheduled. This prevents duplicate
 * execution of scheduled jobs when running multiple application instances.
 * <p>
 * The aspect intercepts all methods annotated with @Scheduled. If the current
 * node is not the leader (as determined by DSpaceLeadershipService), the scheduled
 * method is skipped. Otherwise, the method proceeds as normal.
 * <p>
 * Logging is performed at debug level only, and log level is checked before logging.
 * Errors are logged at error level.
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 */
@Aspect
@Component
public class LeaderScheduledAspect {

    /**
     * Logger for debug and error messages related to scheduled task execution.
     */
    private static final Logger log = LoggerFactory.getLogger(LeaderScheduledAspect.class);

    /**
     * Service to determine if the current node is the leader in a clustered environment.
     * Only the leader node should execute scheduled tasks.
     */
    @Autowired
    private DSpaceLeadershipService leadershipService;

    /**
     * Intercepts all methods annotated with @Scheduled.
     * Only allows execution if the current node is the leader.
     *
     * @param pjp       the join point representing the scheduled method
     * @param scheduled the @Scheduled annotation instance
     * @return the result of the scheduled method, or null if not leader
     * @throws Throwable if the scheduled method throws an exception
     */
    @Around("@annotation(scheduled)")
    public Object aroundScheduled(ProceedingJoinPoint pjp, Scheduled scheduled) throws Throwable {
        String methodName = pjp.getSignature().toShortString();
        // Log at debug level if enabled
        if (log.isDebugEnabled()) {
            log.debug("Scheduled task triggered: {}", methodName);
        }

        // Check leadership before proceeding
        if (!leadershipService.isLeader()) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping scheduled task {}: not the leader", methodName);
            }
            return null;
        }

        try {
            Object result = pjp.proceed();
            // Log completion at debug level if enabled
            if (log.isDebugEnabled()) {
                log.debug("Scheduled task completed: {}", methodName);
            }
            return result;
        } catch (Exception e) {
            // Log errors at error level
            log.error("Error in scheduled task: {}", methodName, e);
            throw e;
        }
    }
}
