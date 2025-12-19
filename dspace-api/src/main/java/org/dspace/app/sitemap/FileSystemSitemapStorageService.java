/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.sitemap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import jakarta.annotation.PostConstruct;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * File system implementation of SitemapStorageService. This implementation
 * stores sitemap files in a local directory and maintains the same behavior
 * as the original AbstractGenerator implementation.
 * <P>
 * Configuration properties:
 * - sitemap.dir: Directory where sitemap files are stored
 * - dspace.ui.url: Base URL for generating sitemap access URLs
 * - sitemap.path: Path segment for sitemap access (defaults to "sitemaps")
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 */
public class FileSystemSitemapStorageService implements SitemapStorageService {

    private static final Logger log = LogManager.getLogger(FileSystemSitemapStorageService.class);

    @Autowired
    protected ConfigurationService configurationService;

    /**
     * Directory where sitemap files are stored
     */
    private File outputDirectory;

    /**
     * Base URL for accessing sitemap files
     */
    private String baseUrl;

    /**
     * Path segment for sitemap access
     */
    private String sitemapPath;

    /**
     * Flag to track initialization state
     */
    private volatile boolean initialized = false;

    /**
     * Automatically called after dependency injection completes.
     * This ensures the service is properly initialized before any method is called.
     */
    @PostConstruct
    private void initialize() throws IOException {
        if (initialized) {
            log.warn("FileSystemSitemapStorageService already initialized, skipping re-initialization");
            return;
        }

        log.info("Initializing FileSystemSitemapStorageService...");

        // Get the output directory from configuration
        String outputDirPath = configurationService.getProperty("sitemap.dir");
        if (outputDirPath == null || outputDirPath.trim().isEmpty()) {
            log.error("sitemap.dir configuration property is not set or is empty");
            throw new IOException("sitemap.dir configuration property is not set");
        }

        outputDirectory = new File(outputDirPath);
        log.info("Configuring sitemap directory path: {}", outputDirectory.getAbsolutePath());

        // Verify the path can be created by checking parent write permissions
        validateDirectoryPath(outputDirectory);

        // Set up URL configuration
        baseUrl = configurationService.getProperty("dspace.ui.url");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            log.warn("dspace.ui.url is not configured, sitemap URLs may not be accessible");
            baseUrl = "";
        } else if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        sitemapPath = configurationService.getProperty("sitemap.path", "sitemaps");
        if (sitemapPath.startsWith("/")) {
            sitemapPath = sitemapPath.substring(1);
        }

