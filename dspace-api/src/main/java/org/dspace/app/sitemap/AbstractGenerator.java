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
import java.io.PrintStream;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.utils.DSpace;

/**
 * Base class for creating sitemaps of various kinds. A sitemap consists of one
 * or more files which list significant URLs on a site for search engines to
 * efficiently crawl. Dates of modification may also be included. A sitemap
 * index file that links to each of the sitemap files is also generated. It is
 * this index file that search engines should be directed towards.
 * <P>
 * Provides most of the required functionality, subclasses need just implement a
 * few methods that specify the "boilerplate" and text for including URLs.
 * <P>
 * Typical usage:
 * <pre>
 *   AbstractGenerator g = new FooGenerator(...);
 *   while (...) {
 *     g.addURL(url, date);
 *   }
 *   g.finish();
 * </pre>
 *
 * @author Robert Tansley
 */
public abstract class AbstractGenerator {

    private static final Logger log = LogManager.getLogger(AbstractGenerator.class);

    /**
     * Number of files written so far
     */
    protected int fileCount;

    /**
     * Number of bytes written to current file
     */
    protected int bytesWritten;

    /**
     * Number of URLs written to current file
     */
    protected int urlsWritten;

    /**
     * Storage service for writing sitemap files
     */
    protected SitemapStorageService storageService = new DSpace().getServiceManager()
                .getServiceByName("sitemapStorageService", SitemapStorageService.class);

    /**
     * Current output
     */
    protected PrintStream currentOutput;

    /**
     * Current underlying output stream
     */
    protected OutputStream currentOutputStream;

    /**
     * Size in bytes of trailing boilerplate
     */
    private final int trailingByteCount;

    /**
     * Initialize this generator to write to the given directory. This must be
     * called by any subclass constructor.
     *
     */
    public AbstractGenerator() {
        fileCount = 0;
        trailingByteCount = getTrailingBoilerPlate().length();
        currentOutput = null;
        currentOutputStream = null;
    }

    /**
     * Start writing a new sitemap file.
     *
     * @throws IOException if IO error
     *                     if an error occurs creating the file
     */
    protected void startNewFile() throws IOException {
        String lbp = getLeadingBoilerPlate();
        String filename = getFilename(fileCount);

        log.debug("Starting new sitemap file: {} (file count: {})", filename, fileCount);

        OutputStream tempOutputStream = null;
        PrintStream tempOutput = null;
        boolean success = false;

        try {
            tempOutputStream = storageService.createSitemapFile(filename, useCompression());
            tempOutput = new PrintStream(tempOutputStream);
            tempOutput.print(lbp);

            // Mark as successful before transferring ownership
            success = true;

            // Only assign to instance variables after successful initialization
            currentOutputStream = tempOutputStream;
            currentOutput = tempOutput;
            bytesWritten = lbp.length();
            urlsWritten = 0;

        } finally {
            // If initialization failed, ensure streams are closed
            if (!success) {
                if (tempOutput != null) {
                    try {
                        tempOutput.close();
                    } catch (Exception e) {
                        log.error("Error closing PrintStream during cleanup", e);
                    }
                } else if (tempOutputStream != null) {
                    try {
                        tempOutputStream.close();
                    } catch (IOException e) {
                        log.error("Error closing OutputStream during cleanup", e);
                    }
                }
            }
        }
    }

    /**
     * Add the given URL to the sitemap.
     *
     * @param url     Full URL to add
     * @param lastMod Date URL was last modified, or {@code null}
     * @throws IOException if IO error
     *                     if an error occurs writing
     */
    public void addURL(String url, Date lastMod) throws IOException {
        try {
            // Kick things off if this is the first call
            if (currentOutput == null) {
                startNewFile();
            }

            String newURLText = getURLText(url, lastMod);

            if (bytesWritten + newURLText.length() + trailingByteCount > getMaxSize()
                || urlsWritten + 1 > getMaxURLs()) {
                closeCurrentFile();
                startNewFile();
            }

            currentOutput.print(newURLText);
            bytesWritten += newURLText.length();
            urlsWritten++;
        } catch (IOException e) {
            // Ensure streams are cleaned up if an error occurs
            cleanup();
            throw e;
        }
    }

    /**
     * Cleanup method to ensure all streams are properly closed.
     * This method should be called in case of exceptions to prevent resource leaks.
     */
    public void cleanup() {
        if (currentOutput != null) {
            try {
                currentOutput.close();
            } finally {
                currentOutput = null;
            }
        }

        if (currentOutputStream != null) {
            try {
                currentOutputStream.close();
            } catch (IOException e) {
                log.error("Error closing OutputStream during cleanup", e);
            } finally {
                currentOutputStream = null;
            }
        }

        log.debug("AbstractGenerator cleanup completed");
    }

