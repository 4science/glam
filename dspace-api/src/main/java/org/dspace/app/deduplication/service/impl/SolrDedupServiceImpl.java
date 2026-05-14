/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.deduplication.service.impl;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.dspace.app.deduplication.model.DuplicateDecisionObjectRest;
import org.dspace.app.deduplication.model.DuplicateDecisionType;
import org.dspace.app.deduplication.model.DuplicateDecisionValue;
import org.dspace.app.deduplication.service.DedupService;
import org.dspace.app.deduplication.service.SearchDeduplication;
import org.dspace.app.deduplication.service.SolrDedupServiceIndexPlugin;
import org.dspace.app.deduplication.utils.DuplicateItemInfo;
import org.dspace.app.deduplication.utils.IDedupUtils;
import org.dspace.app.deduplication.utils.Signature;
import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.exception.SQLRuntimeException;
import org.dspace.deduplication.Deduplication;
import org.dspace.deduplication.service.DeduplicationService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.solr.SolrClientFactory;
import org.dspace.utils.DSpace;
import org.dspace.versioning.service.VersioningService;

public class SolrDedupServiceImpl implements DedupService {

    private static final Logger log = LogManager.getLogger(SolrDedupServiceImpl.class);

    private static final String DEDUPLICATION_CORE_PROPERTY = "deduplication.search.server";

    public static final String LAST_INDEXED_FIELD = "SolrIndexer.lastIndexed";
    public static final String UNIQUE_ID_FIELD = "dedup.uniqueid";
    public static final String RESOURCE_RESOURCETYPE_FIELD = "dedup.resourcetype";
    public static final String RESOURCE_SIGNATURETYPE_FIELD = "dedup.signaturetype";
    public static final String RESOURCE_SIGNATURE_FIELD = "dedup.signature";
    public static final String RESOURCE_ID_FIELD = "dedup.id";
    /***
     * Identify the couple of UUID.
     */
    public static final String RESOURCE_IDS_FIELD = "dedup.ids";
    /**
     * Identify the deduplication status
     *
     * @See DeduplicationFlag
     */
    public static final String RESOURCE_FLAG_FIELD = "dedup.flag";
    public static final String RESOURCE_NOTE_FIELD = "dedup.note";
    public static final String RESOURCE_WITHDRAWN_FIELD = "dedup.withdrawn";
    public static final String SUBQUERY_STORED_DECISION = UNIQUE_ID_FIELD + ":{0}-*_{1}" + " AND " + "-("
        + UNIQUE_ID_FIELD + "{0}-match)";
    public static final String QUERY_REMOVE = RESOURCE_IDS_FIELD + ":{0}" + " AND " + RESOURCE_RESOURCETYPE_FIELD
        + ":{1}";

    private DSpace dspace = new DSpace();

    @Inject
    protected ItemService itemService;
    @Inject
    protected ConfigurationService configurationService;
    @Inject
    protected VersioningService versioningService;
    @Inject
    protected IDedupUtils dedupUtils;
    @Inject
    private DeduplicationService deduplicationService;
    @Inject
    @Named("dedupSolrClientFactory")
    private SolrClientFactory solrClientFactory;

    // Helper for join subqueries
    private static String buildJoinSubquery(String fromField, String toField, String flagValue, String fromIndex) {
        boolean isSolrCloud = DSpaceServicesFactory.getInstance()
                                                   .getConfigurationService()
                                                   .getBooleanProperty("solr.cloud.enabled", false);
        if (isSolrCloud) {
            return String.format("{!join method=\"crossCollection\" fromIndex=\"%s\" from=\"%s\" to=\"%s\"}%s:%s",
                                 fromIndex, fromField, toField, RESOURCE_FLAG_FIELD, flagValue);
        } else {
            return String.format("{!join from=%s to=%s}%s:%s", fromField, toField, RESOURCE_FLAG_FIELD, flagValue);
        }
    }

    private static String getFromIndex() {
        String coreUrl = DSpaceServicesFactory.getInstance()
                                              .getConfigurationService()
                                              .getProperty(DEDUPLICATION_CORE_PROPERTY);
        try {
            return Path.of(new URL(coreUrl).getPath())
                       .getFileName()
                       .toString();
        } catch (MalformedURLException ex) {
            log.warn("Unable to extract core name from URI '{}':  {}",
                     coreUrl, ex.getMessage());
        }
        return null;
    }

