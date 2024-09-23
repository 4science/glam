/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.checker;

import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.checker.CSVChecksumResultMailCollector;
import org.dspace.checker.CSVDroidChecksumCollector;
import org.dspace.checker.ChecksumResultCode;
import org.dspace.checker.DroidCheckResult;
import org.dspace.checker.MostRecentChecksum;
import org.dspace.checker.factory.CheckerServiceFactory;
import org.dspace.checker.service.ChecksumHistoryService;
import org.dspace.checker.service.MostRecentChecksumService;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamFormatService;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Integration tests for {@link ChecksumCheckerScript}
 *
 */
public class ChecksumCheckerScriptIT extends AbstractIntegrationTestWithDatabase {

    private static final String TEST_FILE = "./target/testing/dspace/assetstore/droid/empty.pdf";

    private MostRecentChecksumService mostRecentChecksumService;
    private ChecksumHistoryService checksumHistoryService;
    private Collection collection;
    private BitstreamService bitstreamService;
    private BitstreamFormatService bitstreamFormatService;

    private static Matcher<Iterable<? extends String>> headerMatcher() {
        return contains(
            equalTo("bitstream"),
            equalTo("filename"),
            equalTo("item"),
            equalTo("item title"),
            equalTo("collection"),
            equalTo("original checksum"),
            equalTo("validation checksum")
        );
    }

    private static Matcher<Iterable<? extends String>> droidHeaderMatch() {
        return contains(
            equalTo("bitstream_id"),
            equalTo("id"),
            equalTo("uri"),
            equalTo("filepath"),
            equalTo("filename"),
            equalTo("method"),
            equalTo("status"),
            equalTo("filesize"),
            equalTo("type"),
            equalTo("file_extension"),
            equalTo("last_modified_date"),
            equalTo("extension_mismatch"),
            equalTo("hash"),
            equalTo("file_format_count"),
            equalTo("PUID"),
            equalTo("mime_type"),
            equalTo("file_format_name"),
            equalTo("file_format_version")
        );
    }

    private static Matcher<Iterable<? extends String>> droidMatcher(
        MostRecentChecksum mrc, DroidCheckResult droidCheckResult
    ) {
        return contains(
            equalTo(mrc.getBitstream().getID().toString()),
            equalTo(droidCheckResult.getID().toString()),
            equalTo(String.valueOf(droidCheckResult.getURI())),
            equalTo(String.valueOf(droidCheckResult.getPath())),
            equalTo(droidCheckResult.getFilename()),
            equalTo("file"),
            equalTo(Optional.ofNullable(droidCheckResult.getStatus())
                            .map(status -> status.getStatusCode().toString())
                            .orElse("")),
            equalTo(String.valueOf(droidCheckResult.getFileSize())),
            equalTo(droidCheckResult.getType()),
            equalTo(droidCheckResult.getFileExtension()),
            equalTo(String.valueOf(droidCheckResult.getLastModifiedDate())),
            equalTo(String.valueOf(droidCheckResult.isExtensionMismatch())),
            equalTo(mrc.getCurrentChecksum()),
            equalTo("1"),
            equalTo(droidCheckResult.getPUID()),
            equalTo(droidCheckResult.getMimeType()),
            equalTo(droidCheckResult.getFileFormat()),
            equalTo(Optional.ofNullable(droidCheckResult.getFormatVersion()).orElse(""))
        );
    }

    private static Matcher<Iterable<? extends String>> checksumMatcher(
        Bitstream bs, Item item, MostRecentChecksum mrc
    ) {
        return contains(
            equalTo(bs.getID().toString()),
            equalTo(Optional.ofNullable(bs.getName()).or(() -> Optional.ofNullable(bs.getSource())).orElse("")),
            equalTo(item.getID().toString()),
            equalTo(item.getName()),
            equalTo(item.getOwningCollection().getID().toString()),
            equalTo(bs.getChecksum()),
            equalTo(mrc.getCurrentChecksum())
        );
    }

