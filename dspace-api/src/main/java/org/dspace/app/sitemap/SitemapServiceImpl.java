/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.sitemap;

import static org.dspace.discovery.SearchUtils.RESOURCE_TYPE_FIELD;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.core.Context;
import org.dspace.core.LogHelper;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResult;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.discovery.SearchUtils;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Implementation of SitemapService that handles sitemap generation,
 * data collection from DSpace repositories, and coordination with
 * storage services.
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 */
@Service
public class SitemapServiceImpl implements SitemapService {

    private static final Logger log = LogManager.getLogger(SitemapServiceImpl.class);

    private static final int PAGE_SIZE = 100;

    @Autowired
    protected ConfigurationService configurationService;

    @Autowired
    protected SitemapStorageService storageService;

    private final SearchService searchService = SearchUtils.getSearchService();

    @Override
    public void generateSitemaps(boolean makeHTMLMap, boolean makeSitemapOrg) throws SQLException, IOException {
        log.info("Starting generateSitemaps() - HTML sitemap: {}, XML sitemap: {}", makeHTMLMap, makeSitemapOrg);

        validateConfiguration();

        String uiURLStem = configurationService.getProperty("dspace.ui.url");
        if (!uiURLStem.endsWith("/")) {
            uiURLStem = uiURLStem + '/';
        }
        String sitemapStem = uiURLStem + "sitemap";
        log.debug("UI URL stem: {}, Sitemap stem: {}", uiURLStem, sitemapStem);

        AbstractGenerator html = null;
        AbstractGenerator sitemapsOrg = null;

        if (makeHTMLMap) {
            log.info("Initializing HTML sitemap generator");
            html = new HTMLSitemapGenerator(sitemapStem, ".html");
        }

        if (makeSitemapOrg) {
            log.info("Initializing XML (sitemaps.org) sitemap generator");
            sitemapsOrg = new SitemapsOrgGenerator(sitemapStem, ".xml");
        }

        Context c = new Context(Context.Mode.READ_ONLY);
        log.info("Created DSpace context for sitemap generation");

        long commsCount;
        long collsCount;
        long itemsCount;

        try {
            log.info("Starting discovery queries to collect community, collection, and item data");

            // Process Communities
            log.debug("Processing communities...");
            commsCount = processEntities(c, html, sitemapsOrg, "Community",
                                       uiURLStem + "communities/", makeHTMLMap, makeSitemapOrg);
            log.debug("Completed processing {} communities", commsCount);

            // Process Collections
            log.debug("Processing collections...");
            collsCount = processEntities(c, html, sitemapsOrg, "Collection",
                                       uiURLStem + "collections/", makeHTMLMap, makeSitemapOrg);
            log.debug("Completed processing {} collections", collsCount);

            // Process Items
            log.debug("Processing items...");
            itemsCount = processItems(c, html, sitemapsOrg, uiURLStem, makeHTMLMap, makeSitemapOrg);
            log.debug("Completed processing {} items", itemsCount);

            // Finalize sitemaps
            finalizeSitemaps(c, html, sitemapsOrg, makeHTMLMap, makeSitemapOrg,
                           commsCount, collsCount, itemsCount);

            log.info("Sitemap generation process completed successfully - Total processed: " +
                         "{} communities, {} collections, {} items", commsCount, collsCount, itemsCount);

        } catch (SearchServiceException e) {
            log.error("Search service exception during sitemap generation", e);
            // Cleanup generators to prevent resource leaks
            cleanupGenerators(html, sitemapsOrg);
            throw new RuntimeException(e);
        } catch (Exception e) {
            log.error("Unexpected exception during sitemap generation", e);
            // Cleanup generators to prevent resource leaks
            cleanupGenerators(html, sitemapsOrg);
            throw e;
        } finally {
            log.info("Cleaning up DSpace context");
            c.abort();
        }
    }

    @Override
    public void deleteSitemaps() throws IOException {
        log.info("Attempting to delete all sitemap files");

        if (!sitemapsExist()) {
            log.warn("No sitemap files found to delete");
            return;
        }
        storageService.deleteSitemaps();
    }

    @Override
    public boolean sitemapsExist() {
        String[] files = storageService.listSitemapFiles();
        return files != null && files.length > 0;
    }

    @Override
    public void validateConfiguration() throws IOException {
        String sitemapDir = configurationService.getProperty("sitemap.dir");
        String uiUrl = configurationService.getProperty("dspace.ui.url");

        if (sitemapDir == null || sitemapDir.trim().isEmpty()) {
            throw new IOException("sitemap.dir configuration property is not set");
        }

        if (uiUrl == null || uiUrl.trim().isEmpty()) {
            throw new IOException("dspace.ui.url configuration property is not set");
        }

        log.debug("Sitemap configuration validated successfully");
    }

