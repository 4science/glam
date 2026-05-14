/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.solr;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.CursorMarkParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * Wrapper around any SolrClient that handles SolrCloud pagination limitations
 * transparently without requiring code changes to existing services.
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 **/
public class SolrCloudClientDecorator extends SolrClient {

    private static Logger log = LogManager.getLogger(SolrCloudClientDecorator.class);

    private final ConfigurationService configurationService = DSpaceServicesFactory
        .getInstance()
        .getConfigurationService();

    private final SolrClient delegate;
    private final String defaultUniqueField;


    public SolrCloudClientDecorator(SolrClient delegate, String defaultUniqueField) {
        this.defaultUniqueField = defaultUniqueField;
        this.delegate = delegate;
    }

    @Override
    public QueryResponse query(SolrParams params) throws SolrServerException, IOException {
        return handleQuery(null, params, SolrRequest.METHOD.GET);
    }

    @Override
    public QueryResponse query(SolrParams params, SolrRequest.METHOD method) throws SolrServerException, IOException {
        return handleQuery(null, params, method);
    }

    @Override
    public QueryResponse query(String collection, SolrParams params) throws SolrServerException, IOException {
        return handleQuery(collection, params, SolrRequest.METHOD.GET);
    }

    @Override
    public QueryResponse query(String collection, SolrParams params, METHOD method)
        throws SolrServerException, IOException {
        return handleQuery(collection, params, method);
    }

    private QueryResponse handleQuery(String collection, SolrParams params, METHOD method)
        throws SolrServerException, IOException {

        ModifiableSolrParams modParams = params instanceof ModifiableSolrParams
            ? (ModifiableSolrParams) params
            : new ModifiableSolrParams(params);

        int requestedStart = modParams.getInt(CommonParams.START, 0);
        int requestedRows = modParams.getInt(CommonParams.ROWS, -1);

        // Determine whether to use cursor-based pagination for large result sets
        // Cursor pagination is more efficient for deep pagination but has specific requirements
        boolean useCursor = shouldUseCursor(requestedStart, requestedRows);

        if (useCursor) {
            return handleCursorBasedQuery(collection, modParams, requestedStart, requestedRows);
        } else {
            // Use standard pagination - delegate directly to underlying SolrClient
            return handleStandardQuery(collection, modParams, method);
        }
    }

    /**
     * Handles standard pagination queries by delegating directly to the underlying SolrClient.
     */
    private QueryResponse handleStandardQuery(String collection, ModifiableSolrParams modParams, METHOD method)
        throws SolrServerException, IOException {
        modParams.add(CommonParams.SORT, defaultUniqueField + " asc");
        if (log.isDebugEnabled()) {
            log.debug("Executing standard Solr query (no cursor) with params: {}", modParams,
                      new Exception("Stack trace"));
        }
        if (collection != null) {
            return delegate.query(collection, modParams, method);
        } else {
            return delegate.query(modParams, method);
        }
    }