    private static Matcher<MostRecentChecksum> resultCodeMatcher(ChecksumResultCode resultCode) {
        return hasProperty(
            "checksumResult",
            is(hasProperty("resultCode", equalTo(resultCode)))
        );
    }

    @Before
    public void setup() throws Exception {
        context.turnOffAuthorisationSystem();

        mostRecentChecksumService = CheckerServiceFactory.getInstance().getMostRecentChecksumService();

        checksumHistoryService = CheckerServiceFactory.getInstance().getChecksumHistoryService();
        bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
        bitstreamFormatService = ContentServiceFactory.getInstance().getBitstreamFormatService();

        parentCommunity = CommunityBuilder.createCommunity(context)
                .build();

        collection = CollectionBuilder.createCollection(context, parentCommunity)
                .build();

        context.restoreAuthSystemState();
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
                        "-b", bitstream.getID().toString(),
                };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertEquals(handler.getErrorMessages().size(), 0);

        List<MostRecentChecksum> mostRecentChecksums = mostRecentChecksumService.findNotInHistory(context);

        assertEquals(mostRecentChecksums.size(), 1);
        assertEquals(mostRecentChecksums.get(0).getBitstream().getID(), bitstream2.getID());

        checksumHistoryService.deleteByBitstream(context, bitstream);
        checksumHistoryService.deleteByBitstream(context, bitstream2);
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

