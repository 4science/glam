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
import java.util.stream.Collectors;

import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.StringUtils;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.scripts.DSpaceCommandLineParameter;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.dspace.scripts.service.ProcessService;
import org.dspace.scripts.service.ScriptService;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * This service orchestrates the scheduling of curation tasks using the ProcessService.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
@Service
public class CurationTaskScheduler {

    private static final Logger log = LogManager.getLogger(CurationTaskScheduler.class);

    @Autowired
    ProcessService processService;

    @Autowired
    ScriptService scriptService;

    @Autowired
    ConfigurationService configurationService;

    @Autowired
    ItemService itemService;

    public void scheduleCurationTaskProcess(Context context, Item item) throws SQLException {
        String curationMetadata = this.configurationService.getProperty("curation.task.metadata.definition");
        if (StringUtils.isEmpty(curationMetadata)) {
            return;
        }

        List<MetadataValue> metadata =
            this.itemService.getMetadataByMetadataString(item, curationMetadata);
        if (metadata.isEmpty()) {
            return;
        }

        List<DSpaceCommandLineParameter> params =
            metadata.stream()
                    .map(MetadataValue::getValue)
                    .map(value -> new DSpaceCommandLineParameter("-task", value))
                    .collect(Collectors.toList());
        params.add(new DSpaceCommandLineParameter("-id", item.getID().toString()));
        String scriptName = "curateOrchestrator";

        ScriptConfiguration<?> scriptConfiguration =
            this.scriptService.getScriptConfiguration(scriptName);

        if (scriptConfiguration == null || !scriptConfiguration.isAllowedToExecute(context, params)) {
            return;
        }

        try {
            DSpaceRunnable<?> scriptInstance =
                this.scriptService.createDSpaceRunnableForScriptConfiguration(scriptConfiguration);
        } catch (IllegalAccessException | InstantiationException e) {
            log.error("Cannot initialize the script {}!", scriptName, e);
            return;
        }

        EPerson currentUser = context.getCurrentUser();
        DSpaceProcessRunnableHandler dSpaceProcessRunnableHandler =
            new DSpaceProcessRunnableHandler(currentUser, scriptName, params, context.getSpecialGroups(),
                                             context.getCurrentLocale());

        try {
            DSpaceRunnable<?> dSpaceRunnable =
                scriptService.createDSpaceRunnableForScriptConfiguration(scriptConfiguration);
            List<String> args = new ArrayList<>();
            for (DSpaceCommandLineParameter parameter : params) {
                args.add(parameter.getName());
                if (parameter.getValue() != null) {
                    args.add(parameter.getValue());
                }
            }

            dSpaceRunnable.initialize(
                args.toArray(new String[0]),
                dSpaceProcessRunnableHandler,
                currentUser
            );
            dSpaceProcessRunnableHandler.schedule(dSpaceRunnable);
        } catch (IllegalAccessException | InstantiationException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

}
