/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.service.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.checker.DroidValidationException;
import org.dspace.checker.service.AbstractDroidValidationService;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.core.Context;
import org.dspace.storage.bitstore.service.BitstreamStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.nationalarchives.droid.core.SignatureParseException;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DroidValidationServiceImpl extends AbstractDroidValidationService {

    public static final String FILE_SUFFIX = ".droid";
    private static final Logger log = LoggerFactory.getLogger(DroidValidationServiceImpl.class);

    @Autowired
    protected BitstreamStorageService storageService;


    @Override
    public DroidValidationState validate(final Context context, final Bitstream bitstream)
        throws DroidValidationException {
        try {
            return validate(context, getInstance(), getFile(context, bitstream), bitstream);
        } catch (SignatureParseException e) {
            log.error("Cannot initialize DROID validation feature!", e);
            throw new DroidValidationException("Cannot initialize DROID validation feature!", e);
        }
    }

    private File getFile(Context context, Bitstream bitstream) {
        InputStream inputStream;
        try {
            inputStream = this.storageService.retrieve(context, bitstream);
        } catch (IOException | SQLException e) {
            log.error("Cannot load the file to validate with DROID!", e);
            throw new RuntimeException(e);
        }
        File tempFile;
        try {
            tempFile = File.createTempFile(randomFileName(), getSuffix(context, bitstream));
        } catch (IOException e) {
            log.error("Cannot create the temporary file for DROID validation!", e);
            throw new RuntimeException(e);
        }

        tempFile.deleteOnExit();

        try {
            FileUtils.copyInputStreamToFile(inputStream, tempFile);
        } catch (IOException e) {
            log.error("Cannot copy the bitstream content into the temporary file!", e);
            throw new RuntimeException(e);
        }
        return tempFile;
    }

    private String randomFileName() {
        return StringUtils.join(
            List.of(tempFile(), UUID.randomUUID().toString()),
            "_"
        );
    }

    private String getSuffix(final Context context, final Bitstream bitstream) {
        String suffix = FILE_SUFFIX;
        try {
            BitstreamFormat format = bitstream.getFormat(context);
            if (format != null && format.getExtensions() != null && !format.getExtensions().isEmpty()) {
                suffix = "." + format.getExtensions().get(0);
            }
        } catch (SQLException e) {
            log.error(
                "Cannot determine the suffix of the bitstream {} to process, using the default DROID extension.\n{}",
                bitstream.getID(),
                e
            );
        }
        return suffix;
    }

}
