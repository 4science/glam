/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.mail.MessagingException;

import org.dspace.checker.EvaluationContext.EvaluationContextMapper;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class CsvDroidChecksumCollector implements ChecksumResultsCollector {

    private static final Logger log = LoggerFactory.getLogger(CsvDroidChecksumCollector.class);
    private final ConfigurationService configurationService =
        DSpaceServicesFactory.getInstance().getConfigurationService();
    private String filePath;
    private File outputFile;
    private String dateSuffix;

    private Map<String, EvaluationContextMapper> rowMappers =
        Stream.of(
            Map.entry("bitstream_id", (EvaluationContextMapper) (ec) -> ec.checksum.getBitstream().getID().toString()),
            Map.entry("id", (EvaluationContextMapper) (ec) -> ec.droidCheckResult.getID().toString()),
            Map.entry("uri",
                      (EvaluationContextMapper) (ec) -> ec.droidCheckResult.getURI()),
            Map.entry("filepath",
                      (EvaluationContextMapper) (ec) -> ec.droidCheckResult.getPath()),
            Map.entry("filename",
                      (EvaluationContextMapper) (ec) -> ec.droidCheckResult.getFilename()),
            Map.entry("method",
                      (EvaluationContextMapper) (ec) -> "file"),
            Map.entry("status",
                      (EvaluationContextMapper) (ec) ->
                          Optional.ofNullable(ec.droidCheckResult.getStatus())
                                  .map(status -> status.getStatusCode().toString())
                                  .orElse(null)
            ),
            Map.entry("filesize",
                      (EvaluationContextMapper) (ec) -> String.valueOf(ec.droidCheckResult.getFileSize())),
            Map.entry("type",
                      (EvaluationContextMapper) (ec) -> ec.droidCheckResult.getType()),
            Map.entry("file_extension",
                      (EvaluationContextMapper) (ec) -> ec.droidCheckResult.getFileExtension()),
            Map.entry("last_modified_date",
                      (EvaluationContextMapper) (ec) -> String.valueOf(ec.droidCheckResult.getLastModifiedDate())
            ),
            Map.entry("extension_mismatch",
                      (EvaluationContextMapper) (ec) -> String.valueOf(ec.droidCheckResult.isExtensionMismatch())),
            Map.entry("hash",
                      (EvaluationContextMapper) (ec) -> ec.checksum.getCurrentChecksum()),
            Map.entry("file_format_count", (EvaluationContextMapper) (info) -> "1"),
            Map.entry("PUID",
                      (EvaluationContextMapper) (ec) -> ec.droidCheckResult.getPUID()),
            Map.entry("mime_type",
                      (EvaluationContextMapper) (ec) -> ec.droidCheckResult.getMimeType()),
            Map.entry("file_format_name",
                      (EvaluationContextMapper) (ec) -> ec.droidCheckResult.getFileFormat()),
            Map.entry("file_format_version",
                      (EvaluationContextMapper) (ec) -> ec.droidCheckResult.getFormatVersion())

        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    public CsvDroidChecksumCollector() {
        this(null);
    }

    public CsvDroidChecksumCollector(String outputFile) {
        this.filePath = outputFile;
    }

    private static String getDateSuffix() {
        return LocalDateTime
            .ofInstant(Instant.now(), ZoneOffset.UTC)
            .format(
                DateTimeFormatter
                    .ofPattern("yyyy-MM-dd_HH_mm")
                    .withZone(ZoneOffset.UTC)
            );
    }

    private String getPrefix() {
        return configurationService.getProperty("droid.csv.checksum.outputfile.prefix");
    }

    private String getTempDir() {
        return configurationService.getProperty("droid.csv.checksum.outputfile.tempdir");
    }

    private String getFieldsSeparator() {
        return configurationService.getProperty("droid.csv.checksum.separator");
    }

    @Override
    public void collect(Context context, MostRecentChecksum info) throws SQLException {
        if (this.outputFile == null) {
            this.dateSuffix = getDateSuffix();
            try {
                Path directoryPath = getDirectoryPath();
                File tempDirectory = directoryPath.toFile();
                if (!tempDirectory.exists()) {
                    tempDirectory = Files.createDirectory(directoryPath).toFile();
                }
                this.outputFile =
                    File.createTempFile(
                        getPrefix() + "_",
                        "_" + dateSuffix + ".csv",
                        tempDirectory
                    );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (!this.outputFile.exists()) {
            log.error("Cannot open find the csv file for the export!");
            return;
        }
        try (
            FileWriter fw = new FileWriter(outputFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter pw = new PrintWriter(bw);
        ) {
            if (outputFile.length() == 0) {
                pw.println(String.join(getFieldsSeparator(), getHeader()));
            }
            for (Collection<String> row : rows(info)) {
                pw.println(String.join(getFieldsSeparator(), row));
            }
        } catch (IOException e) {
            log.error("Cannot add elements to the configured output file!", e);
            throw new RuntimeException(e);
        }
    }

    private Path getDirectoryPath() {
        return Paths.get(Optional.ofNullable(this.filePath).orElseGet(() -> getTempDir()));
    }

    @Override
    public void complete(Context context) {
        if (this.outputFile == null || !this.outputFile.exists() || outputFile.length() == 0) {
            return;
        }
        try {
            Email email = Email.getEmail(
                I18nUtil.getEmailFilename(
                    context.getCurrentLocale(),
                    configurationService.getProperty("droid.csv.checksum.mail.template"))
            );
            if (this.outputFile.exists()) {
                email.addAttachment(this.outputFile,  getPrefix() + ".csv");
            }
            String[] recipients = configurationService.getArrayProperty("droid.csv.checksum.mail.recipients");
            for (String recipient : recipients) {
                email.addRecipient(recipient);
            }
            email.send();
        } catch (IOException | MessagingException e) {
            log.error("Cannot create the email using the configured template!", e);
            throw new RuntimeException(e);
        }
    }

    protected Collection<Collection<String>> rows(MostRecentChecksum info) {
        if (info == null) {
            return List.of();
        }
        Collection<String> header = this.getHeader();
        return info.getDroidCheckResults()
            .stream()
            .map(droid -> new EvaluationContext(info, droid))
            .map(ec -> header.stream()
                             .map(entry -> rowMappers.get(entry).apply(ec))
                             .collect(Collectors.toList())
            )
            .collect(Collectors.toList());
    }

    protected Collection<String> getHeader() {
        return rowMappers.keySet();
    }
}
