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

import org.dspace.checker.ChecksumHistory;
import org.dspace.checker.DroidCheckHistory;
import org.dspace.checker.MostRecentChecksum;
import org.dspace.content.Bitstream;
import org.dspace.core.Context;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public interface DroidHistoryService {

    void addHistory(Context context, ChecksumHistory history, MostRecentChecksum mostRecentChecksum);

    List<DroidCheckHistory> findBy(Context context, Bitstream bitstream) throws SQLException;

    void deleteByBitstream(Context context, Bitstream bitstream) throws SQLException;
}
