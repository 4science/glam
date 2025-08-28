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

import org.dspace.content.Bitstream;
import org.dspace.core.Context;

/**
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DroidSimpleDispatcher extends SimpleDispatcher {
    /**
     * Creates a new SimpleDispatcher.
     *
     * @param context   Context
     * @param startTime timestamp for beginning of checker process
     * @param looping   indicates whether checker should loop infinitely through
     *                  most_recent_checksum table
     */
    public DroidSimpleDispatcher(Context context, Date startTime, boolean looping) {
        super(context, startTime, looping);
    }


    @Override
    protected Bitstream findByDate(Context context, Date date) {
        Bitstream found = null;
        try {
            MostRecentChecksum checksum = checksumService.findOldestByDroid(context, date);
            if (checksum != null) {
                found = checksum.getBitstream();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return found;
    }

    @Override
    protected Bitstream loop(Context context) {
        Bitstream found = null;
        try {
            MostRecentChecksum checksum = checksumService.findOldestByDroid(context);
            if (checksum != null) {
                found = checksum.getBitstream();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return found;
    }
}