        checksumHistoryService.deleteByBitstream(context, bitstream);
        checksumHistoryService.deleteByBitstream(context, bitstream2);
    }

    @Test
    public void testDroidValidation() throws Exception {
        context.turnOffAuthorisationSystem();

        Item item = createItem("Test", collection);
        Item item2 = createItem("Test 2", collection);
        Path tempFile = Paths.get(TEST_FILE);
        Bitstream bitstream =
                BitstreamBuilder.createBitstream(context, item, new FileInputStream(tempFile.toFile()))
                                .withName("test.pdf")
                                .withFormat("pdf")
                                .build();
        bitstreamService.setFormat(context, bitstream, bitstreamFormatService.guessFormat(context, bitstream));

        Bitstream bitstream2 =
                BitstreamBuilder.createBitstream(context, item2, new FileInputStream(tempFile.toFile()))
                                .withName("test.doc")
                                .withFormat("application/msword")
                                .build();
        bitstreamService.setFormat(context, bitstream2, bitstreamFormatService.guessFormat(context, bitstream2));

        context.restoreAuthSystemState();

        String[] args =
            new String[] {
                "checksum-checker",
                "-a", collection.getHandle(),
                "-D"
            };

        TestDSpaceRunnableHandler handler = Mockito.spy(new TestDSpaceRunnableHandler());
        ArgumentCaptor<String> fileCaptor = ArgumentCaptor.forClass(String.class);

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        assertThat(handler.getInfoMessages().size(), is(2));
        assertThat(
            handler.getInfoMessages(),
            containsInAnyOrder(
                containsString(bitstream.getID().toString()),
                containsString(bitstream2.getID().toString())
            )
        );
        assertEquals(handler.getErrorMessages().size(), 0);

        Mockito.verify(handler)
               .writeFilestream(
                   Mockito.any(Context.class),
                   fileCaptor.capture(),
                   Mockito.any(InputStream.class),
                   Mockito.eq("csv")
               );
        String fileName = fileCaptor.getValue();

        assertThat(fileName, containsString(CSVDroidChecksumCollector.getPrefix()));
        List<List<String>> records;
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(fileName))) {
            records =
                reader.lines()
                      .map(line -> Arrays.asList(line.split(CSVDroidChecksumCollector.getFieldsSeparator())))
                      .collect(Collectors.toList());
        }

        List<String> header = records.remove(0);
        assertThat(header, droidHeaderMatch());

        MostRecentChecksum mrc = mostRecentChecksumService.findByBitstream(context, bitstream);
        MostRecentChecksum mrc2 = mostRecentChecksumService.findByBitstream(context, bitstream2);

        assertThat(records,
                   containsInAnyOrder(
                       droidMatcher(mrc, mrc.getDroidCheckResults().get(0)),
                       droidMatcher(mrc2, mrc2.getDroidCheckResults().get(0))
                   )
        );

        checksumHistoryService.deleteByBitstream(context, bitstream);
        checksumHistoryService.deleteByBitstream(context, bitstream2);
    }

    @Test
    public void testMailReporter() throws Exception {
        context.turnOffAuthorisationSystem();
        Item item = createItem("Test", collection);
        Item item2 = createItem("Test 2", collection);
        Bitstream bitstream = createBitstream(item, "wrong_checksum", new ByteArrayInputStream(new byte[0]));
        Bitstream bitstream2 = createBitstream(item2, "wrong_checksum", new ByteArrayInputStream(new byte[0]));
        context.restoreAuthSystemState();

        String[] args =
            new String[] {
                "checksum-checker",
                "-a", collection.getHandle(),
                "-m"
            };
        TestDSpaceRunnableHandler handler = Mockito.spy(new TestDSpaceRunnableHandler());
        ArgumentCaptor<String> fileCaptor = ArgumentCaptor.forClass(String.class);


        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);

        Mockito.verify(handler)
               .writeFilestream(
                   Mockito.any(Context.class),
                   fileCaptor.capture(),
                   Mockito.any(InputStream.class),
                   Mockito.eq("csv")
               );
        String fileName = fileCaptor.getValue();

        assertThat(fileName, containsString(CSVChecksumResultMailCollector.getPrefix()));

        List<List<String>> records;
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(fileName))) {
            records =
                reader.lines()
                      .map(line -> Arrays.asList(line.split(CSVChecksumResultMailCollector.getFieldsSeparator())))
                      .collect(Collectors.toList());
        }

        assertThat(records, notNullValue());
        assertThat(records.size(), is(3));

        List<String> headerLine = records.remove(0);
        assertThat(headerLine.size(), is(7));
        assertThat(headerLine, headerMatcher());
        MostRecentChecksum mrc = mostRecentChecksumService.findByBitstream(context, bitstream);
        MostRecentChecksum mrc2 = mostRecentChecksumService.findByBitstream(context, bitstream2);
        assertThat(
            records,
            containsInAnyOrder(
                checksumMatcher(bitstream, item, mrc),
                checksumMatcher(bitstream2, item2, mrc2)
            )
        );

        assertThat(mrc, resultCodeMatcher(ChecksumResultCode.CHECKSUM_NO_MATCH));
        assertThat(mrc2, resultCodeMatcher(ChecksumResultCode.CHECKSUM_NO_MATCH));

        assertThat(handler.getInfoMessages().size(), is(2));
        assertThat(
            handler.getInfoMessages(),
            containsInAnyOrder(
                containsString(bitstream.getID().toString()),
                containsString(bitstream2.getID().toString())
            )
        );
        assertEquals(handler.getErrorMessages().size(), 0);

        List<MostRecentChecksum> mostRecentChecksums = mostRecentChecksumService.findNotInHistory(context);

        assertEquals(mostRecentChecksums.size(), 0);

        checksumHistoryService.deleteByBitstream(context, bitstream);
        checksumHistoryService.deleteByBitstream(context, bitstream2);
    }


    private Item createItem(String name, Collection collection) {
        return ItemBuilder.createItem(context, collection)
                .withTitle(name)
                .build();
    }

    private Bitstream createBitstream(Item item, InputStream is) throws Exception {
        return BitstreamBuilder.createBitstream(context, item, is)
                .build();
    }

    private Bitstream createBitstream(Item item, String checksum, InputStream is) throws Exception {
        return BitstreamBuilder.createBitstream(context, item, is)
                               .withChecksum(checksum)
                               .build();
    }

}
