/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
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
 * This class is responsible to collect Droid related entities and then organize them as a CSV file.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class CSVDroidChecksumCollector implements ChecksumResultsCollector {

    private static final Logger log = LoggerFactory.getLogger(CSVDroidChecksumCollector.class);

    private final ConfigurationService configurationService =
        DSpaceServicesFactory.getInstance().getConfigurationService();

    private String filePath;
    private final CSVTempFileWriter csvWriter = new CSVTempFileWriter(getTempDir(), getPrefix(), getDateSuffix());
    public static Map<String, EvaluationContextMapper> rowMappers =
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
                                  .orElse("")
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
                      (EvaluationContextMapper) (ec) ->
                          Optional.ofNullable(ec.droidCheckResult.getFormatVersion()).orElse(""))

        ).collect(Collectors.toMap(
            Map.Entry::getKey, Map.Entry::getValue,
            (e1, e2) -> {
                throw new RuntimeException();
            }, LinkedHashMap::new)
        );
    private final CSVCollectorWriter<EvaluationContext> collectorWriter =
        new CSVCollectorWriter<EvaluationContext>()
            .with(new CSVCollector<EvaluationContext>().with(getFieldsSeparator()).with(rowMappers))
            .with(csvWriter);

    public CSVDroidChecksumCollector() {
        this(null);
    }

    public CSVDroidChecksumCollector(String outputFile) {
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

    public static String getPrefix() {
        return DSpaceServicesFactory.getInstance().getConfigurationService()
                                    .getProperty("droid.csv.checksum.outputfile.prefix");
    }

    private static Stream<EvaluationContext> mapToEvaluationContext(MostRecentChecksum info) {
        return info.getDroidCheckResults()
                   .stream()
                   .map(droid -> new EvaluationContext(info, droid));
    }

    public static String getFieldsSeparator() {
        return DSpaceServicesFactory.getInstance().getConfigurationService()
                                    .getProperty("droid.csv.checksum.separator");
    }

    private String getTempDir() {
        return Optional.ofNullable(this.filePath)
                       .orElseGet(() -> configurationService.getProperty("droid.csv.checksum.outputfile.tempdir"));
    }

    private String[] getRecipients() {
        return configurationService.getArrayProperty("droid.csv.checksum.mail.recipients");
    }

    private String getEmailTemplate() {
        return configurationService.getProperty("droid.csv.checksum.mail.template");
    }


    @Override
    public void collect(Context context, MostRecentChecksum info) throws SQLException {
        try {
            this.collectorWriter.writeRows(
                mapToEvaluationContext(info)
                    .collect(Collectors.toList())
            );
        } catch (Exception e) {
            log.error("Cannot add elements to the configured output file!", e);
            throw new RuntimeException("Cannot add elements to the configured output file!", e);
        }
    }

    @Override
    public void complete(Context context) {
        try {
            collectorWriter.close();
        } catch (Exception e) {
            throw new RuntimeException("Cannot close the csv writer", e);
        }
        try {
            Email email = Email.getEmail(
                I18nUtil.getEmailFilename(
                    context.getCurrentLocale(),
                    getEmailTemplate()
                )
            );
            email.addAttachment(csvWriter.outputFile, getPrefix() + ".csv");
            String[] recipients = getRecipients();
            for (String recipient : recipients) {
                email.addRecipient(recipient);
            }
            log.info("Sending the email with the droid output as attachment.");
            email.send();
        } catch (IOException | MessagingException e) {
            log.error("Cannot create the email using the configured template!", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<File> output(Context context) throws Exception {
        if (csvWriter.outputFile == null || !csvWriter.outputFile.exists()) {
            return ChecksumResultsCollector.super.output(context);
        }
        return List.of(csvWriter.outputFile);
    }

    protected Collection<String> getHeader() {
        return rowMappers.keySet();
    }
}