    // Subquery methods
    public static String getSubqueryNotInRejected() {
        return "-(" + buildJoinSubquery(RESOURCE_ID_FIELD, RESOURCE_ID_FIELD, "reject_admin", getFromIndex())
            + ")";
    }

    public static String getSubqueryNotInRejectedOrVerify() {
        return "-(" + buildJoinSubquery(RESOURCE_ID_FIELD, RESOURCE_ID_FIELD, "verify*", getFromIndex())
            + ") OR -(" + buildJoinSubquery(RESOURCE_ID_FIELD, RESOURCE_ID_FIELD, "reject*", getFromIndex())
            + ")";
    }

    public static String getSubqueryWFMatchOrRejectedOrVerify() {
        return "(" + buildJoinSubquery(RESOURCE_ID_FIELD, RESOURCE_ID_FIELD, "verify_wf", getFromIndex())
            + " OR " + buildJoinSubquery(RESOURCE_ID_FIELD, RESOURCE_ID_FIELD, "reject_wf", getFromIndex())
            + " OR dedup.flag:match)";
    }

    public static String getSubqueryWSMatchOrRejectedOrVerify() {
        return "(" + buildJoinSubquery(RESOURCE_ID_FIELD, RESOURCE_ID_FIELD, "verify_ws", getFromIndex())
            + " OR " + buildJoinSubquery(RESOURCE_ID_FIELD, RESOURCE_ID_FIELD, "reject_ws", getFromIndex())
            + " OR dedup.flag:match)";
    }

    public static String getSubqueryNotInRejectedOrVerifyWF() {
        return "-(" + buildJoinSubquery(RESOURCE_ID_FIELD, RESOURCE_ID_FIELD, "verify_wf", getFromIndex())
            + ") OR -(" + buildJoinSubquery(RESOURCE_ID_FIELD, RESOURCE_ID_FIELD, "reject_wf", getFromIndex())
            + ") OR -(" + buildJoinSubquery(RESOURCE_ID_FIELD, RESOURCE_ID_FIELD, "reject_admin", getFromIndex())
            + ")";
    }

    public SolrClient getSolr() {
        return solrClientFactory
            .getClient(DEDUPLICATION_CORE_PROPERTY)
            .orElseThrow(() -> new RuntimeException("Unable to get Solr client for dedup core"));
    }

    @Override
    public void indexContent(Context ctx, Item iu, boolean force) throws SearchServiceException {

        Map<String, List<String>> tmpMapFilter = new HashMap<String, List<String>>();
        List<String> tmpFilter = new ArrayList<String>();

        fillSignature(ctx, iu, tmpMapFilter, tmpFilter);

        if (tmpFilter.isEmpty()) {
            return;
        }

        // the FAKE identifier
        String dedupID = iu.getID() + "-" + iu.getID();

        // retrieve all search plugin to build search document in the same index
        SearchDeduplication searchSignature = dspace.getServiceManager()
                .getServiceByName("item".toUpperCase() + "SearchDeduplication", SearchDeduplication.class);

        // build the dedup reject in the dedup index core
        buildFromDedupReject(ctx, iu, tmpMapFilter, tmpFilter, searchSignature);

        // clean FAKE documents related to this identifier
        removeFake(dedupID, iu.getType());

        // build the FAKE document
        build(ctx, iu.getID(), iu.getID(), DeduplicationFlag.FAKE, tmpMapFilter, searchSignature, null);

        // remove previous potential match
        removeMatch(iu.getID(), iu.getType());

        // build the new ones
        buildPotentialMatch(ctx, iu, tmpMapFilter, tmpFilter, searchSignature);

    }

