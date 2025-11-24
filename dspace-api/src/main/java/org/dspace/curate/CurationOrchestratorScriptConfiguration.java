/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import org.apache.commons.cli.Options;
import org.dspace.scripts.configuration.ScriptConfiguration;

/**
 * Configuration class for Curation Orchestrator Script
 * @param <T> the DSpaceRunnable type
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class CurationOrchestratorScriptConfiguration<T extends CurationOrchestratorScript>
    extends ScriptConfiguration<T> {

    private Class<T> dspaceRunnableClass;

    @Override
    public Class<T> getDspaceRunnableClass() {
        return this.dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }

    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();
            options.addOption("id","identifier", true, "item identifier (handle or uuid)");
            options.addOption("t", "task", true, "curation task to execute, allowed multiple values");

            var maessage = "force execution of the curation task even if it was already executed";
            options.addOption("f", "force", false, maessage);
            super.options = options;
        }
        return options;
    }

}