    /**
     * Process entities of a specific type (Community, Collection).
     */
    private long processEntities(Context context, AbstractGenerator html, AbstractGenerator sitemapsOrg,
                               String entityType, String urlPrefix, boolean makeHTMLMap, boolean makeSitemapOrg)
        throws SearchServiceException, IOException, SQLException {

        int offset = 0;
        long totalCount;

        DiscoverQuery discoveryQuery = new DiscoverQuery();
        discoveryQuery.setMaxResults(PAGE_SIZE);
        discoveryQuery.setQuery("*:*");
        discoveryQuery.addFilterQueries(RESOURCE_TYPE_FIELD + ":" + entityType);

        do {
            discoveryQuery.setStart(offset);
            DiscoverResult discoverResult = searchService.search(context, discoveryQuery);
            List<IndexableObject> docs = discoverResult.getIndexableObjects();
            totalCount = discoverResult.getTotalSearchResults();

            log.debug("Processing {} batch: offset={}, batchSize={}, total={}",
                     entityType, offset, docs.size(), totalCount);

            for (IndexableObject doc : docs) {
                String url = urlPrefix + doc.getID();
                context.uncacheEntity(doc.getIndexedObject());

                if (makeHTMLMap && html != null) {
                    html.addURL(url, null);
                }
                if (makeSitemapOrg && sitemapsOrg != null) {
                    sitemapsOrg.addURL(url, null);
                }
            }
            offset += PAGE_SIZE;
        } while (offset < totalCount);

        return totalCount;
    }

    /**
     * Process items with special handling for entity types.
     */
    private long processItems(Context context, AbstractGenerator html, AbstractGenerator sitemapsOrg,
                            String uiURLStem, boolean makeHTMLMap, boolean makeSitemapOrg)
        throws SearchServiceException, IOException, SQLException {

        int offset = 0;
        long totalCount;

        DiscoverQuery discoveryQuery = new DiscoverQuery();
        discoveryQuery.setMaxResults(PAGE_SIZE);
        discoveryQuery.setQuery("*:*");
        discoveryQuery.addFilterQueries(RESOURCE_TYPE_FIELD + ":Item");
        discoveryQuery.addSearchField("search.entitytype");

        do {
            discoveryQuery.setStart(offset);
            DiscoverResult discoverResult = searchService.search(context, discoveryQuery);
            List<IndexableObject> docs = discoverResult.getIndexableObjects();
            totalCount = discoverResult.getTotalSearchResults();

            log.debug("Processing item batch: offset={}, batchSize={}, total={}",
                     offset, docs.size(), totalCount);

            for (IndexableObject doc : docs) {
                String url;
                List<String> entityTypeFieldValues = discoverResult.getSearchDocument(doc).get(0)
                                        .getSearchFieldValues("search.entitytype");
                if (CollectionUtils.isNotEmpty(entityTypeFieldValues)) {
                    url = uiURLStem + "entities/" + StringUtils.lowerCase(entityTypeFieldValues.get(0)) + "/"
                            + doc.getID();
                } else {
                    url = uiURLStem + "items/" + doc.getID();
                }
                Date lastMod = doc.getLastModified();
                context.uncacheEntity(doc.getIndexedObject());

                if (makeHTMLMap && html != null) {
                    html.addURL(url, lastMod);
                }
                if (makeSitemapOrg && sitemapsOrg != null) {
                    sitemapsOrg.addURL(url, lastMod);
                }
            }
            offset += PAGE_SIZE;
        } while (offset < totalCount);

        return totalCount;
    }

    /**
     * Finalize sitemap generation and log results.
     */
    private void finalizeSitemaps(Context context, AbstractGenerator html, AbstractGenerator sitemapsOrg,
                                boolean makeHTMLMap, boolean makeSitemapOrg,
                                long commsCount, long collsCount, long itemsCount) throws IOException {

        if (makeHTMLMap && html != null) {
            log.debug("Finalizing HTML sitemap files...");
            int files = html.finish();
            log.debug("HTML sitemap generation completed - Files created: {}", files);
            log.info(LogHelper.getHeader(context, "write_sitemap",
                                          "type=html,num_files=" + files + ",communities="
                                              + commsCount + ",collections=" + collsCount
                                              + ",items=" + itemsCount));
        }

        if (makeSitemapOrg && sitemapsOrg != null) {
            log.debug("Finalizing XML sitemap files...");
            int files = sitemapsOrg.finish();
            log.debug("XML sitemap generation completed - Files created: {}", files);
            log.info(LogHelper.getHeader(context, "write_sitemap",
                                          "type=xml,num_files=" + files + ",communities="
                                              + commsCount + ",collections=" + collsCount
                                              + ",items=" + itemsCount));
        }
    }

    /**
     * Cleanup method to ensure all generators properly close their streams.
     * This method should be called in case of exceptions to prevent resource leaks.
     *
     * @param generators the generators to cleanup (can be null)
     */
    private void cleanupGenerators(AbstractGenerator... generators) {
        for (AbstractGenerator generator : generators) {
            if (generator != null) {
                try {
                    generator.cleanup();
                } catch (Exception e) {
                    log.error("Error during generator cleanup", e);
                }
            }
        }
    }
}

