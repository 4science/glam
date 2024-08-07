/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.dao;

import java.sql.SQLException;
import java.util.List;

import org.dspace.checker.DroidCheckResult;
import org.dspace.content.Bitstream;
import org.dspace.core.Context;
import org.dspace.core.GenericDAO;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public interface DroidCheckResultDAO extends GenericDAO<DroidCheckResult> {

    List<DroidCheckResult> findBy(Context context, Bitstream bitstream) throws SQLException;

}
