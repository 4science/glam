/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.checker;

import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.checker.MostRecentChecksum;
import org.dspace.checker.factory.CheckerServiceFactory;
import org.dspace.checker.service.ChecksumHistoryService;
import org.dspace.checker.service.MostRecentChecksumService;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for {@link ChecksumCheckerScript}
 *
 */
public class ChecksumCheckerScriptIT extends AbstractIntegrationTestWithDatabase {
    private MostRecentChecksumService mostRecentChecksumService;
    private ChecksumHistoryService checksumHistoryService;
    private Collection collection;

    @Before
    public void setup() throws Exception {
        context.turnOffAuthorisationSystem();

        mostRecentChecksumService = CheckerServiceFactory.getInstance().getMostRecentChecksumService();

        checksumHistoryService = CheckerServiceFactory.getInstance().getChecksumHistoryService();

        parentCommunity = CommunityBuilder.createCommunity(context)
                .build();

        collection = CollectionBuilder.createCollection(context, parentCommunity)
                .build();

        context.restoreAuthSystemState();
    }

    @Test
    public void testShouldAddChecksumHistoryOnlyForBitstreamWithPassedId() throws Exception {
        context.turnOffAuthorisationSystem();
        Item item = createItem("Test", collection);
        Item item2 = createItem("Test 2", collection);
        Bitstream bitstream = createBitstream(item, new ByteArrayInputStream(new byte[0]));
        Bitstream bitstream2 = createBitstream(item2, new ByteArrayInputStream(new byte[0]));
        context.restoreAuthSystemState();

        String[] args =
                new String[] {
                        "checksum-checker",
                        "-h",
                        "-b", bitstream.getID().toString(),
                };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertEquals(handler.getErrorMessages().size(), 0);

        List<MostRecentChecksum> mostRecentChecksums = mostRecentChecksumService.findNotInHistory(context);

        assertEquals(mostRecentChecksums.size(), 1);
        assertEquals(mostRecentChecksums.get(0).getBitstream().getID(), bitstream2.getID());

        // Remove checksum and history to prevent foreign key exception
        checksumHistoryService.deleteByBitstream(context, bitstream);
        mostRecentChecksumService.deleteByBitstream(context, bitstream);
        mostRecentChecksumService.deleteByBitstream(context, bitstream2);
    }

    @Test
    public void testShouldAddChecksumHistoryForBothPassedBitstreams() throws Exception {
        context.turnOffAuthorisationSystem();
        Item item = createItem("Test", collection);
        Item item2 = createItem("Test 2", collection);
        Bitstream bitstream = createBitstream(item, new ByteArrayInputStream(new byte[0]));
        Bitstream bitstream2 = createBitstream(item2, new ByteArrayInputStream(new byte[0]));
        context.restoreAuthSystemState();

        String[] args =
                new String[] {
                        "checksum-checker",
                        "-h",
                        "-b", bitstream.getID().toString(),
                        "-b", bitstream2.getID().toString(),
                };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertEquals(handler.getErrorMessages().size(), 0);

        List<MostRecentChecksum> mostRecentChecksums = mostRecentChecksumService.findNotInHistory(context);

        assertEquals(mostRecentChecksums.size(), 0);

        // Remove checksum and history to prevent foreign key exception
        checksumHistoryService.deleteByBitstream(context, bitstream);
        checksumHistoryService.deleteByBitstream(context, bitstream2);
        mostRecentChecksumService.deleteByBitstream(context, bitstream);
        mostRecentChecksumService.deleteByBitstream(context, bitstream2);
    }

    @Test
    public void testShouldAddChecksumHistoryOnlyForBitstreamFromCollectionWithPassedId() throws Exception {
        context.turnOffAuthorisationSystem();
        Item item = createItem("Test", collection);
        Collection secondCollection = CollectionBuilder.createCollection(context, parentCommunity)
                .build();
        Item item2 = createItem("Test 2", secondCollection);
        Bitstream bitstream = createBitstream(item, new ByteArrayInputStream(new byte[0]));
        Bitstream bitstream2 = createBitstream(item2, new ByteArrayInputStream(new byte[0]));
        context.restoreAuthSystemState();

        String[] args =
                new String[] {
                        "checksum-checker",
                        "-a", collection.getHandle()
                };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertEquals(handler.getErrorMessages().size(), 0);

        List<MostRecentChecksum> mostRecentChecksums = mostRecentChecksumService.findNotInHistory(context);

        assertEquals(mostRecentChecksums.size(), 1);
        assertEquals(mostRecentChecksums.get(0).getBitstream().getID(), bitstream2.getID());

        // Remove checksum and history to prevent foreign key exception
        checksumHistoryService.deleteByBitstream(context, bitstream);
        mostRecentChecksumService.deleteByBitstream(context, bitstream);
        mostRecentChecksumService.deleteByBitstream(context, bitstream2);
    }

    private Item createItem(String name, Collection collection) {
        return ItemBuilder.createItem(context, collection)
                .withTitle(name)
                .build();
    }

    private Bitstream createBitstream(Item item, InputStream is) throws SQLException, AuthorizeException, IOException {
        return BitstreamBuilder.createBitstream(context, item, is)
                .build();
    }
}
