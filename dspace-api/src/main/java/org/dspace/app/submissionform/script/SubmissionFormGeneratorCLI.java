/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

import com.apicatalog.jsonld.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI variant for the {@link SubmissionFormGenerator} class.
 * This was done to specify the specific behaviors for the CLI.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class SubmissionFormGeneratorCLI extends SubmissionFormGenerator {

    private static final Logger log = LoggerFactory.getLogger(SubmissionFormGeneratorCLI.class);

    protected void initContext() {
        this.context = new Context(Context.Mode.READ_ONLY);
    }

    protected void attachZipToProcess(Path zipFile) {
        if (StringUtils.isNotBlank(this.outputPath)) {
            Path outputZipPath = Paths.get(this.outputPath, OUTPUT_ZIP_FILE_NAME);

            try {
                Files.createDirectories(outputZipPath.getParent());
            } catch (IOException e) {
                log.error("Cannot create output directory:{}", outputZipPath.getParent(), e);
                handler.logError("Cannot create output directory: " + outputZipPath.getParent(), e);
                throw new RuntimeException(e);
            }

            log.info("Attaching zip file to the process");
            try {
                handler.writeFilestream(context, outputZipPath.toAbsolutePath().toString(),
                                        Files.newInputStream(zipFile), ZIP_TYPE);
            } catch (IOException | SQLException | AuthorizeException e) {
                log.error("Error attaching zip file to the process", e);
                throw new RuntimeException(e);
            }
        }
    }

}