    private void fillSignature(Context ctx, DSpaceObject iu, Map<String, List<String>> tmpMapFilter,
            List<String> tmpFilter) {
        // get all algorithms to build signature
        List<Signature> signAlgo = dspace.getServiceManager().getServicesByType(Signature.class);
        for (Signature algo : signAlgo) {
            if (iu.getType() == algo.getResourceTypeID()) {
                List<String> signatures = algo.getSignature(iu, ctx);
                for (String signature : signatures) {
                    if (StringUtils.isNotEmpty(signature)) {
                        String key = algo.getSignatureType() + "_signature";
                        if (tmpMapFilter.containsKey(key)) {
                            List<String> obj = tmpMapFilter.get(key);
                            obj.add(signature);
                            tmpMapFilter.put(key, obj);
                        } else {
                            List<String> obj = new ArrayList<String>();
                            obj.add(signature);
                            tmpMapFilter.put(key, obj);
                        }
                    }
                }

                List<String> plainSignatures = algo.getPlainSignature(iu, ctx);
                for (String signature : plainSignatures) {
                    if (StringUtils.isNotEmpty(signature)) {
                        String key = "plain_" + algo.getSignatureType() + "_signature";
                        if (tmpMapFilter.containsKey(key)) {
                            List<String> obj = tmpMapFilter.get(key);
                            obj.add(signature);
                            tmpMapFilter.put(key, obj);
                        } else {
                            List<String> obj = new ArrayList<String>();
                            obj.add(signature);
                            tmpMapFilter.put(key, obj);
                        }
                    }
                }
            }
        }

        String result = "";
        int index = 0;
        for (String tmpF : tmpMapFilter.keySet()) {
            if (index > 0) {
                result += " OR ";
            }

            result += tmpF + ":(";
            int jindex = 0;
            for (String s : tmpMapFilter.get(tmpF)) {
                if (jindex > 0) {
                    result += " OR ";
                }
                result += s;
                jindex++;
            }
            result += ")";
            index++;
        }

        if (StringUtils.isNotBlank(result)) {
            tmpFilter.add(result);
        }
    }

    private void buildPotentialMatch(Context ctx, Item iu, Map<String, List<String>> tmpMapFilter,
            List<String> tmpFilter, SearchDeduplication searchSignature) throws SearchServiceException {
        tmpFilter.add("+" + RESOURCE_FLAG_FIELD + ":" + DeduplicationFlag.FAKE.getDescription());
        // select all fake not in reject and build the potential match
        String[] tmpArrayFilter = new String[tmpFilter.size()];
        QueryResponse response = find("*:*", tmpFilter.toArray(tmpArrayFilter));
        SolrDocumentList list = response.getResults();
        external: for (SolrDocument resultDoc : list) {

            // build the MATCH identifier
            Collection<Object> matchIds = (Collection<Object>) resultDoc.getFieldValues(RESOURCE_IDS_FIELD);
            UUID matchId = iu.getID();

            internal: for (Object matchIdObj : matchIds) {
                try {
                    matchId = UUID.fromString((String) matchIdObj);

                    if (!iu.getID().equals(matchId)) {
                        break internal;
                    }
                } catch (IllegalArgumentException ie) {
                    log.error("Match ids: " + matchId + ". Id " + matchId + " is not an UUID");
                }
            }

            if (matchId.equals(iu.getID()) || areDifferentVersionsOfSameItem(ctx, iu, matchId)
                || isNotLastVersion(ctx, matchId)) {
                continue external;
            }

            Map<String, List<String>> tmp = new HashMap<String, List<String>>();

            for (String field : resultDoc.getFieldNames()) {
                List<String> valueResult = new ArrayList<String>();
                if (field.endsWith("_signature")) {

                    List<String> valueCurrentSignature = tmpMapFilter.get(field);
                    Collection<Object> valuesSignature = (Collection<Object>) resultDoc.getFieldValues(field);
                    if (valueCurrentSignature != null && !valueCurrentSignature.isEmpty()) {
                        for (Object valSign : valuesSignature) {
                            if (valueCurrentSignature.contains((String) valSign)) {
                                valueResult.add((String) valSign);
                            }
                        }
                    }
                }
                if (!valueResult.isEmpty()) {
                    tmp.put(field, valueResult);
                }
            }

            build(ctx, iu.getID(), matchId, DeduplicationFlag.MATCH, tmp, searchSignature, null);

        }
    }

    private boolean isNotLastVersion(Context context, UUID itemId) {
        try {
            Item item = itemService.find(context, itemId);
            if (item == null) {
                return true;
            }
            return !itemService.isLatestVersion(context, item);
        } catch (SQLException e) {
            throw new SQLRuntimeException(e);
        }
    }

