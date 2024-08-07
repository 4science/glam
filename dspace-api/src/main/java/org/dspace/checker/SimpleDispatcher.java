/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;
import java.util.Iterator;
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

    protected int fetchSize = 10;

    private int offset = 0;

    private MostRecentChecksum lastChecksum;

    private Iterator<MostRecentChecksum> toProcess = null;

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
        if (toProcess == null ||
            (!toProcess.hasNext() && offset % fetchSize == 0)
        ) {
            findRecentChecksumToProcess();
        }

        Bitstream bitstream = null;
        if (
            toProcess.hasNext()
        ) {
            if (toProcess.next() != null) {
                context.uncacheEntity(lastChecksum);
                lastChecksum = toProcess.next();
                bitstream = lastChecksum.getBitstream();
            }
            offset++;
        }

        return bitstream;
    }

    private void findRecentChecksumToProcess() throws SQLException {
        if (fetchByDate) {
            // will retrieve the bitstreams to process.
            this.toProcess = checksumService.findAll(context, processStartTime, 0, fetchSize);
        } else {
            this.toProcess = checksumService.findAll(context, Date.from(Instant.MAX), offset, fetchSize);
        }
    }
}
