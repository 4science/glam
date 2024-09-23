/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.checker;

import static org.dspace.app.launcher.ScriptLauncher.handleScript;
import static org.dspace.builder.CollectionBuilder.createCollection;
import static org.dspace.builder.CommunityBuilder.createCommunity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.authorize.AuthorizeException;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.BundleBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.checker.ChecksumResultCode;
import org.dspace.checker.DroidCheckHistory;
import org.dspace.checker.DroidCheckResult;
import org.dspace.checker.DroidResultCode;
import org.dspace.checker.MostRecentChecksum;
import org.dspace.checker.factory.CheckerServiceFactory;
import org.dspace.checker.factory.DroidServiceFactory;
import org.dspace.checker.service.ChecksumHistoryService;
import org.dspace.checker.service.DroidCheckResultService;
import org.dspace.checker.service.DroidHistoryService;
import org.dspace.checker.service.MostRecentChecksumService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.core.Email;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.storage.bitstore.factory.StorageServiceFactory;
import org.dspace.storage.bitstore.service.BitstreamStorageService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class ChecksumCheckerIT extends AbstractIntegrationTestWithDatabase {

    private static final String TEST_OUTPUT = "./target/testing/dspace/assetstore";

    private final DroidCheckResultService droidCheckResultService =
        DroidServiceFactory.getInstance().getDroidCheckResultService();
    private final MostRecentChecksumService mostRecentChecksumService =
        CheckerServiceFactory.getInstance().getMostRecentChecksumService();
    private final BitstreamStorageService storageService =
        StorageServiceFactory.getInstance().getBitstreamStorageService();
    private final DroidHistoryService droidHistoryService =
        DroidServiceFactory.getInstance().getDroidHistoryService();
    private final ChecksumHistoryService checksumHistoryService =
        CheckerServiceFactory.getInstance().getChecksumHistoryService();
    private final ConfigurationService configurationService =
        DSpaceServicesFactory.getInstance().getConfigurationService();
    private Community community;
    private Collection collection;

    private MockedStatic<Email> emailMockedStatic;
    private Email mockedEmail;
    private ArgumentCaptor<File> fileArgumentCaptor;

    @Before
    public void beforeTests() throws SQLException, AuthorizeException {
        emailMockedStatic = mockStatic(Email.class);
        mockedEmail = Mockito.mock(Email.class);
        fileArgumentCaptor = ArgumentCaptor.forClass(File.class);
        emailMockedStatic.when(() -> Email.getEmail(any())).thenReturn(this.mockedEmail);

        // force output folder
        configurationService.setProperty("droid.csv.checksum.outputfile.tempdir", TEST_OUTPUT);

        context.turnOffAuthorisationSystem();
        community = createCommunity(context).build();
        collection = createCollection(context, community).withAdminGroup(eperson).build();
        context.restoreAuthSystemState();
    }

    @After
    public void tearDown() {
        emailMockedStatic.close();
    }

    @Test
    public void testHelpCommand() throws Exception {
        String[] args =
            new String[] {"checksum-checker", "-h"};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        assertThat("Expected infos", handler.getInfoMessages(), not(empty()));
        assertThat("Expected no warnings", handler.getWarningMessages(), empty());

        List<String> errorMessages = handler.getErrorMessages();
        assertThat("Expected no error message", errorMessages, empty());
    }

    @Test
    public void testBitstreamValidation() throws Exception {

        context.turnOffAuthorisationSystem();
        Item item =
            ItemBuilder.createItem(context, collection)
                       .withTitle("Custom Item")
                       .build();

        Bundle bundle =
            BundleBuilder.createBundle(context, item)
                         .withName("ORIGINAL")
                         .build();

        String bitstreamContent = "Dummy content";
        Bitstream bitstream;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, bundle, is)
                                        .withName("Bitstream")
                                        .withMimeType("text/plain")
                                        .withFormat("text")
                                        .build();
        }
        context.restoreAuthSystemState();
        context.commit();


        MostRecentChecksum checksum =
            this.mostRecentChecksumService.findByBitstream(context, bitstream);

        assertThat(checksum, nullValue());

        String[] args =
            new String[] {"checksum-checker", "-b", bitstream.getID().toString()};
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        assertThat("Expected no warnings", handler.getWarningMessages(), empty());
        assertThat("Expected no error message", handler.getErrorMessages(), empty());

        bitstream = context.reloadEntity(bitstream);

        checksum =
            this.mostRecentChecksumService.findByBitstream(context, bitstream);

        assertThat(checksum, not(nullValue()));
        assertThat(checksum.getChecksumResult(), not(nullValue()));
        assertThat(checksum.getChecksumResult().getResultCode(), equalTo(ChecksumResultCode.CHECKSUM_MATCH));


    }

    @Test
    public void testDroidValidation() throws Exception {

        context.turnOffAuthorisationSystem();
        Item item =
            ItemBuilder.createItem(context, collection)
                       .withTitle("Custom Item")
                       .build();

        Bundle bundle =
            BundleBuilder.createBundle(context, item)
                         .withName("ORIGINAL")
                         .build();

        String bitstreamContent = "Dummy content";
        Bitstream bitstream;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, bundle, is)
                                        .withName("Bitstream")
                                        .withMimeType("text/plain")
                                        .withFormat("text")
                                        .build();
        }
        context.restoreAuthSystemState();
        context.commit();


        MostRecentChecksum checksum =
            this.mostRecentChecksumService.findByBitstream(context, bitstream);

        assertThat(checksum, nullValue());

        String[] args =
            new String[] {
                "checksum-checker", "-b", bitstream.getID().toString(), "-D"
            };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        assertThat("Expected no warnings", handler.getWarningMessages(), empty());
        assertThat("Expected no error message", handler.getErrorMessages(), empty());

        bitstream = context.reloadEntity(bitstream);
        Path filePath = Paths.get(storageService.absolutePath(context, bitstream));

        checksum =
            this.mostRecentChecksumService.findByBitstream(context, bitstream);

        assertThat(checksum, not(nullValue()));
        assertThat(checksum.getChecksumResult(), not(nullValue()));
        assertThat(checksum.getChecksumResult().getResultCode(), equalTo(ChecksumResultCode.CHECKSUM_MATCH));
        assertThat(checksum.getDroidCheckStatus().getStatusCode(), equalTo(DroidResultCode.VALIDATED));
        assertThat(checksum.getDroidCheckResults(), is(not(empty())));
        assertThat(checksum.getDroidCheckResults(), hasSize(1));

        DroidCheckResult checkResult = checksum.getDroidCheckResults().get(0);
        assertThat(checkResult, not(nullValue()));

        List<DroidCheckResult> found = this.droidCheckResultService.findBy(context, bitstream);
        assertThat(found, not(nullValue()));
        assertThat(found, hasSize(1));

        DroidCheckResult foundResult = found.get(0);
        assertThat(foundResult.getID(), equalTo(checkResult.getID()));
        assertThat(foundResult.isExtensionMismatch(), equalTo(false));
        assertThat(foundResult.getFileExtension(), equalTo("txt"));
        assertThat(foundResult.getPath(), equalTo(filePath.toString()));
        assertThat(foundResult.getFilename(), equalTo(filePath.getFileName().toString()));
        assertThat(foundResult.getFileFormat(), equalTo("Plain Text File"));
        assertThat(foundResult.getMimeType(), equalTo("text/plain"));
        assertThat(foundResult.getPUID(), equalTo("x-fmt/111"));
        assertThat(foundResult.getType(), equalTo("Extension"));
        assertThat(foundResult.getFileSize(), equalTo(bitstream.getSizeBytes()));
        assertThat(foundResult.getStatus().getStatusCode(), equalTo(DroidResultCode.VALIDATED));

        List<DroidCheckHistory> histories = droidHistoryService.findBy(context, bitstream);
        assertThat(histories, not(nullValue()));
        assertThat(histories, hasSize(1));

        DroidCheckResult historyFound = found.get(0);
        assertThat(historyFound.isExtensionMismatch(), equalTo(foundResult.isExtensionMismatch()));
        assertThat(historyFound.getFileExtension(), equalTo(foundResult.getFileExtension()));
        assertThat(historyFound.getPath(), equalTo(foundResult.getPath()));
        assertThat(historyFound.getFilename(), equalTo(foundResult.getFilename()));
        assertThat(historyFound.getFileFormat(), equalTo(foundResult.getFileFormat()));
        assertThat(historyFound.getMimeType(), equalTo(foundResult.getMimeType()));
        assertThat(historyFound.getPUID(), equalTo(foundResult.getPUID()));
        assertThat(historyFound.getType(), equalTo(foundResult.getType()));
        assertThat(historyFound.getFileSize(), equalTo(foundResult.getFileSize()));
        assertThat(historyFound.getStatus().getStatusCode(), equalTo(foundResult.getStatus().getStatusCode()));

        emailMockedStatic.verify(() -> Email.getEmail(any()), times(1));
        verify(mockedEmail, times(1))
            .addAttachment(fileArgumentCaptor.capture(), any(String.class));

        File email = fileArgumentCaptor.getValue();

        assertThat(email, not(nullValue()));
        assertThat(FilenameUtils.getExtension(email.getPath()), equalTo("csv"));
        assertThat(Long.valueOf(email.length()), is(greaterThan(0L)));

        try (
            InputStream is = new FileInputStream(email);
            InputStreamReader ir = new InputStreamReader(is, "UTF-8");
            BufferedReader reader = new BufferedReader(ir);
        ) {
            String line = reader.readLine();
            assertIsCsvHeader(line);
            line = reader.readLine();
            assertThat(
                line,
                allOf(
                    containsString(bitstream.getID().toString()),
                    containsString(foundResult.getID().toString()),
                    containsString(String.valueOf(foundResult.getURI())),
                    containsString(String.valueOf(foundResult.getPath())),
                    containsString(foundResult.getFilename()),
                    containsString("file"),
                    containsString(Optional.ofNullable(foundResult.getStatus())
                                    .map(status -> status.getStatusCode().toString())
                                    .orElse("")),
                    containsString(String.valueOf(foundResult.getFileSize())),
                    containsString(foundResult.getType()),
                    containsString(foundResult.getFileExtension()),
                    containsString(String.valueOf(foundResult.getLastModifiedDate())),
                    containsString(String.valueOf(foundResult.isExtensionMismatch())),
                    containsString(checksum.getCurrentChecksum()),
                    containsString("1"),
                    containsString(foundResult.getPUID()),
                    containsString(foundResult.getMimeType()),
                    containsString(foundResult.getFileFormat()),
                    containsString(Optional.ofNullable(foundResult.getFormatVersion()).orElse(""))
                )
            );
            line = reader.readLine();
            assertThat(line, nullValue());
        }
    }

    @Test
    public void testDroidCsvCollector() throws Exception {

        context.turnOffAuthorisationSystem();
        Item item =
            ItemBuilder.createItem(context, collection)
                       .withTitle("Custom Item")
                       .build();

        Bundle bundle =
            BundleBuilder.createBundle(context, item)
                         .withName("ORIGINAL")
                         .build();

        String bitstreamContent = "Dummy content";
        Bitstream bitstream;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, bundle, is)
                                        .withName("Bitstream")
                                        .withMimeType("text/plain")
                                        .withFormat("text")
                                        .build();
        }
        bitstreamContent = "Custom dummy content";
        Bitstream bitstream1;
        try (InputStream is = IOUtils.toInputStream(bitstreamContent, CharEncoding.UTF_8)) {
            bitstream1 = BitstreamBuilder.createBitstream(context, bundle, is)
                                        .withName("Bitstream1")
                                        .withMimeType("text/plain")
                                        .withFormat("text")
                                        .build();
        }
        context.restoreAuthSystemState();
        context.commit();

        MostRecentChecksum checksum =
            this.mostRecentChecksumService.findByBitstream(context, bitstream);
        MostRecentChecksum checksum1 =
            this.mostRecentChecksumService.findByBitstream(context, bitstream1);

        assertThat(checksum, nullValue());
        assertThat(checksum1, nullValue());

        String[] args =
            new String[] {
                "checksum-checker",
                "-b", bitstream.getID().toString(),
                "-b", bitstream1.getID().toString(),
                "-D"
            };
        TestDSpaceRunnableHandler handler = new TestDSpaceRunnableHandler();

        handleScript(args, ScriptLauncher.getConfig(kernelImpl), handler, kernelImpl, eperson);
        assertThat("Expected no warnings", handler.getWarningMessages(), empty());
        assertThat("Expected no error message", handler.getErrorMessages(), empty());

        bitstream = context.reloadEntity(bitstream);
        bitstream1 = context.reloadEntity(bitstream1);

        checksum =
            this.mostRecentChecksumService.findByBitstream(context, bitstream);

        assertThat(checksum, not(nullValue()));
        assertThat(checksum.getChecksumResult(), not(nullValue()));
        assertThat(checksum.getChecksumResult().getResultCode(), equalTo(ChecksumResultCode.CHECKSUM_MATCH));
        assertThat(checksum.getDroidCheckStatus().getStatusCode(), equalTo(DroidResultCode.VALIDATED));
        assertThat(checksum.getDroidCheckResults(), is(not(empty())));
        assertThat(checksum.getDroidCheckResults(), hasSize(1));

        DroidCheckResult checkResult = checksum.getDroidCheckResults().get(0);
        assertThat(checkResult, not(nullValue()));

        checksum1 =
            this.mostRecentChecksumService.findByBitstream(context, bitstream1);

        assertThat(checksum1, not(nullValue()));
        assertThat(checksum1.getChecksumResult(), not(nullValue()));
        assertThat(checksum1.getChecksumResult().getResultCode(), equalTo(ChecksumResultCode.CHECKSUM_MATCH));
        assertThat(checksum1.getDroidCheckStatus().getStatusCode(), equalTo(DroidResultCode.VALIDATED));
        assertThat(checksum1.getDroidCheckResults(), is(not(empty())));
        assertThat(checksum1.getDroidCheckResults(), hasSize(1));

        DroidCheckResult checkResult1 = checksum1.getDroidCheckResults().get(0);
        assertThat(checkResult1, not(nullValue()));

        emailMockedStatic.verify(() -> Email.getEmail(any()), times(1));
        verify(mockedEmail, times(1))
            .addAttachment(fileArgumentCaptor.capture(), any(String.class));

        File email = fileArgumentCaptor.getValue();

        assertThat(email, not(nullValue()));
        assertThat(FilenameUtils.getExtension(email.getPath()), equalTo("csv"));
        assertThat(Long.valueOf(email.length()), is(greaterThan(0L)));

        try (
            InputStream is = new FileInputStream(email);
            InputStreamReader ir = new InputStreamReader(is, "UTF-8");
            BufferedReader reader = new BufferedReader(ir);
        ) {
            // header
            String line = reader.readLine();
            assertIsCsvHeader(line);
            // first line
            line = reader.readLine();
            assertLine(line, bitstream, checkResult, checksum);
            // second line
            line = reader.readLine();
            assertLine(line, bitstream1, checkResult1, checksum1);
            // end of line
            line = reader.readLine();
            assertThat(line, nullValue());
        }
    }

    private static void assertLine(String line, Bitstream bitstream, DroidCheckResult checkResult,
                                  MostRecentChecksum checksum) {
        assertThat(
            line,
            allOf(
                containsString(bitstream.getID().toString()),
                containsString(checkResult.getID().toString()),
                containsString(checkResult.getURI().toString()),
                containsString(checkResult.getPath()),
                containsString(checkResult.getFilename()),
                containsString("file"),
                containsString(checkResult.getStatus().getStatusCode().name()),
                containsString(checkResult.getFileSize().toString()),
                containsString(checkResult.getType()),
                containsString(checkResult.getFileExtension()),
                containsString(String.valueOf(checkResult.getLastModifiedDate())),
                containsString(String.valueOf(checkResult.isExtensionMismatch())),
                containsString(checksum.getCurrentChecksum()),
                containsString("1"),
                containsString(checkResult.getPUID()),
                containsString(checkResult.getMimeType()),
                containsString(checkResult.getFileFormat()),
                containsString(
                    Optional.ofNullable(checkResult.getFormatVersion())
                        .map(String::valueOf)
                        .orElse("")
                )
            )
        );
    }

    public static void assertIsCsvHeader(String line) {
        assertThat(
            line,
            allOf(
                containsString("bitstream_id"),
                containsString("id"),
                containsString("uri"),
                containsString("filepath"),
                containsString("filename"),
                containsString("method"),
                containsString("status"),
                containsString("filesize"),
                containsString("type"),
                containsString("file_extension"),
                containsString("last_modified_date"),
                containsString("extension_mismatch"),
                containsString("hash"),
                containsString("file_format_count"),
                containsString("PUID"),
                containsString("mime_type"),
                containsString("file_format_name"),
                containsString("file_format_version")
            )
        );
    }
}
