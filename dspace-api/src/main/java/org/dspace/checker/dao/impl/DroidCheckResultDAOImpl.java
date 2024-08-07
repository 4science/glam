/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.dao.impl;

import java.sql.SQLException;
import java.util.List;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;

import org.dspace.checker.DroidCheckResult;
import org.dspace.checker.DroidCheckResult_;
import org.dspace.checker.MostRecentChecksum;
import org.dspace.checker.MostRecentChecksum_;
import org.dspace.checker.dao.DroidCheckResultDAO;
import org.dspace.content.Bitstream;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DroidCheckResultDAOImpl extends AbstractHibernateDAO<DroidCheckResult> implements DroidCheckResultDAO {

    @Override
    public List<DroidCheckResult> findBy(Context context, Bitstream bitstream) throws SQLException {
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery criteriaQuery = getCriteriaQuery(criteriaBuilder, DroidCheckResult.class);
        Root<DroidCheckResult> droidRoot = criteriaQuery.from(DroidCheckResult.class);
        Join<DroidCheckResult, MostRecentChecksum> join =
            droidRoot.join(DroidCheckResult_.mostRecentChecksum, JoinType.INNER);
        criteriaQuery.select(droidRoot);
        criteriaQuery.where(criteriaBuilder.equal(join.get(MostRecentChecksum_.BITSTREAM), bitstream));
        return list(context, criteriaQuery, false, DroidCheckResult.class, -1, -1);
    }
}
