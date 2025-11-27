/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import org.dspace.checker.DroidValidationException;
import org.dspace.content.Bitstream;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.nationalarchives.droid.core.SignatureParseException;
import uk.gov.nationalarchives.droid.internal.api.ApiResult;
import uk.gov.nationalarchives.droid.internal.api.DroidAPI;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public abstract class AbstractDroidValidationService implements DroidValidationService {

    private static final Logger log = LoggerFactory.getLogger(AbstractDroidValidationService.class);

    @Autowired
    protected ConfigurationService configurationService;

    protected static final DroidValidationState validate(
        Context context, DroidAPI droidAPI, File file, Bitstream bitstream
    ) throws DroidValidationException {
        return new DroidValidationState(context, droidAPI, file, bitstream).validate();
    }

    protected String containerFile() {
        return configurationService.getProperty("droid.container.file");
    }

    protected String signatureFile() {
        return configurationService.getProperty("droid.signature.file");
    }

    protected String tempFile() {
        return configurationService.getProperty("droid.temp.file");
    }

    protected DroidAPI getInstance() throws SignatureParseException {
        return DroidAPI.getInstance(
            Paths.get(signatureFile()),
            Paths.get(containerFile())
        );
    }

    public static final class DroidValidationState {

        final Context context;
        final DroidAPI droidAPI;
        final File file;
        final Bitstream bitstream;
        List<ApiResult> validationResult;

        protected DroidValidationState(
            Context context,
            DroidAPI droidAPI,
            File file,
            Bitstream bitstream
        ) {
            this.context = context;
            this.droidAPI = droidAPI;
            this.file = file;
            this.bitstream = bitstream;
        }

        private DroidValidationState validate() throws DroidValidationException {
            try {
                this.validationResult = this.droidAPI.submit(file.toPath().toAbsolutePath());
            } catch (IOException e) {
                log.error("Cannot validate the file with DROID!", e);
                throw new DroidValidationException("Cannot validate the file with DROID!", e);
            }
            return this;
        }
    }

}
