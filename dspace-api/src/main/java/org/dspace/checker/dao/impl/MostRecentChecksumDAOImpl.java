/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.dao.impl;

import java.sql.SQLException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.dspace.checker.ChecksumHistory;
import org.dspace.checker.ChecksumHistory_;
import org.dspace.checker.ChecksumResult;
import org.dspace.checker.ChecksumResultCode;
import org.dspace.checker.ChecksumResult_;
import org.dspace.checker.MostRecentChecksum;
import org.dspace.checker.MostRecentChecksum_;
import org.dspace.checker.dao.MostRecentChecksumDAO;
import org.dspace.content.Bitstream;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;

/**
 * Hibernate implementation of the Database Access Object interface class for the MostRecentChecksum object.
 * This class is responsible for all database calls for the MostRecentChecksum object and is autowired by spring
 * This class should never be accessed directly.
 *
 * @author kevinvandevelde at atmire.com
 */
public class MostRecentChecksumDAOImpl extends AbstractHibernateDAO<MostRecentChecksum>
    implements MostRecentChecksumDAO {

    private static List<Order> getDefaultOrder(HibernateQuery hq) {
        return List.of(
            hq.criteriaBuilder.asc(hq.root.get(MostRecentChecksum_.processEndDate)),
            hq.criteriaBuilder.asc(hq.root.get(MostRecentChecksum_.bitstream))
        );
    }

    protected MostRecentChecksumDAOImpl() {
        super();
    }


    @Override
    public List<MostRecentChecksum> findByNotProcessedInDateRange(Context context, Date startDate, Date endDate)
        throws SQLException {

        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery<MostRecentChecksum> criteriaQuery = getCriteriaQuery(criteriaBuilder, MostRecentChecksum.class);
        Root<MostRecentChecksum> mostRecentChecksumRoot = criteriaQuery.from(MostRecentChecksum.class);
        criteriaQuery.select(mostRecentChecksumRoot);
        criteriaQuery.where(criteriaBuilder.and(
            criteriaBuilder.equal(mostRecentChecksumRoot.get(MostRecentChecksum_.toBeProcessed), false),
            criteriaBuilder
                .lessThanOrEqualTo(mostRecentChecksumRoot.get(MostRecentChecksum_.processStartDate), startDate),
            criteriaBuilder.greaterThan(mostRecentChecksumRoot.get(MostRecentChecksum_.processStartDate), endDate)
                            )
        );
        List<Order> orderList = new LinkedList<>();
        orderList.add(criteriaBuilder.asc(mostRecentChecksumRoot.get(MostRecentChecksum_.bitstream)));
        criteriaQuery.orderBy(orderList);
        return list(context, criteriaQuery, false, MostRecentChecksum.class, -1, -1);
    }


    @Override
    public MostRecentChecksum findByBitstream(Context context, Bitstream bitstream) throws SQLException {
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery<MostRecentChecksum> criteriaQuery = getCriteriaQuery(criteriaBuilder, MostRecentChecksum.class);
        Root<MostRecentChecksum> mostRecentChecksumRoot = criteriaQuery.from(MostRecentChecksum.class);
        mostRecentChecksumRoot.fetch(MostRecentChecksum_.BITSTREAM, JoinType.INNER);
        criteriaQuery.select(mostRecentChecksumRoot);
        criteriaQuery
            .where(criteriaBuilder.equal(mostRecentChecksumRoot.get(MostRecentChecksum_.bitstream), bitstream));
        return singleResult(context, criteriaQuery);
    }


    @Override
    public List<MostRecentChecksum> findByResultTypeInDateRange(Context context, Date startDate, Date endDate,
                                                                ChecksumResultCode resultCode) throws SQLException {
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery<MostRecentChecksum> criteriaQuery = getCriteriaQuery(criteriaBuilder, MostRecentChecksum.class);
        Root<MostRecentChecksum> mostRecentChecksumRoot = criteriaQuery.from(MostRecentChecksum.class);
        Join<MostRecentChecksum, ChecksumResult> mostRecentResult =
            mostRecentChecksumRoot.join(MostRecentChecksum_.checksumResult);

        criteriaQuery.select(mostRecentChecksumRoot);
        criteriaQuery.where(criteriaBuilder.and(
            criteriaBuilder.equal(mostRecentResult.get(ChecksumResult_.resultCode), resultCode),
            criteriaBuilder.lessThanOrEqualTo(
                mostRecentChecksumRoot.get(MostRecentChecksum_.processStartDate), endDate),
            criteriaBuilder.greaterThan(mostRecentChecksumRoot.get(MostRecentChecksum_.processStartDate), startDate)
                            )
        );
        List<Order> orderList = new LinkedList<>();
        orderList.add(criteriaBuilder.asc(mostRecentChecksumRoot.get(MostRecentChecksum_.bitstream)));
        criteriaQuery.orderBy(orderList);
        return list(context, criteriaQuery, false, MostRecentChecksum.class, -1, -1);

    }

    @Override
    public void deleteByBitstream(Context context, Bitstream bitstream) throws SQLException {
        String hql = "delete from MostRecentChecksum WHERE bitstream=:bitstream";
        Query query = createQuery(context, hql);
        query.setParameter("bitstream", bitstream);
        query.executeUpdate();
    }

    @Override
    public MostRecentChecksum getOldestRecord(Context context) throws SQLException {
        HibernateQuery hq = buildHq(context);
        return singleResult(
            context,
            hq.criteriaQuery
                .where(hq.criteriaBuilder.equal(hq.root.get(MostRecentChecksum_.toBeProcessed), true))
                .orderBy(getDefaultOrder(hq))
        );
    }

    @Override
    public MostRecentChecksum getOldestRecord(Context context, Date lessThanDate) throws SQLException {
        HibernateQuery hq = buildHq(context);
        return singleResult(
            context,
            hq.criteriaQuery
                .where(
                    hq.criteriaBuilder.and(
                        hq.criteriaBuilder.equal(hq.root.get(MostRecentChecksum_.toBeProcessed), true),
                        hq.criteriaBuilder.lessThan(hq.root.get(MostRecentChecksum_.processStartDate), lessThanDate)
                    )
                )
                .orderBy(getDefaultOrder(hq))
        );
    }

    @Override
    public Iterator<MostRecentChecksum> findAll(Context context, Date lessThanDate, int offset, int limit)
        throws SQLException {
        HibernateQuery hq = buildHq(context);
        return iterate(
            getHibernateSession(context)
                .createQuery(
                    hq.criteriaQuery
                        .where(
                            hq.criteriaBuilder.and(
                                hq.criteriaBuilder.equal(hq.root.get(MostRecentChecksum_.toBeProcessed), true),
                                hq.criteriaBuilder.lessThan(hq.root.get(MostRecentChecksum_.processStartDate),
                                                            lessThanDate)
                            )
                        )
                        .orderBy(getDefaultOrder(hq))
                )
                .setFirstResult(offset)
                .setMaxResults(limit)
        );
    }

    private HibernateQuery buildHq(Context context) throws SQLException {
        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery<MostRecentChecksum> criteriaQuery = getCriteriaQuery(criteriaBuilder, MostRecentChecksum.class);
        Root<MostRecentChecksum> root = criteriaQuery.from(MostRecentChecksum.class);
        // JOIN FETCH Bitstream
        root.fetch(MostRecentChecksum_.BITSTREAM, JoinType.INNER);
        criteriaQuery.select(root);
        return new HibernateQuery(criteriaBuilder, criteriaQuery, root);
    }

    private static class HibernateQuery {
        public final CriteriaBuilder criteriaBuilder;
        public final CriteriaQuery<MostRecentChecksum> criteriaQuery;
        public final Root<MostRecentChecksum> root;

        public HibernateQuery(
            CriteriaBuilder criteriaBuilder,
            CriteriaQuery<MostRecentChecksum> criteriaQuery,
            Root<MostRecentChecksum> root
        ) {
            this.criteriaBuilder = criteriaBuilder;
            this.criteriaQuery = criteriaQuery;
            this.root = root;
        }
    }


    @Override
    public List<MostRecentChecksum> findNotInHistory(Context context) throws SQLException {

        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery<MostRecentChecksum> criteriaQuery = getCriteriaQuery(criteriaBuilder, MostRecentChecksum.class);
        Root<MostRecentChecksum> checksumRoot = criteriaQuery.from(MostRecentChecksum.class);

        Subquery<Bitstream> subQuery = criteriaQuery.subquery(Bitstream.class);
        Root<ChecksumHistory> historyRoot = subQuery.from(ChecksumHistory.class);
        subQuery.select(historyRoot.get(ChecksumHistory_.bitstream));

        criteriaQuery.where(
            criteriaBuilder.not(checksumRoot.get(MostRecentChecksum_.bitstream).in(subQuery)));

        return list(context, criteriaQuery, false, MostRecentChecksum.class, -1, -1);
    }
}
