/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script;

import java.io.InputStream;

import org.apache.commons.cli.Options;
import org.dspace.scripts.configuration.ScriptConfiguration;

/**
 * Configuration class for the {@link SubmissionFormGenerator} script.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class SubmissionFormGeneratorScriptConfiguration<T extends SubmissionFormGenerator>
        extends ScriptConfiguration<T> {

    private Class<T> dspaceRunnableClass;

    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();
            super.options = options;
            options.addOption("e", "excel", true, "Input file excel");
            options.getOption("e").setType(InputStream.class);
            options.getOption("e").setRequired(true);

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

    @Override
    public Class<T> getDspaceRunnableClass() {
        return this.dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }

}
