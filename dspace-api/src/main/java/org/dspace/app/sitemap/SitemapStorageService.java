/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.sitemap;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Service interface for sitemap storage operations. This abstraction allows
 * different storage backends to be used for sitemap generation and retrieval.
 * <P>
 * Implementations can support various storage systems such as:
 * - File system storage
 * - Cloud storage (S3, Azure Blob, etc.)
 * - Database storage
 * - Distributed file systems
 * <P>
 * The service handles compression, file naming, and storage-specific operations
 * transparently to the sitemap generators.
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 */
public interface SitemapStorageService {

    /**
     * Create an output stream for writing a sitemap file. The implementation
     * should handle compression if enabled and ensure proper file naming.
     *
     * @param filename      the name of the sitemap file to create
     * @param useCompression whether to apply compression (e.g., GZIP) to the output
     * @return an OutputStream for writing the sitemap content
     * @throws IOException if an error occurs creating the output stream
     */
    OutputStream createSitemapFile(String filename, boolean useCompression) throws IOException;

    /**
     * Create an output stream for writing the sitemap index file. The implementation
     * should handle compression if enabled.
     *
     * @param indexFilename  the name of the index file to create
     * @param useCompression whether to apply compression (e.g., GZIP) to the output
     * @return an OutputStream for writing the index content
     * @throws IOException if an error occurs creating the output stream
     */
    OutputStream createIndexFile(String indexFilename, boolean useCompression) throws IOException;

    /**
     * Check if a sitemap file exists in the storage.
     *
     * @param filename the name of the file to check
     * @return true if the file exists, false otherwise
     */
    boolean exists(String filename);

    /**
     * List all sitemap files in the storage.
     *
     * @return an array of filenames, or empty array if no files exist
     */
    String[] listSitemapFiles();

    void deleteSitemaps() throws IOException;
}