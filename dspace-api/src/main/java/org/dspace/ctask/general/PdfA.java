/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.general;

import java.io.IOException;

import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.curate.CloudCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.ScheduledProcess;

/**
 * PdfA curation task.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class PdfA implements CloudCurationTask {

    @Override
    public int perform(Context ctx, Item item, ScheduledProcess scheduledProcess) {
        return 0;
    }

    @Override
    public void init(Curator curator, String taskId) throws IOException {

    }

    @Override
    public int perform(DSpaceObject dso) throws IOException {
        return 0;
    }

    @Override
    public int perform(Context ctx, String id) throws IOException {
        return 0;
    }

    @Override
    public boolean isCloudCurationTask() {
        return true;
    }

}
