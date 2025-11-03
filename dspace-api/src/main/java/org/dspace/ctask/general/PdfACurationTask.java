/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.general;

import static org.dspace.curate.CurationOrchestratorScript.STATUS_FILE_PATTERN_NAME;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.ScheduledCurationTask;
import org.dspace.curate.ServerlessCurationTask;
import org.dspace.curate.dto.StatusJsonDTO;
import org.dspace.curate.service.CurationTaskResult;
import org.dspace.storage.bitstore.BitStoreService;
import org.dspace.storage.bitstore.BitstreamStorageServiceImpl;
import org.dspace.storage.bitstore.S3BitStoreService;

/**
 * PDF/A Curation Task that processes PDF bitstreams and creates PDF/A compliant versions.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class PdfACurationTask extends AbstractCurationTask implements ServerlessCurationTask {

    private static final Logger log = LogManager.getLogger(PdfACurationTask.class);
    private static final String SUPPORTED_MIME_TYPE = "application/pdf";

    private static final String PDFA_BUNDLE_NAME = "PDFA";
    private static final String JSON_SUCCESS_STATUS = "success";

    @Override
    public CurationTaskResult initPerform(Context context, AmazonS3 amazonS3, ScheduledCurationTask scheduledTask,
                                          String processId) {

        String json = downloadJSON(amazonS3, processId, scheduledTask);
        StatusJsonDTO statusJsonDTO = convertToJsonNode(json);
        if (statusJsonDTO == null) {
            var errorMessage = "Unable to parse output status JSON for bitstream:" + scheduledTask.uuid();
            return CurationTaskResult.failure(scheduledTask.jobType(), List.of(), errorMessage);
        }

        if (!StringUtils.equals(JSON_SUCCESS_STATUS, statusJsonDTO.getStatus())) {
            var message = "PdfACurationTask: PDF/A CurationTask failed for bitstream:{} with error:{} ";
            log.error(message, scheduledTask.uuid(), statusJsonDTO.getError());
            return CurationTaskResult.failure(scheduledTask.jobType(), List.of(), statusJsonDTO.getError());
        }

        TransferManager transferManager = TransferManagerBuilder.standard().withS3Client(amazonS3).build();
        try {
            try (InputStream pdfaInputStream = downloadPdfA(transferManager, statusJsonDTO.getOutputPath())) {
                if (pdfaInputStream == null) {
                    var errorMessage = "PDF/A file could not be downloaded from S3 for bitstream:";
                    log.error(errorMessage + scheduledTask.uuid());
                    return CurationTaskResult.failure(scheduledTask.jobType(), List.of(),
                                           errorMessage + scheduledTask.uuid());
                }
                log.info("PdfACurationTask: Creating PDF/A bitstream");
                Bitstream pdfaBitstream = createBitstream(context, pdfaInputStream, scheduledTask, statusJsonDTO);
                return CurationTaskResult.success(scheduledTask.jobType(), List.of(pdfaBitstream));
            } catch (IOException e) {
                log.error("PdfACurationTask: ERROR while creating bitstream PDF/A due to:{} ", e.getMessage());
                return CurationTaskResult.failure(scheduledTask.jobType(), List.of(), e.getMessage());
            }
        } catch (SQLException e) {
            var message = "PdfACurationTask: ERROR while creating bitstream PDF/A for origin Bitstream:{} due to:{} ";
            log.error(message, scheduledTask.uuid(), e.getMessage());
            return CurationTaskResult.failure(scheduledTask.jobType(), List.of(),
                    "ERROR while creating bitstream PDF/A for origin Bitstream:" + scheduledTask.uuid());
        } finally {
            transferManager.shutdownNow(false);
        }
    }

    @Override
    public void finalizeTask(Context context, Item item, CurationTaskResult CurationTaskResult)
            throws SQLException, AuthorizeException {
        Bundle pdfaBundle;
        List<Bundle> pdfaBundles = itemService.getBundles(item, PDFA_BUNDLE_NAME);
        if (pdfaBundles.size() < 1) {
            log.info("PdfACurationTask: Creating new PDFA bundle for item: " + item.getID());
            pdfaBundle = bundleService.create(context, item, PDFA_BUNDLE_NAME);
        } else {
            pdfaBundle = pdfaBundles.get(0);
        }
        for (Bitstream bitstream : CurationTaskResult.bitsreams()) {
            addBitstreamToBundle(context, bitstream, pdfaBundle);
        }
    }

    /**
     * Retrieves all bitstreams from an item that can be processed for PDF/A conversion.
     * Only includes PDF bitstreams that are stored in S3.
     *
     * @param context the DSpace context
     * @param item the item to analyze
     * @return list of bitstreams eligible for PDF/A conversion
     * @throws SQLException if database operations fail
     */
    @Override
    public List<Bitstream> getProcessableBitstreams(Context context, Item item) throws SQLException {
        List<Bitstream> processableBitstreams = new ArrayList<>();
        Iterator<Bitstream> bitstreams = this.bitstreamService.getItemBitstreams(context, item);
        while (bitstreams.hasNext()) {
            Bitstream currentBitstream = bitstreams.next();
            BitStoreService bitStoreService = ((BitstreamStorageServiceImpl) this.bitstreamStorageService).getStores()
                                                                                .get(currentBitstream.getStoreNumber());
            if (!(bitStoreService instanceof S3BitStoreService)) {
                var message = "PdfACurationTask: Skipping bitstream {} because is not stored on S3!";
                log.info(message, currentBitstream.getID());
                continue;
            }
            if (skipBitstream(currentBitstream)) {
                var message = "PdfACurationTask: Skipping bitstream {} was required during submission!";
                log.info(message, currentBitstream.getID());
                continue;
            }
            if (!isPDF(context, currentBitstream)) {
                var message = "PdfACurationTask: Skipping bitstream: {}  of item {}, because is not a PDF!";
                log.info(message, currentBitstream.getID(), item.getID());
                continue;
            }
            processableBitstreams.add(currentBitstream);
        }
        return processableBitstreams;
    }

    private boolean isPDF(Context context, Bitstream currentBitstream) {
        try {
            BitstreamFormat bitstreamFormat = bitstreamFormatService.guessFormat(context, currentBitstream);
            if (bitstreamFormat == null) {
                log.info("PdfACurationTask: Cannot determine format for bitstream:{} ", currentBitstream.getID());
                return false;
            }
            String mimeType = bitstreamFormat.getMIMEType();
            var message = "PdfACurationTask: Bitstream format for bitstream id: {} is: {} ";
            log.info(message, currentBitstream.getID(), mimeType);

            return SUPPORTED_MIME_TYPE.equalsIgnoreCase(mimeType);
        } catch (SQLException e) {
            var errorMessage = "PdfACurationTask: Error getting bitstream format for bitstream id:{} ";
            log.error(errorMessage, currentBitstream.getID(), e);
            return false;
        }
    }

    private String downloadJSON(AmazonS3 s3Client, String processId, ScheduledCurationTask sTask) {
        try {
            String outputBucketName = getBucketName();
            String jsonName = String.format(STATUS_FILE_PATTERN_NAME, sTask.uuid(), sTask.jobType());
            String fileKey =  processId + "/" + jsonName;

            var message = "PdfACurationTask: Downloading output JSON file with key: {} from bucket: {} .";
            log.info(message, fileKey, outputBucketName);
            S3Object s3Object = s3Client.getObject(outputBucketName, fileKey);
            try (InputStream is = s3Object.getObjectContent()) {
                return IOUtils.toString(is, Charset.defaultCharset());
            }
        } catch (IOException e) {
            log.error("PdfACurationTask: Error reading JSON file: " + e.getMessage());
            return null;
        }
    }

    private InputStream downloadPdfA(TransferManager transferManager, String filePath) {
        try {
            Path tempFile = Files.createTempFile("temp-pdf-file", ".pdf");
            log.info("PdfACurationTask: Downloading PDF/A file from S3 path: {} .", filePath);
            Download download = transferManager.download(getBucketName(), filePath, tempFile.toFile());
            download.waitForCompletion();

            // Returns an InputStream that self-deletes when closed.
            return new FileInputStream(tempFile.toFile()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        Files.deleteIfExists(tempFile);
                    }
                }
            };
        } catch (InterruptedException | IOException e) {
            log.error("PdfACurationTask: Error downloading file from S3 path '{}': {}", filePath, e.getMessage());
            return null;
        }
    }

    private StatusJsonDTO convertToJsonNode(String json) {
        if (StringUtils.isBlank(json)) {
            log.error("PdfACurationTask: Provided JSON was empty or null!");
            return null;
        }
        try {
            return new ObjectMapper().readValue(json, StatusJsonDTO.class);
        } catch (JsonProcessingException e) {
            log.error("PdfACurationTask: Unable to process json response, " + e.getMessage(), e);
            return null;
        }
    }

    private Bitstream createBitstream(Context context, InputStream is, ScheduledCurationTask scheduledTask,
                                      StatusJsonDTO dto) throws SQLException, IOException {
        log.info("PdfACurationTask: Creating PDF/A bitstream without bundle association.");
        Bitstream pdfaBitstream = bitstreamService.create(context, is);

        log.info("PdfACurationTask: PDF/A bitstream created with id:{} .", pdfaBitstream.getID());
        Bitstream originalBitstream = getOriginalBitstream(context, scheduledTask.uuid());
        addReferenceToOriginalBitstream(context, pdfaBitstream, originalBitstream);
        String fileName = getPDFaName(originalBitstream, dto.getOutputPath());

        log.info("PdfACurationTask: Setting PDF/A bitstream name to:{} ", fileName);
        pdfaBitstream.setName(context, fileName);
        BitstreamFormat bitstreamFormat = bitstreamFormatService.guessFormat(context, pdfaBitstream);
        bitstreamService.setFormat(context, pdfaBitstream, bitstreamFormat);
        return pdfaBitstream;
    }

    private void addBitstreamToBundle(Context context, Bitstream pdfaBitstream, Bundle pdfaBundle)
            throws SQLException, AuthorizeException {
        var message = "PdfACurationTask: Adding PDF/A bitstream:{} to PDFA bundle:{} .";
        log.info(message, pdfaBitstream.getID(), pdfaBundle.getID());
        bundleService.addBitstream(context, pdfaBundle, pdfaBitstream);
    }

    private void addReferenceToOriginalBitstream(Context context, Bitstream pdfaBitstream, Bitstream originalBitstream)
            throws SQLException {
        var uuid = originalBitstream.getID().toString();
        bitstreamService.addMetadata(context, pdfaBitstream, "bitstream", "curation", "originalBitstream", null, uuid);
    }

    private String getPDFaName(Bitstream originalBitstream, String outputPath) {
        String generatedName = getGeneratedName(outputPath);
        if (originalBitstream == null) {
            var message = "PdfACurationTask: Cannot find original bitstream for PDF/A! Used generated name: {} ";
            log.error(message, generatedName);
            return generatedName;
        }
        var message = "PdfACurationTask: Using original bitstream name: {} for PDF/A bitstream! ";
        log.info(message, originalBitstream.getName());
        return originalBitstream.getName();
    }

    private String getGeneratedName(String outputPath) {
        String generatedName = outputPath.substring(outputPath.lastIndexOf('/') + 1);
        return generatedName;
    }

    private Bitstream getOriginalBitstream(Context context, UUID bitstreamUUID) throws SQLException {
        return bitstreamService.find(context, bitstreamUUID);
    }

    private String getBucketName() {
        return this.configurationService.getProperty("curation.s3.bucketName-output");
    }

    @Override
    public void init(Curator curator, String taskId) throws IOException {
        super.init(curator, taskId);
    }

    @Override
    public int perform(DSpaceObject dso) throws IOException {
        return 1;
    }

    @Override
    public int perform(Context ctx, String id) throws IOException {
        return 1;
    }

}
