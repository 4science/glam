/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.general;

import static org.dspace.curate.CurationOrchestratorScript.STATUS_FILE_PATTER_NAME;
import static org.dspace.curate.Curator.CURATE_FAIL;
import static org.dspace.curate.Curator.CURATE_SUCCESS;

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
import org.dspace.curate.ScheduledProcess;
import org.dspace.curate.ServerlessCurationTask;
import org.dspace.curate.dto.StatusJsonDTO;
import org.dspace.storage.bitstore.BitStoreService;
import org.dspace.storage.bitstore.BitstreamStorageServiceImpl;
import org.dspace.storage.bitstore.S3BitStoreService;

/**
 * PdfA curation task.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class PdfACurationTask extends AbstractCurationTask implements ServerlessCurationTask {

    private static final Logger log = LogManager.getLogger(PdfACurationTask.class);

    private static final String PDFA_BUNDLE_NAME = "PDFA";
    private static final String JSON_SUCCESS_STATUS = "success";

    @Override
    public int perform(Context ctx, Item item, AmazonS3 amazonS3, ScheduledProcess scheduledProcess)
            throws IOException {
        TransferManager transferManager = TransferManagerBuilder.standard().withS3Client(amazonS3).build();
        try {
            for (ScheduledCurationTask scheduledCurationTask : scheduledProcess.files()) {
                if (!StringUtils.equals(taskId, scheduledCurationTask.jobType())) {
                    continue;
                }

                String json = downloadJSON(amazonS3, scheduledProcess, scheduledCurationTask);
                StatusJsonDTO statusJsonDTO = convertToJsonNode(json);
                if (statusJsonDTO == null) {
                    return CURATE_FAIL;
                }

                if (!StringUtils.equals(JSON_SUCCESS_STATUS, statusJsonDTO.getStatus())) {
                    log.error("PDF/A curation task failed for bitstream with uuid:{} due error: {} .",
                              scheduledCurationTask.uuid(), statusJsonDTO.getError());
                    return CURATE_FAIL;
                }

                try (InputStream pdfaInputStream = downloadPdfA(transferManager, statusJsonDTO.getOutputPath())) {
                    if (pdfaInputStream == null) {
                        log.error("ERROR downloading PDF/A file from S3 for Item:{} .", item.getID().toString());
                        return CURATE_FAIL;
                    }
                    log.info("Creating PDF/A bitstream for Item:{} .", item.getID().toString());
                    createBitstream(ctx, item, statusJsonDTO, pdfaInputStream);
                }
            }
        } catch (SQLException | AuthorizeException e) {
            var message = "ERROR while creating bitstream PDF/A for Item:{} due to: {} .";
            log.error(message, item.getID().toString(), e.getMessage());
            return CURATE_FAIL;
        } finally {
            transferManager.shutdownNow(false);
        }
        return CURATE_SUCCESS;
    }

    @Override
    public List<Bitstream> getProcessableBitstreams(Context context, Item item) throws SQLException {
        List<Bitstream> processableBitstreams = new ArrayList<>();
        Iterator<Bitstream> bitstreams = this.bitstreamService.getItemBitstreams(context, item);
        while (bitstreams.hasNext()) {
            Bitstream currentBitstream = bitstreams.next();
            BitStoreService bitStoreService = ((BitstreamStorageServiceImpl) this.bitstreamStorageService).getStores()
                                                                                .get(currentBitstream.getStoreNumber());
            if (!(bitStoreService instanceof S3BitStoreService)) {
                log.info("Skipping bitstream {} because is not stored on S3!", currentBitstream.getID());
                continue;
            }
            if (skipBitstream(currentBitstream)) {
                log.info("Skipping bitstream {} was required during submission!", currentBitstream.getID());
                continue;
            }
            if (!isPDF(context, currentBitstream)) {
                var message = "Skipping bitstream: {}  of item {}, because is not a PDF!";
                log.info(message, currentBitstream.getID(), item.getID());
                continue;
            }
            processableBitstreams.add(currentBitstream);
        }
        return processableBitstreams;
    }

    private boolean isPDF(Context context, Bitstream currentBitstream) {
        BitstreamFormat bitstreamFormat = null;
        try {
            bitstreamFormat = bitstreamFormatService.guessFormat(context, currentBitstream);
        } catch (SQLException e) {
            var message = "Error while getting bitstream format for bitstream id: {} , due to: {} ";
            log.error(message, currentBitstream.getID(), e);
        }
        var info = "Bitstream format for bitstream id: {} is: {} ";
        log.info(info, currentBitstream.getID(), bitstreamFormat.getMIMEType());
        return bitstreamFormat != null && StringUtils.equalsIgnoreCase(bitstreamFormat.getMIMEType(),"application/pdf");
    }

    private String downloadJSON(AmazonS3 s3Client, ScheduledProcess scheduledProcess, ScheduledCurationTask sTask) {
        try {
            var jsonName = String.format(STATUS_FILE_PATTER_NAME, sTask.uuid(), sTask.jobType());
            var fileKey =  scheduledProcess.process() + "/" + jsonName;
            var message = "Downloading output JSON file with key: {} from bucket: {} .";
            log.info(message, fileKey, scheduledProcess.bucketNameOutput());
            S3Object s3Object = s3Client.getObject(scheduledProcess.bucketNameOutput(), fileKey);
            try (InputStream is = s3Object.getObjectContent()) {
                return IOUtils.toString(is, Charset.defaultCharset());
            }
        } catch (IOException e) {
            log.error("Error reading JSON file: " + e.getMessage());
            return null;
        }
    }

    private InputStream downloadPdfA(TransferManager transferManager, String filePath) {
        try {
            Path tempFile = Files.createTempFile("temp-pdf-file", ".pdf");
            log.info("Downloading PDF/A file from S3 path: {} .", filePath);
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
            log.error("Error downloading file from S3 path '{}': {}", filePath, e.getMessage());
            return null;
        }
    }

    private StatusJsonDTO convertToJsonNode(String json) {
        if (StringUtils.isBlank(json)) {
            log.error("Provided JSON was empty or null!");
            return null;
        }
        try {
            return new ObjectMapper().readValue(json, StatusJsonDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Unable to process json response, " + e.getMessage(), e);
        }
        return null;
    }

    private Bitstream createBitstream(Context context, Item item, StatusJsonDTO dto, InputStream is)
             throws SQLException, AuthorizeException, IOException {
        Bundle pdfaBundle;
        List<Bundle> bundles = itemService.getBundles(item, PDFA_BUNDLE_NAME);
        if (bundles.size() < 1) {
            log.info("Creating new PDFA bundle for item: " + item.getID());
            pdfaBundle = bundleService.create(context, item, PDFA_BUNDLE_NAME);
        } else {
            pdfaBundle = bundles.iterator().next();
        }

        log.info("Creating PDF/A bitstream for item: " + item.getID());
        Bitstream pdfaBitstream = bitstreamService.create(context, pdfaBundle, is);
        Bitstream originalBitstream = getOriginalBitstream(item, dto.getOutputPath());
        bitstreamService.addMetadata(context, pdfaBitstream, "bitstream", "curation", "originalBitstream", null,
                                     originalBitstream.getID().toString());

        String fileName = getPDFaName(originalBitstream, dto.getOutputPath());
        log.info("Setting PDF/A bitstream name to: " + fileName + " for item: " + item.getID());

        pdfaBitstream.setName(context, fileName);
        BitstreamFormat bitstreamFormat = bitstreamFormatService.guessFormat(context, pdfaBitstream);
        bitstreamService.setFormat(context, pdfaBitstream, bitstreamFormat);
        bitstreamService.update(context, pdfaBitstream);
        return pdfaBitstream;
    }

    private String getPDFaName(Bitstream originalBitstream, String outputPath) {
        String generatedName = getGeneratedName(outputPath);
        if (originalBitstream == null) {
            log.error("Cannot find original bitstream for PDF/A! Used generated name: {} ", generatedName);
            return generatedName;
        }
        log.info("Using original bitstream name: {} for PDF/A bitstream! ", originalBitstream.getName());
        return originalBitstream.getName();
    }

    private String getGeneratedName(String outputPath) {
        String generatedName = outputPath.substring(outputPath.lastIndexOf('/') + 1);
        return generatedName;
    }

    private  Bitstream getOriginalBitstream(Item item, String outputPath) {
        String generatedName = getGeneratedName(outputPath);
        String [] splitedGeneratedName = generatedName.split("_");
        if (splitedGeneratedName.length < 2) {
            log.error("Generated name for PDF/A is not in expected format! Used generated name: {} ", generatedName);
            return null;
        }
        return item.getBundles("ORIGINAL")
                   .get(0)
                   .getBitstreams()
                   .stream()
                   .filter( b -> StringUtils.equals(b.getInternalId(), splitedGeneratedName[0]))
                   .findFirst()
                   .orElse(null);
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
        return 0;
    }

    @Override
    public int perform(Context ctx, String id) throws IOException {
        return 0;
    }

}
