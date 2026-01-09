/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.general;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.curate.service.CurationTaskResult;
import org.dspace.storage.bitstore.BitStoreService;
import org.dspace.storage.bitstore.BitstreamStorageServiceImpl;
import org.dspace.storage.bitstore.S3BitStoreService;
import org.junit.Test;

/**
 * Rudimentary test of the PdfACurationTask curation task.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class PdfACurationTaskIT extends AbstractIntegrationTestWithDatabase {

    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    @Test
    public void testGetRelatedBundle() {
        PdfACurationTask task = new PdfACurationTask();
        String relatedBundle = task.getRelatedBundle();
        assertEquals("PDFA", relatedBundle);
    }

    @Test
    public void testGetProcessableBitstreams() throws Exception {
        context.turnOffAuthorisationSystem();
        Community community = CommunityBuilder.createCommunity(context)
                                              .withName("Test Community")
                                              .build();

        Collection collection = CollectionBuilder.createCollection(context, community)
                                                 .withName("Test Collection")
                                                 .build();

        Item item = ItemBuilder.createItem(context, collection)
                               .withTitle("Test PDF Item")
                               .build();

        String pdfContent = "PDF test content";
        String txtContent = "This is a text file";

        try (InputStream is = IOUtils.toInputStream(pdfContent, "UTF-8");
             InputStream is2 = IOUtils.toInputStream(txtContent, "UTF-8")) {
            BitstreamBuilder.createBitstream(context, item, is)
                            .withName("test.pdf")
                            .withMimeType("application/pdf")
                            .withStoreNumber(4)
                            .build();
            BitstreamBuilder.createBitstream(context, item, is2)
                            .withName("test.txt")
                            .withStoreNumber(4)
                            .withMimeType("text/plain")
                            .build();
        }

        context.restoreAuthSystemState();
        Map<Integer, BitStoreService> stores = new HashMap<>();
        stores.put(4, mock(S3BitStoreService.class));

        BitstreamStorageServiceImpl bitstreamStorageServiceMock = mock(BitstreamStorageServiceImpl.class);
        when(bitstreamStorageServiceMock.getStores()).thenReturn(stores);

        PdfACurationTask task = new PdfACurationTask();
        task.init(null, "pdfATransformer");
        task.setBitstreamStorageService(bitstreamStorageServiceMock);

        List<Bitstream> processableBitstreams = task.getProcessableBitstreams(context, "pdfATransformer", item);
        assertEquals(1, processableBitstreams.size());
    }

    @Test
    public void testFinalizeTask() throws Exception {
        context.turnOffAuthorisationSystem();
        Community community = CommunityBuilder.createCommunity(context)
                                              .withName("Test Community")
                                              .build();
        Collection collection = CollectionBuilder.createCollection(context, community)
                                                 .withName("Test Collection")
                                                 .build();
        Item item = ItemBuilder.createItem(context, collection)
                               .withTitle("Test Item")
                               .build();

        Bitstream testBitstream = null;
        String testContent = "Test PDF/A content";
        try (InputStream is = IOUtils.toInputStream(testContent, "UTF-8")) {
            testBitstream = bitstreamService.create(context, is);
            testBitstream.setSequenceID(1);
            testBitstream.setName(context, "test-pdfa.pdf");

            List<Bundle> pdfaBundles = itemService.getBundles(item, "PDFA");
            assertEquals(0, pdfaBundles.size());

            PdfACurationTask task = new PdfACurationTask();
            task.init(null, "pdfATransformer");

            // Create test CurationTaskResult
            CurationTaskResult taskResult = CurationTaskResult.success("pdfATransformer", UUID.randomUUID(),
                    List.of(testBitstream));
            // Test finalizeTask
            task.finalizeTask(context, item, taskResult);

            pdfaBundles = itemService.getBundles(item, "PDFA");
            // Verify that the PDFA bundle has been created and contains the test bitstream
            assertEquals(1, pdfaBundles.size());
            // Verify that the PDFA bundle contains the test bitstream
            assertEquals(1, pdfaBundles.get(0).getBitstreams().size());
            // Verify that the bitstream in the PDFA bundle is the test bitstream
            assertEquals(testBitstream.getName(), pdfaBundles.get(0).getBitstreams().get(0).getName());
        } finally {
            if (testBitstream != null ) {
                bitstreamService.delete(context, testBitstream);
            }
        }
        context.restoreAuthSystemState();
    }

    @Test
    public void testGetProcessableBitstreamsWithEmptyItem() throws Exception {
        context.turnOffAuthorisationSystem();
        Community community = CommunityBuilder.createCommunity(context)
                                              .withName("Test Community")
                                              .build();

        Collection collection = CollectionBuilder.createCollection(context, community)
                                                 .withName("Test Collection")
                                                 .build();

        Item emptyItem = ItemBuilder.createItem(context, collection)
                                    .withTitle("Empty Item")
                                    .build();

        context.restoreAuthSystemState();

        PdfACurationTask task = new PdfACurationTask();
        task.init(null, "pdfATransformer");

        List<Bitstream> processableBitstreams = task.getProcessableBitstreams(context, "pdfATransformer", emptyItem);
        assertEquals(0, processableBitstreams.size());
    }

}