    /**
     * Handles cursor-based pagination queries for large result sets.
     *
     * SolrCloud cursor pagination requirements:
     * 1. A unique key field MUST be the first sort criterion for consistent ordering across shards
     * 2. All sort fields must be explicitly defined (no implicit sorting)
     * 3. Uses cursor marks instead of start/rows for pagination
     *
     * This ensures stable, consistent results when paginating through large datasets in SolrCloud.
     */
    private QueryResponse handleCursorBasedQuery(String collection, ModifiableSolrParams modParams,
                                                 int requestedStart, int requestedRows)
        throws SolrServerException, IOException {

        // Store original sort clauses before modification
        String[] originalSortClauses = modParams.getParams(CommonParams.SORT);
        modParams.remove(CommonParams.SORT);

        // SolrCloud cursor pagination REQUIRES the unique key field as the first sort criterion
        // This ensures consistent ordering across distributed shards and prevents result inconsistencies
        modParams.add(CommonParams.SORT, defaultUniqueField + " asc");

        // Add back original sort clauses (except if they already include the unique field)
        boolean hasModifiedSort = false;
        if (originalSortClauses != null) {
            for (String clause : originalSortClauses) {
                if (!clause.startsWith(defaultUniqueField + " ")) {
                    modParams.add(CommonParams.SORT, clause);
                    hasModifiedSort = true;
                }
            }
        }

        // Warn user that sort order has been modified for cursor pagination requirements
        if (hasModifiedSort) {
            log.warn("Using cursor pagination for large result set (start={}, rows={}). " +
                         "Sort order modified to comply with SolrCloud requirements: unique field '{}' added as " +
                         "primary sort. " +
                         "Original sort: [{}] -> Modified sort: [{} asc, {}]",
                     requestedStart, requestedRows, defaultUniqueField,
                     originalSortClauses != null ? String.join(", ", originalSortClauses) : "none",
                     defaultUniqueField,
                     originalSortClauses != null ? String.join(", ", originalSortClauses) : "none");
        }

        // Build SolrQuery with proper sort configuration for cursor pagination
        SolrQuery solrQuery = new SolrQuery();
        String[] finalSortClauses = modParams.getParams(CommonParams.SORT);
        if (finalSortClauses != null) {
            Arrays.stream(finalSortClauses)
                  .map(String::trim)
                  .forEachOrdered(sortClause -> {
                      String[] parts = sortClause.split(" ");
                      if (parts.length == 2) {
                          SolrQuery.ORDER order = parts[1].equalsIgnoreCase("desc")
                              ? SolrQuery.ORDER.desc
                              : SolrQuery.ORDER.asc;
                          solrQuery.addSort(parts[0], order);
                      }
                  });
        }
        solrQuery.add(modParams);

        // Execute cursor-based search and return aggregated results
        PaginatedSearchResult result = cursorBasedSearch(collection, solrQuery, requestedStart, requestedRows);
        return createQueryResponseFromResult(result);
    }


    /**
     * Determines whether cursor-based pagination should be used for a Solr query.
     * <p>
     * Cursor pagination is used for deep paging in SolrCloud, but only under specific conditions:
     * <ul>
     *   <li><b>Case 1:</b> If both {@code start} and {@code rows} are present (explicit paginated call),
     *   cursor <b>should NOT</b> be used.</li>
     *   <li><b>Case 2:</b> If {@code start} is present but {@code rows} is not, cursor <b>should NOT</b> be used,
     *   since the number of rows is limited by the core/collection configuration.</li>
     *   <li><b>Case 3:</b> If both {@code start} and {@code rows} are present, and the requested rows are greater
     *   than the {@code solr.cursor.threshold} configured property, cursor <b>should be used</b> for efficient
     *   deep paging.</li>
     * </ul>
     * <p>
     * In summary, cursor is only used when starting from the beginning (start == 0) and requesting more results than
     * the configured threshold.
     *
     * @param start the starting index for the query (pagination)
     * @param rows  the number of rows requested
     * @return true if cursor pagination should be used, false otherwise
     */

    private boolean shouldUseCursor(int start, int rows) {
        int cursorThreshold = configurationService.getIntProperty("solr.cursor.threshold", 1000);
        // Only use cursor when starting from beginning and fetching many results
        return start == 0 && rows > cursorThreshold;
    }

    /**
     * Performs cursor-based search for large result sets.
     */
    private PaginatedSearchResult cursorBasedSearch(String collection, SolrQuery solrQuery,
                                                    int requestedStart, int requestedRows)
        throws SolrServerException, IOException {

        List<QueryResponse> responses = new ArrayList<>();
        String cursorMark = CursorMarkParams.CURSOR_MARK_START;
        SolrQuery query = solrQuery.getCopy();

        int pageSize = configurationService.getIntProperty("solr.cursor.page.size", 1000);
        query.setRows(pageSize);
        query.setStart(0);

        query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);

        int totalFetched = 0;
        boolean done = false;
        long totalQTime = 0;

        if (log.isDebugEnabled()) {
            log.debug("Executing cursor-based Solr query {} ", solrQuery, new Exception("Stack trace"));
        }

        while (!done && totalFetched < requestedRows) {
            if (log.isDebugEnabled()) {
                log.debug("Executing cursor-based Solr query with cursorMark='{}', rows={}, totalFetched={}",
                          cursorMark, pageSize, totalFetched);
            }
            QueryResponse response = delegate.query(collection, query, SolrRequest.METHOD.POST);
            responses.add(response);
            totalQTime += response.getQTime();

            SolrDocumentList batch = response.getResults();
            totalFetched += batch.size();

            String nextCursorMark = response.getNextCursorMark();
            boolean pageAtEnd = cursorMark.equals(nextCursorMark);
            boolean limitReached = totalFetched >= requestedRows;

            if (pageAtEnd || limitReached) {
                done = true;
            } else {
                cursorMark = nextCursorMark;
                query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
            }
        }

