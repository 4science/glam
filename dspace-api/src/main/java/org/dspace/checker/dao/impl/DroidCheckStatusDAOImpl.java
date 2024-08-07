/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.dao.impl;

import java.sql.SQLException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.dspace.checker.ChecksumResult;
import org.dspace.checker.DroidCheckStatus;
import org.dspace.checker.DroidCheckStatus_;
import org.dspace.checker.DroidResultCode;
import org.dspace.checker.dao.DroidCheckStatusDAO;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DroidCheckStatusDAOImpl extends AbstractHibernateDAO<DroidCheckStatus> implements DroidCheckStatusDAO {

    @Override
    public DroidCheckStatus findBy(Context context, DroidResultCode code) throws SQLException {
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery criteriaQuery = getCriteriaQuery(criteriaBuilder, DroidCheckStatus.class);
        Root<ChecksumResult> checksumResultRoot = criteriaQuery.from(DroidCheckStatus.class);
        criteriaQuery.select(checksumResultRoot);
        criteriaQuery.where(
            criteriaBuilder.equal(
                checksumResultRoot.get(DroidCheckStatus_.STATUS_CODE), code)
        );
        return uniqueResult(context, criteriaQuery, false, DroidCheckStatus.class);
    }
}