        initialized = true;
        log.info("FileSystemSitemapStorageService initialized successfully - Directory: {}, Base URL: {}/{}",
                 outputDirectory.getAbsolutePath(), baseUrl, sitemapPath);
    }

    /**
     * Validates that a directory path can be created/written to.
     * This performs validation only, without creating the directory.
     *
     * @param directory the directory to validate
     * @throws IOException if the directory path cannot be used
     */
    private void validateDirectoryPath(File directory) throws IOException {
        // If directory exists, verify it's actually a directory and writable
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                log.error("Sitemap path exists but is not a directory: {}", directory.getAbsolutePath());
                throw new IOException("Sitemap path is not a directory: " + directory.getAbsolutePath());
            }

            if (!directory.canWrite()) {
                log.error("Cannot write to sitemap directory: {}. Check file permissions.",
                          directory.getAbsolutePath());
                throw new IOException("Cannot write to sitemap directory: " + directory.getAbsolutePath());
            }

            log.info("Sitemap directory exists and is writable: {}", directory.getAbsolutePath());
        } else {
            // Directory doesn't exist - verify we can create it by checking parent permissions
            log.debug("Sitemap directory does not exist, validating parent write permissions: {}",
                      directory.getAbsolutePath());

            // Find the first existing parent directory
            File firstExistingParent = directory.getParentFile();
            while (firstExistingParent != null && !firstExistingParent.exists()) {
                firstExistingParent = firstExistingParent.getParentFile();
            }

            if (firstExistingParent == null) {
                log.error("Could not find any existing parent directory for: {}. Path may be invalid.",
                          directory.getAbsolutePath());
                throw new IOException("Cannot find valid parent directory for: " + directory.getAbsolutePath());
            }

            if (!firstExistingParent.canWrite()) {
                log.error("Cannot write to parent directory: {}. Check file permissions. " +
                              "This directory must be writable to create: {}",
                          firstExistingParent.getAbsolutePath(),
                          directory.getAbsolutePath());
                throw new IOException("Cannot write to parent directory: " + firstExistingParent.getAbsolutePath());
            }

            log.info("Parent directory is writable, can create sitemap directory when needed: {}",
                     firstExistingParent.getAbsolutePath());
        }
    }

    /**
     * Ensures the output directory exists and is writable.
     * Creates the directory (and all parent directories) if it doesn't exist.
     *
     * @throws IOException if the directory cannot be created or is not writable
     */
    private void ensureDirectoryExists() throws IOException {
        if (!outputDirectory.exists()) {
            log.info("Creating sitemap directory: {}", outputDirectory.getAbsolutePath());

            if (!outputDirectory.mkdirs()) {
                // Additional diagnostics for failure
                if (outputDirectory.exists()) {
                    log.error("Path exists after mkdirs() failed. Is it a file? {}",
                              !outputDirectory.isDirectory());
                } else {
                    File parent = outputDirectory.getParentFile();
                    if (parent != null && parent.exists() && !parent.canWrite()) {
                        log.error("Parent directory is not writable: {}", parent.getAbsolutePath());
                    }
                }

                log.error("Failed to create sitemap directory: {}. Possible reasons: " +
                              "1) Insufficient permissions, 2) Path already exists as a file, " +
                              "3) I/O error, 4) Parent directory is read-only",
                          outputDirectory.getAbsolutePath());
                throw new IOException("Failed to create sitemap directory: " + outputDirectory.getAbsolutePath());
            }

            log.info("Successfully created sitemap directory: {}", outputDirectory.getAbsolutePath());
        }

        // Verify it's writable (whether just created or already existed)
        if (!outputDirectory.canWrite()) {
            log.error("Sitemap directory is not writable: {}. Check file permissions.",
                      outputDirectory.getAbsolutePath());
            throw new IOException("Cannot write to sitemap directory: " + outputDirectory.getAbsolutePath());
        }
    }

    @Override
    public void deleteSitemaps() throws IOException {
        ensureInitialized();

        if (!outputDirectory.exists()) {
            log.warn("Sitemap directory does not exist, nothing to delete: {}",
                     outputDirectory.getAbsolutePath());
            return;
        }

        if (!outputDirectory.isDirectory()) {
            log.error("Sitemap path exists but is not a directory: {}",
                      outputDirectory.getAbsolutePath());
            throw new IOException("Cannot delete sitemaps, path is not a directory: " +
                                      outputDirectory.getAbsolutePath());
        }

        log.info("Deleting sitemaps directory: {}", outputDirectory.getAbsolutePath());
        FileUtils.deleteDirectory(outputDirectory);
        log.info("Successfully deleted sitemaps directory: {}", outputDirectory.getAbsolutePath());
    }

    @Override
    public OutputStream createSitemapFile(String filename, boolean useCompression) throws IOException {
        ensureInitialized();

        // Ensure directory exists before creating files (recreates if deleted)
        ensureDirectoryExists();

        File sitemapFile = new File(outputDirectory, filename);
        log.debug("Creating sitemap file: {} (compression: {})", sitemapFile.getAbsolutePath(), useCompression);

        OutputStream outputStream = new FileOutputStream(sitemapFile);

        if (useCompression) {
            outputStream = new GZIPOutputStream(outputStream);
        }

        return outputStream;
    }

    @Override
    public OutputStream createIndexFile(String indexFilename, boolean useCompression) throws IOException {
        ensureInitialized();

        // Ensure directory exists before creating files (recreates if deleted)
        ensureDirectoryExists();

        File indexFile = new File(outputDirectory, indexFilename);
        log.debug("Creating sitemap index file: {} (compression: {})", indexFile.getAbsolutePath(), useCompression);

        OutputStream outputStream = new FileOutputStream(indexFile);

        if (useCompression) {
            outputStream = new GZIPOutputStream(outputStream);
        }

        return outputStream;
    }

    @Override
    public boolean exists(String filename) {
        ensureInitialized();
        File file = new File(outputDirectory, filename);
        return file.exists() && file.isFile();
    }

    @Override
    public String[] listSitemapFiles() {
        ensureInitialized();

        File[] files = outputDirectory.listFiles((dir, name) -> name.endsWith(".xml") || name.endsWith(".xml.gz") ||
            name.endsWith(".html") || name.endsWith(".html.gz"));

        if (files == null) {
            return new String[0];
        }

        String[] filenames = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            filenames[i] = files[i].getName();
        }

        return filenames;
    }

    /**
     * Get the output directory where sitemap files are stored.
     * This method is useful for testing and debugging.
     *
     * @return the output directory
     */
    public File getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Ensure the service has been initialized.
     * This provides a safety net in case @PostConstruct didn't run.
     */
    private void ensureInitialized() {
        if (!initialized || outputDirectory == null) {
            throw new IllegalStateException("FileSystemSitemapStorageService has not been initialized. " +
                                                "Ensure the bean container properly called @PostConstruct " +
                                                "initialization.");
        }
    }

    /**
     * Check if the service is initialized.
     * Useful for testing and diagnostics.
     *
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }
}