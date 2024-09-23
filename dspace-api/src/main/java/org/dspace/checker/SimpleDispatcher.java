/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.sql.SQLException;
import java.util.Date;
import java.util.Optional;

import org.dspace.checker.factory.CheckerServiceFactory;
import org.dspace.checker.service.MostRecentChecksumService;
import org.dspace.content.Bitstream;
import org.dspace.core.Context;

/**
 * An implementation of the selection strategy that selects bitstreams in the
 * order that they were last checked, looping endlessly.
 *
 * @author Jim Downing
 * @author Grace Carpenter
 * @author Nathan Sarr
 */
public class SimpleDispatcher implements BitstreamDispatcher {

    /**
     * Access for bitstream information
     */
    protected final MostRecentChecksumService checksumService =
        CheckerServiceFactory.getInstance().getMostRecentChecksumService();

    /**
     * Should this dispatcher keep on dispatching around the collection?
     */
    protected final boolean loopContinuously;

    /**
     * Date this dispatcher started dispatching.
     */
    protected final Date processStartTime;

    protected final boolean fetchByDate;

    protected final Context context;

    /**
     * Creates a new SimpleDispatcher.
     *
     * @param context   Context
     * @param startTime timestamp for beginning of checker process
     * @param looping   indicates whether checker should loop infinitely through
     *                  most_recent_checksum table
     */
    public SimpleDispatcher(Context context, Date startTime, boolean looping) {
        this.context = context;
        this.processStartTime =
            Optional.ofNullable(startTime)
                    .map(time -> new Date(time.getTime()))
                    .orElse(null);
        this.loopContinuously = looping;
        this.fetchByDate = !this.loopContinuously && (processStartTime != null);
    }

    /**
     * Selects the next candidate bitstream.
     *
     * @throws SQLException if database error
     * @see org.dspace.checker.BitstreamDispatcher#next()
     */
    @Override
    public synchronized Bitstream next() throws SQLException {
        // should process loop infinitely through the
        // bitstreams in most_recent_checksum table?
        MostRecentChecksum oldestRecord;
        if (!loopContinuously && (processStartTime != null)) {
            oldestRecord = checksumService.findOldestRecord(context, processStartTime);
        } else {
            oldestRecord = checksumService.findOldestRecord(context);
        }
        if (oldestRecord != null) {
            return oldestRecord.getBitstream();
        } else {
            return null;
        }

    }
}