    /**
     * Finish with the current sitemap file.
     *
     * @throws IOException if IO error
     *                     if an error occurs writing
     */
    protected void closeCurrentFile() throws IOException {
        if (currentOutput != null) {
            IOException writeException = null;
            IOException underlyingStreamException = null;

            try {
                currentOutput.print(getTrailingBoilerPlate());
            } catch (Exception e) {
                writeException = new IOException("Error writing trailing boilerplate to sitemap file", e);
                log.error("Error writing trailing boilerplate to sitemap file", e);
            }

            // Always attempt to close PrintStream
            try {
                currentOutput.close();
            } finally {
                currentOutput = null;
            }

            // Always attempt to close underlying OutputStream
            if (currentOutputStream != null) {
                try {
                    currentOutputStream.close();
                } catch (IOException e) {
                    underlyingStreamException = e;
                    log.error("Error closing underlying OutputStream", e);
                } finally {
                    currentOutputStream = null;
                }
            }

            fileCount++;
            log.debug("Closed sitemap file {} with {} URLs and {} bytes",
                     getFilename(fileCount - 1), urlsWritten, bytesWritten);

            // Throw the first exception that occurred, if any
            if (writeException != null) {
                throw writeException;
            } else if (underlyingStreamException != null) {
                throw underlyingStreamException;
            }
        }
    }

    /**
     * Complete writing sitemap files and write the index files. This is invoked
     * when all calls to {@link AbstractGenerator#addURL(String, Date)} have
     * been completed, and invalidates the generator.
     *
     * @return number of sitemap files written.
     * @throws IOException if IO error
     *                     if an error occurs writing
     */
    public int finish() throws IOException {
        try {
            // Close the current file if it's open
            if (null != currentOutput) {
                closeCurrentFile();
            }

            // Write the index file
            String indexFilename = getIndexFilename();
            log.debug("Writing sitemap index file: {} for {} sitemap files", indexFilename, fileCount);

            try (OutputStream indexOutputStream = storageService.createIndexFile(indexFilename, useCompression());
                 PrintStream out = new PrintStream(indexOutputStream)) {
                writeIndex(out, fileCount);
            }

            log.info("Sitemap generation completed - {} files written, index: {}", fileCount, indexFilename);
            return fileCount;
        } catch (IOException e) {
            // Ensure any remaining streams are closed in case of error
            cleanup();
            throw e;
        }
    }

    /**
     * Return marked-up text to be included in a sitemap about a given URL.
     *
     * @param url     URL to add information about
     * @param lastMod date URL was last modified, or {@code null} if unknown or not
     *                applicable
     * @return the mark-up to include
     */
    public abstract String getURLText(String url, Date lastMod);

    /**
     * Return the boilerplate at the top of a sitemap file.
     *
     * @return The boilerplate markup.
     */
    public abstract String getLeadingBoilerPlate();

    /**
     * Return the boilerplate at the end of a sitemap file.
     *
     * @return The boilerplate markup.
     */
    public abstract String getTrailingBoilerPlate();

    /**
     * Return the maximum size in bytes that an individual sitemap file should
     * be.
     *
     * @return the size in bytes.
     */
    public abstract int getMaxSize();

    /**
     * Return the maximum number of URLs that an individual sitemap file should
     * contain.
     *
     * @return the maximum number of URLs.
     */
    public abstract int getMaxURLs();

    /**
     * Return whether the written sitemap files and index should be
     * GZIP-compressed.
     *
     * @return {@code true} if GZIP compression should be used, {@code false}
     * otherwise.
     */
    public abstract boolean useCompression();

    /**
     * Return the filename a sitemap at the given index should be stored at.
     *
     * @param number index of the sitemap file (zero is first).
     * @return the filename to write the sitemap to.
     */
    public abstract String getFilename(int number);

    /**
     * Get the filename the index should be written to.
     *
     * @return the filename of the index.
     */
    public abstract String getIndexFilename();

    /**
     * Write the index file.
     *
     * @param output       stream to write the index to
     * @param sitemapCount number of sitemaps that were generated
     * @throws IOException if IO error
     *                     if an IO error occurs
     */
    public abstract void writeIndex(PrintStream output, int sitemapCount)
        throws IOException;
}
