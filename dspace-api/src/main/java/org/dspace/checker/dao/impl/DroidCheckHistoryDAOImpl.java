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
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;

import org.dspace.checker.ChecksumHistory;
import org.dspace.checker.ChecksumHistory_;
import org.dspace.checker.DroidCheckHistory;
import org.dspace.checker.DroidCheckHistory_;
import org.dspace.checker.dao.DroidCheckHistoryDAO;
import org.dspace.content.Bitstream;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DroidCheckHistoryDAOImpl extends AbstractHibernateDAO<DroidCheckHistory> implements DroidCheckHistoryDAO {
    @Override
    public List<DroidCheckHistory> findBy(Context context, Bitstream bitstream) throws SQLException {
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery criteriaQuery = getCriteriaQuery(criteriaBuilder, DroidCheckHistory.class);
        Root<DroidCheckHistory> droidRoot = criteriaQuery.from(DroidCheckHistory.class);
        Join<DroidCheckHistory, ChecksumHistory> join =
            droidRoot.join(DroidCheckHistory_.CHECKSUM_HISTORY, JoinType.INNER);
        criteriaQuery.select(droidRoot);
        criteriaQuery.where(criteriaBuilder.equal(join.get(ChecksumHistory_.BITSTREAM), bitstream));
        return list(context, criteriaQuery, false, DroidCheckHistory.class, -1, -1);
    }

    @Override
    public void deleteByBitstream(Context context, Bitstream bitstream) throws SQLException {
        String hql =
            "delete from DroidCheckHistory " +
            "WHERE id in ( " +
            " SELECT dch.id " +
            " FROM DroidCheckHistory dch " +
            " JOIN dch.checksumHistory ch " +
            " WHERE ch.bitstream = :bitstream " +
            ")";
        Query query = createQuery(context, hql);
        query.setParameter("bitstream", bitstream);
        query.executeUpdate();
    }
}