        return new PaginatedSearchResult(responses, requestedStart, requestedRows, totalQTime);
    }

    private QueryResponse createQueryResponseFromResult(PaginatedSearchResult result) {
        NamedList<Object> response = new NamedList<>();

        // Response header
        NamedList<Object> responseHeader = new NamedList<>();
        responseHeader.add("status", 0);
        responseHeader.add("QTime", (int) result.getTotalQTime());
        response.add("responseHeader", responseHeader);

        // Main results
        response.add("response", result.getAggregatedDocuments());

        // Add facets if present (from first response since they're the same across pages)
        if (!result.getResponses().isEmpty()) {
            QueryResponse firstResponse = result.getResponses().get(0);
            addFacetsToResponse(response, firstResponse);
            addHighlightingToResponse(response, result.getResponses());
            addSpellCheckToResponse(response, firstResponse);
            addStatsToResponse(response, firstResponse);
        }

        return new QueryResponse(response, delegate);
    }

    private void addFacetsToResponse(NamedList<Object> response, QueryResponse firstResponse) {
        if (firstResponse.getFacetFields() != null && !firstResponse.getFacetFields().isEmpty()) {
            NamedList<Object> facetCounts = new NamedList<>();
            NamedList<NamedList<Object>> facetFields = new NamedList<>();

            for (FacetField field : firstResponse.getFacetFields()) {
                NamedList<Object> fieldCounts = new NamedList<>();
                if (field.getValues() != null) {
                    for (FacetField.Count count : field.getValues()) {
                        fieldCounts.add(count.getName(), count.getCount());
                    }
                }
                facetFields.add(field.getName(), fieldCounts);
            }
            facetCounts.add("facet_fields", facetFields);

            // Add facet queries
            if (firstResponse.getFacetQuery() != null && !firstResponse.getFacetQuery().isEmpty()) {
                facetCounts.add("facet_queries", new NamedList<>(firstResponse.getFacetQuery()));
            }

            response.add("facet_counts", facetCounts);
        }
    }

    private void addHighlightingToResponse(NamedList<Object> response, List<QueryResponse> responses) {
        Map<String, Map<String, List<String>>> allHighlighting = new LinkedHashMap<>();

        for (QueryResponse queryResponse : responses) {
            if (queryResponse.getHighlighting() != null) {
                allHighlighting.putAll(queryResponse.getHighlighting());
            }
        }

        if (!allHighlighting.isEmpty()) {
            response.add("highlighting", allHighlighting);
        }
    }

    private void addSpellCheckToResponse(NamedList<Object> response, QueryResponse firstResponse) {
        if (firstResponse.getSpellCheckResponse() != null) {
            response.add("spellcheck", firstResponse.getSpellCheckResponse());
        }
    }

    private void addStatsToResponse(NamedList<Object> response, QueryResponse firstResponse) {
        if (firstResponse.getFieldStatsInfo() != null && !firstResponse.getFieldStatsInfo().isEmpty()) {
            log.warn("Stats component used with cursor pagination - some stats data may not be preserved");

            NamedList<Object> stats = new NamedList<>();
            NamedList<Object> statsFields = new NamedList<>();

            firstResponse.getFieldStatsInfo().forEach((fieldName, statsInfo) -> {
                NamedList<Object> fieldStats = new NamedList<>();
                fieldStats.add("count", statsInfo.getCount());
                fieldStats.add("min", statsInfo.getMin());
                fieldStats.add("max", statsInfo.getMax());
                fieldStats.add("sum", statsInfo.getSum());
                fieldStats.add("mean", statsInfo.getMean());
                statsFields.add(fieldName, fieldStats);
            });

            stats.add("stats_fields", statsFields);
            response.add("stats", stats);
        }
    }

    // Delegate all other methods to the wrapped client
    @Override
    public UpdateResponse add(SolrInputDocument doc) throws SolrServerException, IOException {
        return delegate.add(doc);
    }

    @Override
    public UpdateResponse add(String collection, SolrInputDocument doc) throws SolrServerException, IOException {
        return delegate.add(collection, doc);
    }

    @Override
    public UpdateResponse add(Collection<SolrInputDocument> docs) throws SolrServerException, IOException {
        return delegate.add(docs);
    }

    @Override
    public UpdateResponse add(String collection, Collection<SolrInputDocument> docs)
        throws SolrServerException, IOException {
        return delegate.add(collection, docs);
    }

    @Override
    public UpdateResponse addBean(Object obj) throws IOException, SolrServerException {
        return delegate.addBean(obj);
    }

    @Override
    public UpdateResponse addBean(String collection, Object obj) throws IOException, SolrServerException {
        return delegate.addBean(collection, obj);
    }

    @Override
    public UpdateResponse addBeans(Collection<?> beans) throws SolrServerException, IOException {
        return delegate.addBeans(beans);
    }

    @Override
    public UpdateResponse addBeans(String collection, Collection<?> beans) throws SolrServerException, IOException {
        return delegate.addBeans(collection, beans);
    }

    @Override
    public UpdateResponse commit() throws SolrServerException, IOException {
        return delegate.commit();
    }

    @Override
    public UpdateResponse commit(String collection) throws SolrServerException, IOException {
        return delegate.commit(collection);
    }

    @Override
    public UpdateResponse optimize() throws SolrServerException, IOException {
        return delegate.optimize();
    }

    @Override
    public UpdateResponse optimize(String collection) throws SolrServerException, IOException {
        return delegate.optimize(collection);
    }

    @Override
    public UpdateResponse rollback() throws SolrServerException, IOException {
        return delegate.rollback();
    }

    @Override
    public UpdateResponse rollback(String collection) throws SolrServerException, IOException {
        return delegate.rollback(collection);
    }

    @Override
    public UpdateResponse deleteById(String id) throws SolrServerException, IOException {
        return delegate.deleteById(id);
    }

    @Override
    public UpdateResponse deleteById(String collection, String id) throws SolrServerException, IOException {
        return delegate.deleteById(collection, id);
    }

    @Override
    public UpdateResponse deleteById(List<String> ids) throws SolrServerException, IOException {
        return delegate.deleteById(ids);
    }

    @Override
    public UpdateResponse deleteById(String collection, List<String> ids) throws SolrServerException, IOException {
        return delegate.deleteById(collection, ids);
    }

    @Override
    public UpdateResponse deleteByQuery(String query) throws SolrServerException, IOException {
        return delegate.deleteByQuery(query);
    }

    @Override
    public UpdateResponse deleteByQuery(String collection, String query) throws SolrServerException, IOException {
        return delegate.deleteByQuery(collection, query);
    }

    @Override
    public SolrPingResponse ping() throws SolrServerException, IOException {
        return delegate.ping();
    }

    @Override
    public NamedList<Object> request(SolrRequest request, String collection) throws SolrServerException, IOException {
        return delegate.request(request, collection);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    /**
     * Internal class to hold paginated search results
     */
    private static class PaginatedSearchResult {
        private final List<QueryResponse> responses;
        private final int requestedStart;
        private final int requestedRows;
        private final long totalQTime;
        private final SolrDocumentList aggregatedDocuments;

        public PaginatedSearchResult(List<QueryResponse> responses, int requestedStart,
                                     int requestedRows, long totalQTime) {
            this.responses = responses;
            this.requestedStart = requestedStart;
            this.requestedRows = requestedRows;
            this.totalQTime = totalQTime;
            this.aggregatedDocuments = aggregateDocuments();
        }

        private SolrDocumentList aggregateDocuments() {
            SolrDocumentList aggregated = new SolrDocumentList();

            if (!responses.isEmpty()) {
                // Set numFound from first response
                aggregated.setNumFound(responses.get(0).getResults().getNumFound());
                aggregated.setStart(requestedStart);

                // Add all documents from all responses
                for (QueryResponse response : responses) {
                    if (response.getResults() != null) {
                        aggregated.addAll(response.getResults());
                    }
                }
            }

            return aggregated;
        }

        public List<QueryResponse> getResponses() {
            return responses;
        }

        public SolrDocumentList getAggregatedDocuments() {
            return aggregatedDocuments;
        }

        public long getTotalQTime() {
            return totalQTime;
        }
    }
}
