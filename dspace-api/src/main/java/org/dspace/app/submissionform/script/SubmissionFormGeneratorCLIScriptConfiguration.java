/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script;

import java.io.InputStream;
import java.util.List;

import org.apache.commons.cli.Options;
import org.dspace.core.Context;
import org.dspace.scripts.DSpaceCommandLineParameter;

/**
 * Script configuration for {@link SubmissionFormGeneratorCLI}.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class SubmissionFormGeneratorCLIScriptConfiguration<T extends SubmissionFormGeneratorCLI>
        extends SubmissionFormGeneratorScriptConfiguration<T> {

    @Override
    public boolean isAllowedToExecute(Context context, List<DSpaceCommandLineParameter> commandLineParameters) {
        return true;
    }

    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();
            super.options = options;
            options.addOption("e", "excel", true, "Input file excel");
            options.getOption("e").setType(InputStream.class);
            options.getOption("e").setRequired(true);

            options.addOption("p", "output-path", true,
                              "Directory path where the generated submission-forms.zip file will be copied");
            options.getOption("p").setType(String.class);
            options.getOption("p").setRequired(false);

            options.addOption("f", "force", false,
                    "Proceed with XML generation even if validation produces warnings (attempts to auto-fix warnings)");
            options.getOption("f").setRequired(false);

            options.addOption("d", "submission-name", true, "Submission definition name to use " +
                "(if not specified, the first available from the Excel file will be used)");
            options.getOption("d").setType(String.class);
            options.getOption("d").setRequired(false);
        }
        return options;
    }

}
