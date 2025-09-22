/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.deduplication.utils;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.dspace.app.deduplication.model.DuplicateDecisionObjectRest;
import org.dspace.app.deduplication.service.DedupService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.discovery.SearchServiceException;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public interface IDedupUtils {

    boolean matchExist(Context context, UUID itemID, UUID targetItemID, Integer resourceType,
                       String signatureType, Boolean isInWorkflow) throws SQLException, SearchServiceException;

    void verify(Context context, int dedupId, UUID firstId, UUID secondId, int type, boolean toFix, String note,
                boolean check) throws SQLException, AuthorizeException;

    void setDuplicateDecision(Context context, UUID firstId, UUID secondId, Integer type,
                              DuplicateDecisionObjectRest decisionObject)
        throws AuthorizeException, SQLException, SearchServiceException;

    boolean validateDecision(DuplicateDecisionObjectRest decisionObject);

    DedupService getDedupService();

    void setDedupService(DedupService dedupService);

    void commit();

    List<DuplicateItemInfo> getDuplicateByIDandType(Context context, UUID itemID, int typeID,
                                                    boolean isInWorkflow) throws SQLException, SearchServiceException;

    List<DuplicateItemInfo> getDuplicateByIdAndTypeAndSignatureType(Context context, UUID itemID, int typeID,
                                                                    boolean isInWorkflow)
        throws SQLException, SearchServiceException;

    List<DuplicateItemInfo> getAdminDuplicateByIdAndType(Context context, UUID itemID, int typeID)
        throws SQLException, SearchServiceException;
}