    private void removeFake(String dedupID, Integer type) throws SearchServiceException {
        // remove all FAKE related this deduplication item
        String queryDeleteFake = RESOURCE_RESOURCETYPE_FIELD + ":" + type + " AND " + RESOURCE_FLAG_FIELD + ":"
                + DeduplicationFlag.FAKE.description + " AND " + RESOURCE_ID_FIELD + ":\"" + dedupID + "\"";
        delete(queryDeleteFake);
    }

    private void removeMatch(UUID id, Integer type) throws SearchServiceException {
        // remove all MATCH related to deduplication item
        String queryDeleteMatch = RESOURCE_RESOURCETYPE_FIELD + ":" + type + " AND " + RESOURCE_FLAG_FIELD + ":"
                + DeduplicationFlag.MATCH.description + " AND " + RESOURCE_IDS_FIELD + ":" + id;
        delete(queryDeleteMatch);
    }

    @Override
    public void removeStoredDecision(UUID firstId, UUID secondId, DuplicateDecisionType type)
            throws SearchServiceException {
        QueryResponse response = findDecisions(firstId, secondId, type);

        SolrDocumentList solrDocumentList = response.getResults();
        for (SolrDocument solrDocument : solrDocumentList) {
            String duplicateID = solrDocument.getFieldValue(UNIQUE_ID_FIELD).toString();
            String queryDelete = UNIQUE_ID_FIELD + ":" + duplicateID;
            delete(queryDelete);
        }
    }

    public void build(Context ctx, UUID firstId, UUID secondId, DeduplicationFlag flag,
            Map<String, List<String>> signatures, SearchDeduplication searchSignature, String note) {
        SolrInputDocument doc = new SolrInputDocument();

        // build upgraded document
        doc.addField(LAST_INDEXED_FIELD, new Date());

        UUID[] sortedIds = new UUID[] { firstId, secondId };
        Arrays.sort(sortedIds);

        String dedupID = sortedIds[0] + "-" + sortedIds[1];

        doc.addField(UNIQUE_ID_FIELD, dedupID + "-" + flag.getDescription());
        doc.addField(RESOURCE_ID_FIELD, dedupID);
        doc.addField(RESOURCE_IDS_FIELD, sortedIds[0].toString());
        if (!firstId.equals(secondId)) {
            doc.addField(RESOURCE_IDS_FIELD, sortedIds[1].toString());
        }
        doc.addField(RESOURCE_RESOURCETYPE_FIELD, Constants.ITEM);
        doc.addField(RESOURCE_FLAG_FIELD, flag.getDescription());

        if (signatures != null) {
            for (String key : signatures.keySet()) {
                for (String ss : signatures.get(key)) {
                    doc.addField(key, ss);
                }
                doc.addField(RESOURCE_SIGNATURETYPE_FIELD, key);
            }
        }

        if (StringUtils.isNotBlank(note)) {
            doc.addField(RESOURCE_NOTE_FIELD, note);
        }

        if (searchSignature != null) {
            for (SolrDedupServiceIndexPlugin solrServiceIndexPlugin : searchSignature.getSolrIndexPlugin()) {
                solrServiceIndexPlugin.additionalIndex(ctx, sortedIds[0], sortedIds[1], doc);
            }

        }

        // write the document to the index
        try {
            writeDocument(doc);
            log.info("Wrote {} duplicate: {} to Index", flag.description, dedupID);
            if (!flag.equals(DeduplicationFlag.VERIFYWF)
                && !flag.equals(DeduplicationFlag.VERIFYWS)
                && !flag.equals(DeduplicationFlag.MATCH)) {
                log.debug("Committing deduplication index changes for {} duplicate: {}", flag.description, dedupID);
                getSolr().commit(true, true, true);
            }
        } catch (RuntimeException | IOException | SolrServerException e) {
            log.error("Error while writing a {} to deduplication index: {} message:{}", flag.description, dedupID,
                      e.getMessage(), e);
        }
    }

