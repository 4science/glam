/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.event.Event;
import org.dspace.event.NamedConsumer;

/**
 * Consumer that handles curation tasks for archived items in response to system events.
 * Delegates the actual task scheduling to CurationTaskScheduler.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class CurationTaskConsumer extends NamedConsumer {

    private CurationTaskScheduler curationTaskScheduler;

    @Override
    public void initialize() throws Exception {
        this.curationTaskScheduler = CurationTaskSchedulerFactory.getInstance().getCurationTaskScheduler();
    }

    @Override
    public void consume(Context context, Event event) throws Exception {
        Item item = (Item) event.getSubject(context);
        if (item == null || !item.isArchived()) {
            return;
        }
        context.turnOffAuthorisationSystem();
        try {
            curationTaskScheduler.scheduleCurationTaskProcess(context, item);
        } finally {
            context.restoreAuthSystemState();
        }
    }

    @Override
    public void end(Context context) throws Exception { }

    @Override
    public void finish(Context context) throws Exception { }

}
