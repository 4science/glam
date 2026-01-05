/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.UUID;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.BundleBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration test for BitstreamServiceImpl.findByMetadataValueInBundle method
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
public class BitstreamServiceImplIT extends AbstractIntegrationTestWithDatabase {

    private BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    private Community owningCommunity;
    private Collection collection;
    private Item testItem;
    private Bundle pdfaBundle;
    private Bundle originalBundle;
    private Bitstream testBitstream;
    private String masterUuid;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        context.turnOffAuthorisationSystem();

        // Create test hierarchy: Community -> Collection -> Item
        owningCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        collection = CollectionBuilder.createCollection(context, owningCommunity)
                                      .withName("Collection 1")
                                      .build();

        testItem = ItemBuilder.createItem(context, collection)
                              .withTitle("Test Item")
                              .withIssueDate("2023-01-01")
                              .build();

        // Create PDFA bundle with proper metadata
        pdfaBundle = BundleBuilder.createBundle(context, testItem)
                                  .withName("PDFA")
                                  .build();

        // Create ORIGINAL bundle for contrast testing
        originalBundle = BundleBuilder.createBundle(context, testItem)
                                      .withName("ORIGINAL")
                                      .build();

        // Generate a test UUID for master metadata
        masterUuid = UUID.randomUUID().toString();

        // Create test bitstream in PDFA bundle with master metadata
        try (InputStream is = new ByteArrayInputStream("Test content".getBytes())) {
            testBitstream = BitstreamBuilder.createBitstream(context, pdfaBundle, is)
                                            .withName("test-pdfa.pdf")
                                            .withMimeType("application/pdf")
                                            .withMetadata("bitstream", "master", null, masterUuid)
                                            .build();
        }

