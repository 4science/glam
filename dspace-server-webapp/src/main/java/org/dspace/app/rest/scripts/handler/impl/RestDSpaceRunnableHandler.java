/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.scripts.handler.impl;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.ProcessStatus;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.scripts.DSpaceCommandLineParameter;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.scripts.Process;
import org.dspace.scripts.ProcessDSpaceRunnableHandler;
import org.dspace.scripts.handler.DSpaceRunnableHandler;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;
import org.springframework.core.task.TaskExecutor;

/**
 * The {@link DSpaceRunnableHandler} dealing with Scripts started from the REST api
 */
public class RestDSpaceRunnableHandler extends ProcessDSpaceRunnableHandler {

    private static final Logger log = LogManager.getLogger(RestDSpaceRunnableHandler.class);

    protected final ConfigurationService configurationService =
        DSpaceServicesFactory.getInstance().getConfigurationService();

    /**
     * This constructor will initialise the handler with the process created from the parameters
     * @param ePerson       The eperson that creates the process
     * @param scriptName    The name of the script for which is a process will be created
     * @param parameters    The parameters for this process
     * @param specialGroups The list of special groups related to eperson creating process at process creation time
     * @param currentLocale The context current locale to use inside the runnable handler
     */
    public RestDSpaceRunnableHandler(EPerson ePerson, String scriptName, List<DSpaceCommandLineParameter> parameters,
                                     final List<Group> specialGroups, final Locale currentLocale) {
        super(ePerson, scriptName, parameters, specialGroups, currentLocale);
    }


    /**
     * This method will schedule a process to be run, it will trigger the run method for the Script passed along
     * to this method as well as updating the database logic for the Process representing the execution of this script
     * @param script    The script to be ran
     */
    public void schedule(DSpaceRunnable script) {
        String taskExecutorBeanName = configurationService.getProperty("dspace.task.executor",
                                                                       "dspaceRunnableThreadExecutor");
        TaskExecutor taskExecutor = new DSpace().getServiceManager()
                                                .getServiceByName(taskExecutorBeanName, TaskExecutor.class);
        Context context = new Context();
        try {
            Process process = processService.find(context, processId);
            process.setProcessStatus(ProcessStatus.SCHEDULED);
            processService.update(context, process);
            context.complete();
        } catch (SQLException e) {
            log.error("RestDSpaceRunnableHandler with process: {} ran into an SQLException", processId, e);
        } finally {
            if (context.isValid()) {
                context.abort();
            }
        }
        taskExecutor.execute(script);
    }


}
