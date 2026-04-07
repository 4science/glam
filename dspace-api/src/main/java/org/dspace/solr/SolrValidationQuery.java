/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.solr;

import org.apache.solr.client.solrj.SolrQuery;

/**
 * Represents a configurable Solr validation query used to test Solr core connectivity.
 * Each validation query can be customized for different Solr cores and use cases.
 */
public class SolrValidationQuery {

    private final String query;
    private final String[] filterQueries;
    private final String[] fields;
    private final int rows;

    public SolrValidationQuery(String query, String[] filterQueries, String[] fields, int rows) {
        this.query = query;
        this.filterQueries = filterQueries;
        this.fields = fields;
        this.rows = rows;
    }

    /**
     * Creates a simple validation query that returns no documents but tests connectivity.
     */
    public static SolrValidationQuery createSimplePingQuery() {
        return new SolrValidationQuery("*:*", null, null, 0);
    }

    /**
     * Converts this validation query to a SolrQuery object.
     */
    public SolrQuery toSolrQuery() {
        SolrQuery solrQuery = new SolrQuery(query);

        if (filterQueries != null && filterQueries.length > 0) {
            solrQuery.addFilterQuery(filterQueries);
        }

        if (fields != null && fields.length > 0) {
            solrQuery.setFields(fields);
        }

        solrQuery.setRows(rows);

        return solrQuery;
    }

    public String getQuery() {
        return query;
    }

    public String[] getFilterQueries() {
        return filterQueries;
    }

    public String[] getFields() {
        return fields;
    }

    public int getRows() {
        return rows;
    }
}
