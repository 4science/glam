/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script;

import org.apache.commons.cli.Options;

/**
 * Script configuration for {@link SubmissionFormGeneratorCLI}.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class SubmissionFormGeneratorScriptConfigurationCLI<T extends SubmissionFormGeneratorCLI>
        extends SubmissionFormGeneratorScriptConfiguration<SubmissionFormGenerator> {

    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();

            super.options = options;
        }
        return options;
    }

}
