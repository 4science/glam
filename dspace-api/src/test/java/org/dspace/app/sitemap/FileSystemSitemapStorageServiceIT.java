/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.sitemap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.FileUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Integration tests for FileSystemSitemapStorageService.
 * These tests verify the actual file system operations, directory creation,
 * file compression, and integration with the DSpace configuration system.
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 */
public class FileSystemSitemapStorageServiceIT extends AbstractIntegrationTestWithDatabase {

    private FileSystemSitemapStorageService sitemapStorageService;
    private ConfigurationService configurationService;
    private Path tempSitemapDir;
    private String originalSitemapDir;
    private String originalBaseUrl;
    private String originalSitemapPath;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

        // Store original configuration values for restoration
        originalSitemapDir = configurationService.getProperty("sitemap.dir");
        originalBaseUrl = configurationService.getProperty("dspace.ui.url");
        originalSitemapPath = configurationService.getProperty("sitemap.path");

        // Create a temporary directory for testing
        String dspaceDir = configurationService.getProperty("dspace.dir");
        tempSitemapDir = Files.createTempDirectory(Path.of(dspaceDir), "sitemap-permtest-");

        // Set test configuration
        configurationService.setProperty("sitemap.dir", tempSitemapDir.toString());
        configurationService.setProperty("dspace.ui.url", "https://test.dspace.org");
        configurationService.setProperty("sitemap.path", "test-sitemaps");

        // Create and initialize the service
        sitemapStorageService = new FileSystemSitemapStorageService();
        ReflectionTestUtils.setField(sitemapStorageService, "configurationService", configurationService);

