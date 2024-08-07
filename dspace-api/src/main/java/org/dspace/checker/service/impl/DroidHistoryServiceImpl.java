/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.service.impl;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.dspace.checker.ChecksumHistory;
import org.dspace.checker.DroidCheckHistory;
import org.dspace.checker.DroidCheckResult;
import org.dspace.checker.MostRecentChecksum;
import org.dspace.checker.dao.DroidCheckHistoryDAO;
import org.dspace.checker.service.DroidHistoryService;
import org.dspace.content.Bitstream;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DroidHistoryServiceImpl implements DroidHistoryService {

    @Autowired
    private DroidCheckHistoryDAO droidCheckHistoryDAO;

    @Override
    public void addHistory(Context context, ChecksumHistory history, MostRecentChecksum mostRecentChecksum) {
        List<DroidCheckResult> droidCheckResults = mostRecentChecksum.getDroidCheckResults();
        if (droidCheckResults == null || droidCheckResults.isEmpty()) {
            return;
        }
        List<DroidCheckHistory> droidCheckHistories = new ArrayList<>(droidCheckResults.size());
        for (DroidCheckResult droidCheckResult : droidCheckResults) {
            DroidCheckHistory dch = new DroidCheckHistory()
                .setFilename(droidCheckResult.getFilename())
                .setFileExtension(droidCheckResult.getFileExtension())
                .setFileFormat(droidCheckResult.getFileFormat())
                .setFileSize(droidCheckResult.getFileSize())
                .setPath(droidCheckResult.getPath())
                .setPUID(droidCheckResult.getPUID())
                .setExtensionMismatch(droidCheckResult.isExtensionMismatch())
                .setType(droidCheckResult.getType())
                .setStatus(droidCheckResult.getStatus())
                .setFormatVersion(droidCheckResult.getFormatVersion())
                .setMimeType(droidCheckResult.getMimeType())
                .setURI(droidCheckResult.getURI())
                .setLastModifiedDate(droidCheckResult.getLastModifiedDate())
                .setChecksumHistory(history);
            try {
                droidCheckHistoryDAO.create(context, dch);
                droidCheckHistoryDAO.save(context, dch);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
            droidCheckHistories.add(dch);
        }
        history.setDroidCheckHistory(droidCheckHistories);
    }

    @Override
    public List<DroidCheckHistory> findBy(Context context, Bitstream bitstream) throws SQLException {
        return droidCheckHistoryDAO.findBy(context, bitstream);
    }

    @Override
    public void deleteByBitstream(Context context, Bitstream bitstream) throws SQLException {
        droidCheckHistoryDAO.deleteByBitstream(context, bitstream);
    }
}
