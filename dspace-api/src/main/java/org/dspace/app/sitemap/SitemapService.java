/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.sitemap;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Service interface for sitemap generation and management operations.
 * This service orchestrates the creation of HTML and XML sitemaps,
 * manages sitemap files, and provides high-level operations for
 * sitemap lifecycle management.
 * <P>
 * The service abstracts away the details of data collection, file
 * generation, and storage operations, providing a clean interface
 * for sitemap-related operations.
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 */
public interface SitemapService {

    /**
     * Generate sitemap.org protocol and/or basic HTML sitemaps.
     *
     * @param makeHTMLMap    if {@code true}, generate an HTML sitemap
     * @param makeSitemapOrg if {@code true}, generate an sitemap.org sitemap
     * @throws SQLException if a database error occurs
     * @throws IOException  if an IO error occurs
     */
    void generateSitemaps(boolean makeHTMLMap, boolean makeSitemapOrg) throws SQLException, IOException;

    /**
     * Delete all sitemap files and clean up storage.
     *
     * @throws IOException if an IO error occurs during deletion
     */
    void deleteSitemaps() throws IOException;

    /**
     * Check if sitemap files exist in storage.
     *
     * @return true if sitemap files exist, false otherwise
     */
    boolean sitemapsExist();

    /**
     * Validate sitemap configuration and storage availability.
     *
     * @throws IOException if validation fails
     */
    void validateConfiguration() throws IOException;

}