        context.restoreAuthSystemState();
    }

    /**
     * Test finding bitstreams by metadata value in a specific bundle - basic case
     */
    @Test
    public void testFindByMetadataValueInBundle_BasicCase() throws Exception {
        Iterator<Bitstream> results = bitstreamService.findByMetadataValueInBundle(
            context, testItem.getID(), "PDFA", "bitstream.master", masterUuid
        );

        assertThat("Results should not be null", results, notNullValue());
        assertTrue("Should find one bitstream", results.hasNext());

        Bitstream found = results.next();
        assertEquals("Found bitstream should match the test bitstream", testBitstream.getID(), found.getID());
        assertFalse("Should only find one bitstream", results.hasNext());
    }

    /**
     * Test that bitstreams in other bundles are not returned
     */
    @Test
    public void testFindByMetadataValueInBundle_WrongBundle() throws Exception {
        Iterator<Bitstream> results = bitstreamService.findByMetadataValueInBundle(
            context, testItem.getID(), "ORIGINAL", "bitstream.master", masterUuid
        );

        assertThat("Results should not be null", results, notNullValue());
        assertFalse("Should not find bitstreams in different bundle", results.hasNext());
    }

    /**
     * Test with non-existent metadata value
     */
    @Test
    public void testFindByMetadataValueInBundle_NonExistentValue() throws Exception {
        Iterator<Bitstream> results = bitstreamService.findByMetadataValueInBundle(
            context, testItem.getID(), "PDFA", "bitstream.master", "non-existent-uuid"
        );

        assertThat("Results should not be null", results, notNullValue());
        assertFalse("Should not find bitstreams with non-existent metadata value", results.hasNext());
    }

    /**
     * Test with qualified metadata field (schema.element.qualifier)
     */
    @Test
    public void testFindByMetadataValueInBundle_WithQualifier() throws Exception {
        String qualifiedValue = "qualified-test-value";

        context.turnOffAuthorisationSystem();

        // Create bitstream with qualified metadata
        try (InputStream is = new ByteArrayInputStream("Qualified content".getBytes())) {
            Bitstream qualifiedBitstream = BitstreamBuilder.createBitstream(context, pdfaBundle, is)
                                                           .withName("qualified-test.pdf")
                                                           .withMimeType("application/pdf")
                                                           .withMetadata("dc", "identifier", "uri", qualifiedValue)
                                                           .build();
        }

        context.restoreAuthSystemState();

        Iterator<Bitstream> results = bitstreamService.findByMetadataValueInBundle(
            context, testItem.getID(), "PDFA", "dc.identifier.uri", qualifiedValue
        );

        assertThat("Results should not be null", results, notNullValue());
        assertTrue("Should find bitstream with qualified metadata", results.hasNext());

        Bitstream found = results.next();
        assertThat("Found bitstream should not be null", found, notNullValue());
    }

    /**
     * Test with unqualified metadata field (schema.element)
     */
    @Test
    public void testFindByMetadataValueInBundle_WithoutQualifier() throws Exception {
        String unqualifiedValue = "unqualified-test-value";

        context.turnOffAuthorisationSystem();

        // Create bitstream with unqualified metadata
        try (InputStream is = new ByteArrayInputStream("Unqualified content".getBytes())) {
            Bitstream unqualifiedBitstream = BitstreamBuilder.createBitstream(context, pdfaBundle, is)
                                                             .withName("unqualified-test.pdf")
                                                             .withMimeType("application/pdf")
                                                             .withMetadata("dc", "title", null, unqualifiedValue)
                                                             .build();
        }

        context.restoreAuthSystemState();

        Iterator<Bitstream> results = bitstreamService.findByMetadataValueInBundle(
            context, testItem.getID(), "PDFA", "dc.title", unqualifiedValue
        );

        assertThat("Results should not be null", results, notNullValue());
        assertTrue("Should find bitstream with unqualified metadata", results.hasNext());

        Bitstream found = results.next();
        assertThat("Found bitstream should not be null", found, notNullValue());
    }

    /**
     * Test finding multiple bitstreams with the same metadata value
     */
    @Test
    public void testFindByMetadataValueInBundle_MultipleResults() throws Exception {
        context.turnOffAuthorisationSystem();

        // Create another bitstream with same master UUID in same bundle
        try (InputStream is = new ByteArrayInputStream("Another test content".getBytes())) {
            Bitstream anotherBitstream = BitstreamBuilder.createBitstream(context, pdfaBundle, is)
                                                         .withName("another-test.pdf")
                                                         .withMimeType("application/pdf")
                                                         .withMetadata("bitstream", "master", null, masterUuid)
                                                         .build();
        }

        context.restoreAuthSystemState();

        Iterator<Bitstream> results = bitstreamService.findByMetadataValueInBundle(
            context, testItem.getID(), "PDFA", "bitstream.master", masterUuid
        );

        assertThat("Results should not be null", results, notNullValue());
        assertTrue("Should find first bitstream", results.hasNext());
        results.next(); // First bitstream

        assertTrue("Should find second bitstream", results.hasNext());
        results.next(); // Second bitstream

        assertFalse("Should only find two bitstreams", results.hasNext());
    }

    /**
     * Test error case: metadata field with too many parts
     */
    @Test(expected = IllegalArgumentException.class)
    public void testFindByMetadataValueInBundle_TooManyParts() throws Exception {
        bitstreamService.findByMetadataValueInBundle(context, testItem.getID(), "PDFA",
                                                     "schema.element.qualifier.extra", masterUuid);
    }

    /**
     * Test error case: metadata field with only one part
     */
    @Test(expected = IllegalArgumentException.class)
    public void testFindByMetadataValueInBundle_OnePart() throws Exception {
        bitstreamService.findByMetadataValueInBundle(context, testItem.getID(), "PDFA", "invalid", masterUuid);
    }

    /**
     * Test case sensitivity of metadata values
     */
    @Test
    public void testFindByMetadataValueInBundle_CaseSensitivity() throws Exception {
        // Test with different case - should not find anything since database is typically case-sensitive
        Iterator<Bitstream> results = bitstreamService.findByMetadataValueInBundle(
            context, testItem.getID(), "PDFA", "bitstream.master", masterUuid.toUpperCase()
        );

        assertThat("Results should not be null", results, notNullValue());
        assertFalse("Should not find bitstreams with different case UUID", results.hasNext());
    }

    /**
     * Test with non-existent bundle name
     */
    @Test
    public void testFindByMetadataValueInBundle_NonExistentBundle() throws Exception {
        Iterator<Bitstream> results = bitstreamService.findByMetadataValueInBundle(
            context, testItem.getID(), "NON_EXISTENT", "bitstream.master", masterUuid
        );

        assertThat("Results should not be null", results, notNullValue());
        assertFalse("Should not find bitstreams in non-existent bundle", results.hasNext());
    }
}