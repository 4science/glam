/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.dspace.checker.DroidCheckResult;
import org.dspace.checker.DroidCheckStatus;
import org.dspace.checker.DroidResultCode;
import org.dspace.checker.service.AbstractDroidCheckResultService.AbstractDroidValidationMapper;
import org.dspace.checker.service.AbstractDroidValidationService.DroidValidationState;
import org.dspace.content.BitstreamFormat;
import org.dspace.core.Context;
import org.dspace.storage.bitstore.service.BitstreamStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.nationalarchives.droid.internal.api.ApiResult;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DroidValidationMapper extends AbstractDroidValidationMapper {

    private static final Logger log = LoggerFactory.getLogger(DroidValidationMapper.class);

    protected final Pattern versionPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    protected final BitstreamStorageService storageService;
    protected final DroidCheckStatusService droidCheckStatusService;

    public DroidValidationMapper(
        @Autowired DroidCheckStatusService droidCheckStatusService,
        @Autowired BitstreamStorageService storageService
    ) {
        this.droidCheckStatusService = droidCheckStatusService;
        this.storageService = storageService;
    }

    @Override
    protected List<DroidCheckResult> map(final Context context, final DroidValidationState state) {
        if (state == null || state.validationResult == null) {
            return null;
        }
        Iterator<ApiResult> iterator = state.validationResult.iterator();
        List<DroidCheckResult> results = new ArrayList<>(state.validationResult.size());
        ApiResult apiResult;
        while (iterator.hasNext() && (apiResult = iterator.next()) != null) {
            DroidCheckResult droidCheckResult = new DroidCheckResult();
            droidCheckResult.setStatus(getDroidCheckStatusBy(context, DroidResultCode.VALIDATED));
            droidCheckResult.setFileExtension(apiResult.getExtension());
            droidCheckResult.setFileFormat(apiResult.getName());
            droidCheckResult.setFormatVersion(
                versionPattern.matcher(apiResult.getName())
                              .results()
                              .findFirst()
                              .map(MatchResult::group)
                              .orElse(null)
            );
            try {
                droidCheckResult.setMimeType(
                    Optional.ofNullable(state.bitstream.getFormat(context))
                        .map(BitstreamFormat::getMIMEType)
                        .orElse(null)
                );
            } catch (SQLException e) {
                log.error("Cannot retrieve mimeType from the bitstream file.", e);
                droidCheckResult.setStatus(
                    getDroidCheckStatusBy(context, DroidResultCode.PARTIAL_VALIDATION)
                );
            }
            droidCheckResult.setPUID(apiResult.getPuid());
            droidCheckResult.setType(apiResult.getMethod().getMethod());
            droidCheckResult.setExtensionMismatch(apiResult.isFileExtensionMismatch());
            try {
                Path filePath = Paths.get(storageService.absolutePath(context, state.bitstream));
                droidCheckResult.setPath(filePath.toString());
                droidCheckResult.setFilename(
                    Optional.ofNullable(state.bitstream.getSource())
                            .orElseGet(() -> filePath.getFileName().toString())
                );
                droidCheckResult.setURI(filePath.toUri().toString());
            } catch (IOException | SQLException e) {
                log.error("Cannot retrieve the path of the bitstream file.", e);
                droidCheckResult.setStatus(
                    getDroidCheckStatusBy(context, DroidResultCode.PARTIAL_VALIDATION)
                );
            }
            try {
                droidCheckResult.setLastModifiedDate(
                    LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(storageService.getLastModified(state.bitstream)),
                        ZoneOffset.UTC
                    )
                );
                droidCheckResult.setFileSize(
                    Optional.ofNullable(state.bitstream.getSizeBytes())
                            .filter(val -> val != null)
                            .orElseGet(() -> state.file.getTotalSpace())
                );
            } catch (IOException e) {
                log.error("Cannot retrieve some information about the bitstream file.", e);
                droidCheckResult.setStatus(
                    getDroidCheckStatusBy(context, DroidResultCode.PARTIAL_VALIDATION)
                );
            }
            results.add(droidCheckResult);
        }
        return results;
    }

    private DroidCheckStatus getDroidCheckStatusBy(Context context, DroidResultCode code) {
        try {
            return droidCheckStatusService.findBy(context, code);
        } catch (SQLException e) {
            log.error("Cannot find the status droid status code!", e);
            throw new RuntimeException(e);
        }
    }
}