    private boolean areDifferentVersionsOfSameItem(Context context, Item iu, UUID matchId) {
        try {
            return versioningService.areDifferentVersionsOfSameItem(context, iu.getID(), matchId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected void writeDocument(SolrInputDocument doc) throws IOException {

        try {
            if (getSolr() != null) {
                getSolr().add(doc);

            }
        } catch (SolrServerException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void unIndexContent(Context context, Item item) {
        try {
            delete(MessageFormat.format(QUERY_REMOVE, item.getID(), item.getType()));

        } catch (SearchServiceException e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public QueryResponse search(SolrQuery solrQuery) throws SearchServiceException {
        try {
            return getSolr().query(solrQuery);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new org.dspace.discovery.SearchServiceException(e.getMessage(), e);
        }
    }

    @Override
    public QueryResponse findDecisions(UUID firstItemID, UUID secondItemID, DuplicateDecisionType type)
            throws SearchServiceException {

        String append = "";
        switch (type) {
            case WORKSPACE:
                append = "ws";
                break;
            case WORKFLOW:
                append = "ws";
                break;
            case ADMIN:
                append = "admin";
                break;
            default:
                // no-action
                break;
        }
        String[] sortedIds = new String[] { firstItemID.toString(), secondItemID.toString() };
        Arrays.sort(sortedIds);

        String dedupID = sortedIds[0] + "-" + sortedIds[1];

        String findQuery = MessageFormat.format(SolrDedupServiceImpl.SUBQUERY_STORED_DECISION, dedupID, append);

        QueryResponse response = find(findQuery);

        return response;
    }

    @Override
    public QueryResponse find(String query, String... filters) throws SearchServiceException {
        try {
            SolrQuery solrQuery = new SolrQuery();
            solrQuery.setQuery(query);
            solrQuery.addFilterQuery(filters);

            return getSolr().query(solrQuery);
        } catch (Exception e) {
            throw new org.dspace.discovery.SearchServiceException(e.getMessage(), e);
        }
    }

    @Override
    public UpdateResponse delete(String query) throws SearchServiceException {
        try {
            return getSolr().deleteByQuery(query);
            // Hard commit to make sure the rejection is stored
        } catch (Exception e) {
            throw new org.dspace.discovery.SearchServiceException(e.getMessage(), e);
        }
    }

    @Override
    public void cleanIndex(boolean force) throws IOException, SQLException, SearchServiceException {
        if (force) {
            try {
                getSolr().deleteByQuery("*:*");
            } catch (Exception e) {
                throw new SearchServiceException(e.getMessage(), e);
            }
        } else {
            Context context = new Context();
            context.turnOffAuthorisationSystem();

            try {
                if (getSolr() == null) {
                    return;
                }
                SolrQuery query = new SolrQuery();
                // Query for all indexed Items, Collections and Communities,
                // returning just their handle
                query.setFields(RESOURCE_IDS_FIELD);
                query.setQuery(RESOURCE_RESOURCETYPE_FIELD + ":" + Constants.ITEM);
                QueryResponse rsp = getSolr().query(query);
                SolrDocumentList docs = rsp.getResults();

                Iterator<SolrDocument> iter = docs.iterator();
                while (iter.hasNext()) {

                    SolrDocument doc = (SolrDocument) iter.next();

                    Collection<Object> ids = doc.getFieldValues(RESOURCE_IDS_FIELD);

                    for (Object id : ids) {
                        Item i = ContentServiceFactory.getInstance().getItemService()
                                /* getDSpaceObjectService(type) */.find(context, UUID.fromString((String) id));

                        if (i == null) {
                            log.info("Deleting: " + id);
                            /*
                             * Use IndexWriter to delete, its easier to manage write.lock
                             */
                            unIndexContent(context, i);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error cleaning cris deduplication index: " + e.getMessage(), e);
            } finally {
                context.abort();
            }
        }
    }

    @Override
    public void indexContent(Context context, List<UUID> ids, boolean force) {
        try {
            startMultiThreadIndex(context, force, ids);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void updateIndex(Context context, boolean force) {
        try {
            startMultiThreadIndex(context, true, null);
            commit();
            startMultiThreadIndex(context, false, null);
            commit();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void optimize() {
        try {
            if (getSolr() == null) {
                return;
            }
            long start = System.currentTimeMillis();
            System.out.println("SOLR Dedup Optimize -- Process Started:" + start);
            getSolr().optimize();
            long finish = System.currentTimeMillis();
            System.out.println("SOLR Dedup Optimize -- Process Finished:" + finish);
            System.out.println("SOLR Dedup Optimize -- Total time taken:" + (finish - start) + " (ms).");
        } catch (SolrServerException sse) {
            System.err.println(sse.getMessage());
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        }
    }

    @Override
    public void unIndexContent(Context context, String handleOrUuid) throws IllegalStateException, SQLException {
        Item item = null;
        if (StringUtils.isNotEmpty(handleOrUuid)) {

            item = (Item) HandleServiceFactory.getInstance().getHandleService().resolveToObject(context, handleOrUuid);
        }
        if (item != null) {
            unIndexContent(context, item);
        }
    }

    @Override
    public void removeMatch(Item item) {
        try {
            removeMatch(item.getID(), item.getType());
        } catch (SearchServiceException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void inheritDecisions(Context ctx, Item sourceItem, Item destinationItem) {

        List<DuplicateItemInfo> duplications = findDuplicationWithDecisions(ctx, sourceItem);

        for (DuplicateItemInfo duplication : duplications) {

            for (DuplicateDecisionType decisionType : duplication.getDecisionTypes()) {

                DuplicateDecisionValue decisionValue = duplication.getDecision(decisionType);

                setDuplicateDecision(ctx, destinationItem, duplication.getDuplicateItem().getID(),
                    decisionType, decisionValue, duplication.getNote(decisionType));
            }

        }

    }

    private void setDuplicateDecision(Context context, Item item, UUID duplicatedItemId,
        DuplicateDecisionType decisionType, DuplicateDecisionValue decisionValue, String note) {

        DuplicateDecisionObjectRest decisionObject = new DuplicateDecisionObjectRest();
        decisionObject.setNote(note);
        decisionObject.setType(decisionType);
        decisionObject.setValue(decisionValue.toString());

        try {
            dedupUtils.setDuplicateDecision(context, item.getID(), duplicatedItemId, item.getType(), decisionObject);
        } catch (AuthorizeException | SQLException | SearchServiceException e) {
            throw new RuntimeException(e);
        }

    }

    private List<DuplicateItemInfo> findDuplicationWithDecisions(Context context, Item item) {
        try {
            return dedupUtils.getAdminDuplicateByIdAndType(context, item.getID(), item.getType()).stream()
                             .filter(duplication -> isNotEmpty(duplication.getDecisionTypes()))
                             .collect(Collectors.toList());
        } catch (SQLException | SearchServiceException e) {
            throw new RuntimeException(e);
        }
    }

    private void startMultiThreadIndex(Context context, boolean onlyFake, List<UUID> ids) throws SQLException {
        int numThreads = configurationService.getIntProperty("deduplication.indexer.items.threads", 5);

        if (ids == null) {
            ids = new ArrayList<>();
            Iterator<Item> items = itemService.findAllUnfiltered(context);
            for (Item item : ImmutableList.copyOf(items)) {
                ids.add(item.getID());
            }
        }
        List<UUID>[] arrayIDList = Util.splitList(ids, numThreads);
        List<IndexerThread> threads = new ArrayList<IndexerThread>();
        for (List<UUID> hl : arrayIDList) {
            IndexerThread thread = new IndexerThread(hl, onlyFake);
            thread.start();
            threads.add(thread);
        }
        boolean finished = false;
        while (!finished) {
            finished = true;
            for (IndexerThread thread : threads) {
                finished = finished && !thread.isAlive();
            }
        }
    }

    private void buildFromDedupReject(Context ctx, DSpaceObject iu, Map<String, List<String>> tmpMapFilter,
                                      List<String> tmpFilter, SearchDeduplication searchSignature) {

        try {
            List<Deduplication> tri = deduplicationService.getDeduplicationByFirstAndSecond(ctx, iu.getID(),
                                                                                            iu.getID());

            for (Deduplication row : tri) {

                String submitterDecision = row.getSubmitterDecision();
                String workflowDecision = row.getWorkflowDecision();
                String adminDecision = row.getAdminDecision();
                String readerNote = row.getReaderNote();
                String adminNote = row.getNote();

                UUID firstId = row.getFirstItemId();
                UUID secondId = row.getSecondItemId();
                if (StringUtils.isNotBlank(submitterDecision)) {
                    buildDecision(ctx, firstId, secondId, DeduplicationFlag.getEnum(submitterDecision), readerNote);
                }

                if (StringUtils.isNotBlank(workflowDecision)) {
                    buildDecision(ctx, firstId, secondId, DeduplicationFlag.getEnum(workflowDecision), readerNote);
                }

                if (StringUtils.isNotBlank(adminDecision)) {
                    buildDecision(ctx, firstId, secondId, DeduplicationFlag.getEnum(adminDecision), adminNote);
                }
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }

    }

    @Override
    public void buildDecision(Context context, UUID firstId, UUID secondId, DeduplicationFlag flag, String note) {
        build(context, firstId, secondId, flag, null, null, note);
    }

    @Override
    public void commit() {
        if (getSolr() != null) {
            try {
                getSolr().commit();
            } catch (SolrServerException | IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void unIndexContent(Context context, UUID id) throws IllegalStateException, SQLException {
        try {
            delete(MessageFormat.format(QUERY_REMOVE, id, Constants.ITEM));

        } catch (SearchServiceException e) {
            log.error(e.getMessage(), e);
        }

    }

    public void setItemService(ItemService itemService) {
        this.itemService = itemService;
    }


    /***
     * Deduplication status
     * <p>
     * MATCH, there is a match between dedup.ids items. REJECTWS and REJECTWF, the
     * match was rejected by the user. REJECTADMIN, the match was rejected by the
     * administrator. VERIFYWS and VERIFYWF, the match has to be verified.
     */
    public enum DeduplicationFlag {

        FAKE("fake", 0), MATCH("match", 1), REJECTWS("reject_ws", 2), REJECTWF("reject_wf", 3),
        REJECTADMIN("reject_admin", 4), VERIFYWS("verify_ws", 5), VERIFYWF("verify_wf", 6);

        String description;

        int identifier;

        private DeduplicationFlag(String desc, int identifier) {
            this.description = desc;
            this.identifier = identifier;
        }

        public static DeduplicationFlag getEnum(String description) {
            switch (description) {
                case "reject_admin":
                    return DeduplicationFlag.REJECTADMIN;
                case "reject_ws":
                    return DeduplicationFlag.REJECTWS;
                case "reject_wf":
                    return DeduplicationFlag.REJECTWF;
                case "match":
                    return DeduplicationFlag.MATCH;
                case "verify_ws":
                    return DeduplicationFlag.VERIFYWS;
                case "verify_wf":
                    return DeduplicationFlag.VERIFYWF;
                default:
                    return DeduplicationFlag.FAKE;
            }
        }

        public String getDescription() {
            return description;
        }

        public int getIdentifier() {
            return identifier;
        }
    }

    class IndexerThread extends Thread {
        private boolean onlyFake;

        private List<UUID> itemids;

        public IndexerThread(List<UUID> itemids, boolean onlyFake) {
            this.onlyFake = onlyFake;
            this.itemids = itemids;
        }

        @Override
        public void run() {
            Context context = null;
            try {
                context = new Context();
                context.turnOffAuthorisationSystem();
                int idx = 1;
                final String head = this.getName() + "#" + this.getId();
                final int size = itemids.size();
                for (UUID id : itemids) {
                    try {
                        Item item = ContentServiceFactory.getInstance().getItemService().find(context, id);
                        Map<String, List<String>> tmpMapFilter = new HashMap<String, List<String>>();
                        List<String> tmpFilter = new ArrayList<String>();
                        fillSignature(context, (DSpaceObject) item, tmpMapFilter, tmpFilter);
                        if (!tmpFilter.isEmpty()) {
                            // retrieve all search plugin to build search document in the same index
                            SearchDeduplication searchSignature = dspace.getServiceManager().getServiceByName(
                                    "item".toUpperCase() + "SearchDeduplication", SearchDeduplication.class);
                            if (onlyFake) {
                                buildFromDedupReject(context, item, tmpMapFilter, tmpFilter, searchSignature);
                                build(context, item.getID(), item.getID(), DeduplicationFlag.FAKE, tmpMapFilter,
                                        searchSignature, null);
                            } else {
                                buildPotentialMatch(context, item, tmpMapFilter, tmpFilter, searchSignature);
                            }
                        }
                    } catch (Exception ex) {
                        System.out.println("ERROR: identifier item:" + id + " identifier thread:" + head + " error:"
                                + ex.getMessage());
                    }
                    System.out.println(head + ":" + (idx++) + " / " + size);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (context != null) {
                    context.abort();
                }
            }
        }
    }

}
