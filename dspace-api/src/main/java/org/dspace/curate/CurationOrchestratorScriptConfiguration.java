/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.Options;
import org.dspace.core.Context;
import org.dspace.scripts.DSpaceCommandLineParameter;
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
    private List<ServerlessCurationTask> serverlessCurationTasks;

    public boolean isAllowedToExecute(Context context, List<DSpaceCommandLineParameter> commandLineParameters) {
        try {
            return authorizeService.isAdmin(context) || authorizeService.isComColAdmin(context);
        } catch (SQLException e) {
            throw new RuntimeException("SQLException occurred when checking if the current user is an admin", e);
        }
    }

    @Override
    public Options getOptions() {
        if (options == null) {
            Options options = new Options();
            options.addOption("id","identifier", true, "item identifier (handle or uuid)");

            var taskDescription = "curation task to execute, allowed multiple values! Possible values are: ";
            options.addOption("t", "task", true, taskDescription + getTaskOtions());

            var forceDescription = "force execution of the curation task even if it was already executed";
            options.addOption("f", "force", false, forceDescription);
            super.options = options;
        }
        return options;
    }

    private List<String> getTaskOtions() {
        List<String> taskOptions  = new ArrayList<>();
        serverlessCurationTasks.forEach(task -> taskOptions.add(task.getTaskName()));
        return taskOptions;
    }

    @Override
    public Class<T> getDspaceRunnableClass() {
        return this.dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }

    public void setServerlessCurationTasks(List<ServerlessCurationTask> serverlessCurationTasks) {
        this.serverlessCurationTasks = serverlessCurationTasks;
    }

}
