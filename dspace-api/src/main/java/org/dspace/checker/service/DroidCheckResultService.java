/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.service;

import java.sql.SQLException;
import java.util.List;

import org.dspace.checker.DroidCheckResult;
import org.dspace.checker.DroidValidationException;
import org.dspace.checker.MostRecentChecksum;
import org.dspace.content.Bitstream;
import org.dspace.core.Context;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public interface DroidCheckResultService {

    DroidCheckResult create(Context context, MostRecentChecksum recentChecksum);

    DroidCheckResult save(Context context, DroidCheckResult droidCheckResult) throws SQLException;

    List<DroidCheckResult> findBy(Context context, Bitstream bitstream) throws SQLException;

    List<DroidCheckResult> validate(Context context, MostRecentChecksum checksum)
        throws SQLException, DroidValidationException;
}
