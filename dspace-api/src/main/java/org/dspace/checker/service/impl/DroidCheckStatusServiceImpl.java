/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.service.impl;

import java.sql.SQLException;

import org.dspace.checker.DroidCheckStatus;
import org.dspace.checker.DroidResultCode;
import org.dspace.checker.dao.DroidCheckStatusDAO;
import org.dspace.checker.service.DroidCheckStatusService;
import org.dspace.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DroidCheckStatusServiceImpl implements DroidCheckStatusService {

    @Autowired
    private DroidCheckStatusDAO droidCheckStatusDAO;

    @Override
    public DroidCheckStatus findBy(Context context, DroidResultCode code) throws SQLException {
        return this.droidCheckStatusDAO.findBy(context, code);
    }
}
