/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.service;

import java.sql.SQLException;

import org.dspace.checker.DroidCheckStatus;
import org.dspace.checker.DroidResultCode;
import org.dspace.core.Context;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public interface DroidCheckStatusService {

    DroidCheckStatus findBy(Context context, DroidResultCode code) throws SQLException;

}
