/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.checker;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

import org.apache.commons.io.FilenameUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.scripts.handler.DSpaceRunnableHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChecksumCheckerCliScript extends ChecksumCheckerScript<ChecksumCheckerCliScriptConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(ChecksumCheckerCliScript.class);

    protected String getTempDir() {
        return Optional.ofNullable(
                           configurationService.getProperty("checksum-checker.collect.files.output.dir")
                       ).or(() -> Optional.ofNullable(configurationService.getProperty("upload.temp.dir")))
                       .orElseGet(() -> System.getProperty("java.io.tmpdir"));
    }


    protected void writeOutputFile(DSpaceRunnableHandler handler, Context context, File file)
        throws SQLException {
        if ( file == null) {
            log.warn("The output file was not provided");
            handler.logWarning("The output file was not provided");
            return;
        }

        if (!file.exists() || !file.isFile()) {
            log.info("The output file doesn't exist: {}", file.getAbsolutePath());
            handler.logInfo("The output file doesn't exist:" + file.getAbsolutePath());
            return;
        }
        String tempDir = getTempDir();

        File tempDirFile = new File(tempDir);
        if (!tempDirFile.exists()) {
            if (!tempDirFile.mkdirs()) {
                log.error("Unable to create the tempDir folder: {}", tempDir);
                handler.logError("Unable to create the tempDir folder: " + tempDir);
            }
        }

        Path tempFilePath = Path.of(tempDir, file.getName());

        try (FileInputStream fis = new FileInputStream(file)) {
            context.turnOffAuthorisationSystem();
            handler.writeFilestream(
                context,
                tempFilePath.toAbsolutePath().toString(),
                fis,
                FilenameUtils.getExtension(file.getName())
            );
        } catch (IOException | AuthorizeException e) {
            log.error("Cannot retrieve the output of the process!", e);
            handler.logError("Cannot retrieve the output of the process!", e);
        } finally {
            context.restoreAuthSystemState();
        }
    }

}
