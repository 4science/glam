/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of CurationTaskResolverService that resolves both server-based
 * and serverless curation tasks.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class CurationTaskResolverServiceImpl implements CurationTaskResolverService {

    private static final Logger log = LogManager.getLogger(CurationTaskResolverServiceImpl.class);

    private TaskResolver resolver = new TaskResolver();
    private List<ServerlessCurationTask> serverlessCurationTasks;

    @Override
    public ResolvedTask resolveTask(String taskName, Curator curator) throws IOException {
        Optional<ServerlessCurationTask> serverlessTask = serverlessCurationTasks.stream()
                                                     .filter(task -> task.getTaskName().equals(taskName))
                                                     .findFirst();
        if (serverlessTask.isPresent()) {
            ResolvedTask resolvedServerlessTask = new ResolvedTask(taskName, serverlessTask.get());
            resolvedServerlessTask.init(curator);
            return resolvedServerlessTask;
        } else {
            ResolvedTask resolvedServerTask = resolver.resolveTask(taskName);
            if (resolvedServerTask == null) {
                log.error("Curation task " + taskName + " could not be resolved");
                return null;
            }
            resolvedServerTask.init(curator);
            return resolvedServerTask;
        }
    }

    public void setServerlessCurationTasks(List<ServerlessCurationTask> serverlessCurationTasks) {
        this.serverlessCurationTasks = serverlessCurationTasks;
    }

}