        // Initialize the service (simulating @PostConstruct)
        ReflectionTestUtils.invokeMethod(sitemapStorageService, "initialize");
    }

    @After
    @Override
    public void destroy() throws Exception {
        // Clean up temporary directory
        if (tempSitemapDir != null && Files.exists(tempSitemapDir)) {
            FileUtils.deleteDirectory(tempSitemapDir.toFile());
        }

        // Restore original configuration values
        if (originalSitemapDir != null) {
            configurationService.setProperty("sitemap.dir", originalSitemapDir);
        } else {
            configurationService.setProperty("sitemap.dir", null);
        }

        if (originalBaseUrl != null) {
            configurationService.setProperty("dspace.ui.url", originalBaseUrl);
        } else {
            configurationService.setProperty("dspace.ui.url", null);
        }

        if (originalSitemapPath != null) {
            configurationService.setProperty("sitemap.path", originalSitemapPath);
        } else {
            configurationService.setProperty("sitemap.path", null);
        }

        super.destroy();
    }

    /**
     * Test service initialization with valid configuration
     */
    @Test
    public void testInitializationSuccess() throws Exception {
        // Service should be initialized by setUp()
        assertTrue("Service should be initialized", sitemapStorageService.isInitialized());
        assertNotNull("Output directory should be set", sitemapStorageService.getOutputDirectory());
        assertEquals("Output directory should match temp directory",
                     tempSitemapDir.toFile(), sitemapStorageService.getOutputDirectory());
    }

    /**
     * Test initialization failure when sitemap.dir is not configured
     */
    @Test
    public void testInitializationFailsWithMissingSitemapDir() throws Exception {
        // Create a new service instance
        FileSystemSitemapStorageService newService = new FileSystemSitemapStorageService();
        ReflectionTestUtils.setField(newService, "configurationService", configurationService);

        // Remove sitemap.dir configuration
        configurationService.setProperty("sitemap.dir", null);

        try {
            ReflectionTestUtils.invokeMethod(newService, "initialize");
            fail("Should have thrown IOException for missing sitemap.dir");
        } catch (UndeclaredThrowableException e) {
            Throwable cause = e.getCause();
            assertThat("Should contain appropriate error message",
                       cause.getMessage(), containsString("sitemap.dir configuration property is not set"));
        }
    }

    /**
     * Test initialization failure when sitemap.dir is empty
     */
    @Test
    public void testInitializationFailsWithEmptySitemapDir() throws Exception {
        FileSystemSitemapStorageService newService = new FileSystemSitemapStorageService();
        ReflectionTestUtils.setField(newService, "configurationService", configurationService);

        configurationService.setProperty("sitemap.dir", "   ");

        try {
            ReflectionTestUtils.invokeMethod(newService, "initialize");
            fail("Should have thrown IOException for empty sitemap.dir");
        } catch (UndeclaredThrowableException e) {
            Throwable cause = e.getCause();
            assertThat("Should contain appropriate error message",
                       cause.getMessage(), containsString("sitemap.dir configuration property is not set"));
        }
    }


    /**
     * Test directory creation when it doesn't exist
     */
    @Test
    public void testDirectoryCreation() throws Exception {
        // Delete the directory to test creation
        FileUtils.deleteDirectory(tempSitemapDir.toFile());
        assertFalse("Directory should not exist", Files.exists(tempSitemapDir));

        // Create a sitemap file - this should trigger directory creation
        try (OutputStream os = sitemapStorageService.createSitemapFile("test.xml", false)) {
            os.write("<sitemapindex></sitemapindex>".getBytes());
        }

        assertTrue("Directory should have been created", Files.exists(tempSitemapDir));
        assertTrue("Should be a directory", Files.isDirectory(tempSitemapDir));
        assertTrue("Directory should be writable", Files.isWritable(tempSitemapDir));
    }

    /**
     * Test sitemap file creation without compression
     */
    @Test
    public void testCreateSitemapFileWithoutCompression() throws Exception {
        String filename = "sitemap.xml";
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset></urlset>";

        try (OutputStream os = sitemapStorageService.createSitemapFile(filename, false)) {
            os.write(content.getBytes("UTF-8"));
        }

        File sitemapFile = new File(tempSitemapDir.toFile(), filename);
        assertTrue("Sitemap file should exist", sitemapFile.exists());
        assertTrue("Should be a file", sitemapFile.isFile());

        String writtenContent = FileUtils.readFileToString(sitemapFile, "UTF-8");
        assertEquals("File content should match", content, writtenContent);
    }

    /**
     * Test sitemap file creation with GZIP compression
     */
    @Test
    public void testCreateSitemapFileWithCompression() throws Exception {
        String filename = "sitemap.xml.gz";
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset></urlset>";

        try (OutputStream os = sitemapStorageService.createSitemapFile(filename, true)) {
            os.write(content.getBytes("UTF-8"));
        }

        File sitemapFile = new File(tempSitemapDir.toFile(), filename);
        assertTrue("Sitemap file should exist", sitemapFile.exists());
        assertTrue("Should be a file", sitemapFile.isFile());

        // Verify the file is actually compressed by reading it with GZIP
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(Files.newInputStream(sitemapFile.toPath()))) {
            byte[] decompressedBytes = gzipInputStream.readAllBytes();
            String decompressedContent = new String(decompressedBytes, "UTF-8");
            assertEquals("Decompressed content should match", content, decompressedContent);
        }
    }

    /**
     * Test index file creation without compression
     */
    @Test
    public void testCreateIndexFileWithoutCompression() throws Exception {
        String filename = "sitemap-index.xml";
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<sitemapindex></sitemapindex>";

        try (OutputStream os = sitemapStorageService.createIndexFile(filename, false)) {
            os.write(content.getBytes("UTF-8"));
        }

        File indexFile = new File(tempSitemapDir.toFile(), filename);
        assertTrue("Index file should exist", indexFile.exists());
        assertTrue("Should be a file", indexFile.isFile());

        String writtenContent = FileUtils.readFileToString(indexFile, "UTF-8");
        assertEquals("File content should match", content, writtenContent);
    }

    /**
     * Test index file creation with GZIP compression
     */
    @Test
    public void testCreateIndexFileWithCompression() throws Exception {
        String filename = "sitemap-index.xml.gz";
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<sitemapindex></sitemapindex>";

        try (OutputStream os = sitemapStorageService.createIndexFile(filename, true)) {
            os.write(content.getBytes("UTF-8"));
        }

        File indexFile = new File(tempSitemapDir.toFile(), filename);
        assertTrue("Index file should exist", indexFile.exists());
        assertTrue("Should be a file", indexFile.isFile());

        // Verify the file is actually compressed
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(Files.newInputStream(indexFile.toPath()))) {
            byte[] decompressedBytes = gzipInputStream.readAllBytes();
            String decompressedContent = new String(decompressedBytes, "UTF-8");
            assertEquals("Decompressed content should match", content, decompressedContent);
        }
    }

    /**
     * Test file existence checking
     */
    @Test
    public void testExists() throws Exception {
        String filename = "test-exists.xml";

        // File shouldn't exist initially
        assertFalse("File should not exist initially", sitemapStorageService.exists(filename));

        // Create the file
        try (OutputStream os = sitemapStorageService.createSitemapFile(filename, false)) {
            os.write("<test></test>".getBytes());
        }

        // Now it should exist
        assertTrue("File should exist after creation", sitemapStorageService.exists(filename));

        // Test with non-existent file
        assertFalse("Non-existent file should return false", sitemapStorageService.exists("nonexistent.xml"));
    }

    /**
     * Test listing sitemap files
     */
    @Test
    public void testListSitemapFiles() throws Exception {
        // Initially should be empty
        String[] files = sitemapStorageService.listSitemapFiles();
        assertThat("Should start with no files", files, is(emptyArray()));

        // Create various sitemap files
        String[] expectedFiles = {
            "sitemap1.xml",
            "sitemap2.xml.gz",
            "sitemap3.html",
            "sitemap4.html.gz"
        };

        for (String filename : expectedFiles) {
            try (OutputStream os = sitemapStorageService.createSitemapFile(filename, filename.endsWith(".gz"))) {
                os.write("<test></test>".getBytes());
            }
        }

        // Create some non-sitemap files that shouldn't be listed
        try (OutputStream os = sitemapStorageService.createSitemapFile("readme.txt", false)) {
            os.write("This is not a sitemap".getBytes());
        }
        try (OutputStream os = sitemapStorageService.createSitemapFile("data.json", false)) {
            os.write("{}".getBytes());
        }

        // List files and verify
        files = sitemapStorageService.listSitemapFiles();
        assertThat("Should list all sitemap files", files, arrayContainingInAnyOrder(expectedFiles));
    }

    /**
     * Test deleting sitemaps
     */
    @Test
    public void testDeleteSitemaps() throws Exception {
        // Create some files
        try (OutputStream os = sitemapStorageService.createSitemapFile("sitemap1.xml", false)) {
            os.write("<sitemap></sitemap>".getBytes());
        }
        try (OutputStream os = sitemapStorageService.createSitemapFile("sitemap2.xml.gz", true)) {
            os.write("<sitemap></sitemap>".getBytes());
        }

        // Verify files exist
        assertTrue("Directory should exist", tempSitemapDir.toFile().exists());
        assertEquals("Should have files", 2, sitemapStorageService.listSitemapFiles().length);

        // Delete sitemaps
        sitemapStorageService.deleteSitemaps();

        // Verify directory is deleted
        assertFalse("Directory should be deleted", tempSitemapDir.toFile().exists());
    }

    /**
     * Test deleting sitemaps when directory doesn't exist
     */
    @Test
    public void testDeleteSitemapsWhenDirectoryDoesntExist() throws Exception {
        // Delete the directory first
        FileUtils.deleteDirectory(tempSitemapDir.toFile());

        // This should not throw an exception
        sitemapStorageService.deleteSitemaps();

        // Directory should still not exist
        assertFalse("Directory should not exist", tempSitemapDir.toFile().exists());
    }

    /**
     * Test operations after directory has been deleted externally
     */
    @Test
    public void testOperationsAfterExternalDirectoryDeletion() throws Exception {
        // Create a file first
        try (OutputStream os = sitemapStorageService.createSitemapFile("initial.xml", false)) {
            os.write("<test></test>".getBytes());
        }

        // Externally delete the directory
        FileUtils.deleteDirectory(tempSitemapDir.toFile());
        assertFalse("Directory should be deleted", tempSitemapDir.toFile().exists());

        // Creating a new file should recreate the directory
        try (OutputStream os = sitemapStorageService.createSitemapFile("after-deletion.xml", false)) {
            os.write("<test></test>".getBytes());
        }

        assertTrue("Directory should be recreated", tempSitemapDir.toFile().exists());
        assertTrue("New file should exist", sitemapStorageService.exists("after-deletion.xml"));
        assertFalse("Previous file should not exist", sitemapStorageService.exists("initial.xml"));
    }

    /**
     * Test file operations with special characters in filenames
     */
    @Test
    public void testSpecialCharactersInFilenames() throws Exception {
        String[] specialFilenames = {
            "sitemap-with-hyphens.xml",
            "sitemap_with_underscores.xml",
            "sitemap.with.dots.xml",
            "sitemap123.xml"
        };

        for (String filename : specialFilenames) {
            try (OutputStream os = sitemapStorageService.createSitemapFile(filename, false)) {
                os.write(("<test>" + filename + "</test>").getBytes());
            }

            assertTrue("File with special characters should exist: " + filename,
                       sitemapStorageService.exists(filename));
        }

        String[] listedFiles = sitemapStorageService.listSitemapFiles();
        assertThat("All special character files should be listed",
                   listedFiles, arrayContainingInAnyOrder(specialFilenames));
    }

    /**
     * Test that operations fail when service is not initialized
     */
    @Test
    public void testOperationsFailWhenNotInitialized() throws Exception {
        FileSystemSitemapStorageService uninitializedService = new FileSystemSitemapStorageService();

        try {
            uninitializedService.createSitemapFile("test.xml", false);
            fail("Should have thrown IllegalStateException for uninitialized service");
        } catch (IllegalStateException e) {
            assertThat("Should contain appropriate error message",
                       e.getMessage(), containsString("has not been initialized"));
        }

        try {
            uninitializedService.exists("test.xml");
            fail("Should have thrown IllegalStateException for uninitialized service");
        } catch (IllegalStateException e) {
            assertThat("Should contain appropriate error message",
                       e.getMessage(), containsString("has not been initialized"));
        }

        try {
            uninitializedService.listSitemapFiles();
            fail("Should have thrown IllegalStateException for uninitialized service");
        } catch (IllegalStateException e) {
            assertThat("Should contain appropriate error message",
                       e.getMessage(), containsString("has not been initialized"));
        }
    }

    /**
     * Test service prevents double initialization
     */
    @Test
    public void testDoubleInitializationPrevention() throws Exception {
        // Service is already initialized in setUp(), trying to initialize again
        // should log a warning but not throw an exception

        ReflectionTestUtils.invokeMethod(sitemapStorageService, "initialize");

        // Service should still be functional
        assertTrue("Service should still be initialized", sitemapStorageService.isInitialized());
        assertThat("Output directory should still be set",
                   sitemapStorageService.getOutputDirectory(), is(notNullValue()));
    }

    /**
     * Test listing files when directory doesn't exist
     */
    @Test
    public void testListFilesWhenDirectoryDoesntExist() throws Exception {
        // Delete the directory
        FileUtils.deleteDirectory(tempSitemapDir.toFile());

        String[] files = sitemapStorageService.listSitemapFiles();
        assertThat("Should return empty array when directory doesn't exist",
                   files, is(emptyArray()));
    }

    /**
     * Test handling of IOException during file operations
     */
    @Test
    public void testIOExceptionHandling() throws Exception {
        // Create a file with the same name as a directory to cause conflicts
        String conflictName = "conflict-test";
        File conflictFile = new File(tempSitemapDir.toFile(), conflictName);

        // Create a regular file first
        FileUtils.write(conflictFile, "This is a file, not a directory", "UTF-8");

        try {
            // Try to create an output stream with a path that conflicts with the file
            sitemapStorageService.createSitemapFile(conflictName + "/nested.xml", false);
            fail("Should have thrown IOException due to path conflict");
        } catch (IOException e) {
            // Expected behavior - the path conflict should cause an IOException
            assertThat("Should contain error information", e.getMessage(), is(not("")));
        }
    }
}