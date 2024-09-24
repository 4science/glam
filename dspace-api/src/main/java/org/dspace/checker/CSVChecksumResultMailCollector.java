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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.mail.MessagingException;

import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a  {@link ChecksumResultsCollector} that can collect CSV file and send it as an attachment of a given
 * email. <br/>
 * This collector will collect information of a given {@link ChecksumResult} into a CSV file just by using the
 * {@link CSVChecksumResultMailCollector#collectorWriter}. <br/>
 * Once collector completes, it just sends the email embedding the CSV just collected as an attachment.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class CSVChecksumResultMailCollector implements ChecksumResultsCollector {
    private static final Logger log = LoggerFactory.getLogger(CSVChecksumResultMailCollector.class);
    private final Function<MostRecentChecksum, Bitstream> getBitstream = MostRecentChecksum::getBitstream;
    private final Function<MostRecentChecksum, String> getBitstreamId =
        (info) -> String.valueOf(info.getBitstream().getID());
    private final Function<Bitstream, String> getBitstreamFile =
        (bitstream) -> Optional.ofNullable(bitstream.getName()).or(() -> Optional.ofNullable(bitstream.getSource()))
                               .orElse("");
    private final Function<MostRecentChecksum, String> getOriginalChecksum = MostRecentChecksum::getExpectedChecksum;
    private final Function<MostRecentChecksum, String> getValidationChecksum = MostRecentChecksum::getCurrentChecksum;
    private final Function<MostRecentChecksum, Optional<Item>> getItem = (info) -> {
        try {
            return info.getBitstream().getBundles().stream().findFirst().map(Bundle::getItems).map(i -> i.get(0));
        } catch (SQLException e) {
            log.error("Cannot retrieve item for the current checksum-info!", e);
            throw new RuntimeException("Cannot retrieve item for the current checksum-info!", e);
        }
    };
    private final Function<Optional<Item>, String> getItemId =
        (item) -> item.map(Item::getID).map(String::valueOf).orElse("");
    private final Function<Optional<Item>, String> getItemTitle = (item) -> item.map(Item::getName).orElse("");
    private final Function<Optional<Item>, String> getItemCollection =
        (item) -> item.map(Item::getOwningCollection).or(() -> item.map(Item::getCollections).map(c -> c.get(0)))
                      .map(Collection::getID).map(String::valueOf).orElse("");

    private final List<ChecksumResultCode> invalidResultCodes =
        List.of(ChecksumResultCode.BITSTREAM_NOT_FOUND, ChecksumResultCode.BITSTREAM_INFO_NOT_FOUND,
                ChecksumResultCode.BITSTREAM_MARKED_DELETED, ChecksumResultCode.CHECKSUM_NO_MATCH);

    private final Map<String, ? extends Function<MostRecentChecksum, String>> rowMappers =
        Stream.of(Map.entry("bitstream", getBitstreamId),
                  Map.entry("filename", getBitstreamFile.compose(getBitstream)),
                  Map.entry("item", getItemId.compose(getItem)),
                  Map.entry("item title", getItemTitle.compose(getItem)),
                  Map.entry("collection", getItemCollection.compose(getItem)),
                  Map.entry("original checksum", getOriginalChecksum),
                  Map.entry("validation checksum", getValidationChecksum))
              .collect(Collectors.toMap(
                  Map.Entry::getKey, Map.Entry::getValue,
                  (e1, e2) -> {
                      throw new RuntimeException();
                  }, LinkedHashMap::new)
              );

    private Map<ChecksumResultCode, Integer> results = new HashMap<>();

    private String filePath;

    private final CSVTempFileWriter csvWriter = new CSVTempFileWriter(getTempDir(), getPrefix(), getDateSuffix());
    private final CSVCollectorWriter<MostRecentChecksum> collectorWriter =
        new CSVCollectorWriter<MostRecentChecksum>().with(
            new CSVCollector<MostRecentChecksum>().with(getFieldsSeparator()).with(rowMappers)).with(csvWriter);

    public CSVChecksumResultMailCollector() {
    }

    public CSVChecksumResultMailCollector(String filePath) {
        this.filePath = filePath;
    }

    public static String getDateSuffix() {
        return LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm").withZone(ZoneOffset.UTC));
    }

    public static String getFieldsSeparator() {
        return DSpaceServicesFactory.getInstance().getConfigurationService()
                                    .getProperty("checksum-checker.csv.checksum.separator");
    }

    public static String getConfigTempDir() {
        return DSpaceServicesFactory.getInstance().getConfigurationService()
                                    .getProperty("checksum-checker.csv.checksum.outputfile.tempdir");
    }

    public static String[] getRecipients() {
        return DSpaceServicesFactory.getInstance().getConfigurationService()
                                    .getArrayProperty("checksum-checker.csv.checksum.mail.recipients");
    }

    public static String getEmailTemplate() {
        return DSpaceServicesFactory.getInstance().getConfigurationService()
                                    .getProperty("checksum-checker.csv.checksum.mail.template");
    }

    public static String getPrefix() {
        return DSpaceServicesFactory.getInstance().getConfigurationService()
                                    .getProperty("checksum-checker.csv.checksum.outputfile.prefix");
    }

    private String getTempDir() {
        return Optional.ofNullable(this.filePath).orElseGet(CSVChecksumResultMailCollector::getConfigTempDir);
    }

    @Override
    public void collect(Context context, MostRecentChecksum info) throws SQLException {
        addToResults(info.getChecksumResult());
        writeInvalidCheck(info);
    }

    protected void writeInvalidCheck(MostRecentChecksum info) {
        if (info.getChecksumResult() != null && invalidResultCodes.contains(info.getChecksumResult().getResultCode())) {
            collectorWriter.writeRow(info);
        }
    }

    private void addToResults(ChecksumResult info) {
        this.results.put(info.getResultCode(), this.results.getOrDefault(info.getResultCode(), 0) + 1);
    }

    @Override
    public void complete(Context context) throws SQLException {
        {
            try {
                collectorWriter.close();
            } catch (Exception e) {
                throw new RuntimeException("Cannot close the csv writer", e);
            }
            try {
                Email email = Email.getEmail(I18nUtil.getEmailFilename(context.getCurrentLocale(), getEmailTemplate()));
                // "total",
                email.addArgument(results.values().stream().reduce(0, Integer::sum));
                // "valid"
                email.addArgument(results.getOrDefault(ChecksumResultCode.CHECKSUM_MATCH, 0));

                File attachment = csvWriter.outputFile;
                if (attachment != null && attachment.exists() && attachment.length() > 0) {
                    email.addAttachment(attachment, getPrefix() + ".csv");
                }

                String[] recipients = getRecipients();
                for (String recipient : recipients) {
                    email.addRecipient(recipient);
                }
                log.info("Sending the email with the checksum-checker output as attachment.");
                email.send();
            } catch (IOException | MessagingException e) {
                log.error("Cannot create the email using the configured template!", e);
                throw new RuntimeException("Cannot create the email using the configured template!", e);
            }
        }
    }

    @Override
    public List<File> output(Context context) throws Exception {
        if (csvWriter.outputFile == null || !csvWriter.outputFile.exists() || csvWriter.outputFile.length() == 0) {
            return ChecksumResultsCollector.super.output(context);
        }
        return List.of(csvWriter.outputFile);
    }
}
