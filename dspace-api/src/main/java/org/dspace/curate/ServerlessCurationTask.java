/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.util.List;

import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.ctask.general.CurationTaskException;
import org.dspace.curate.service.CurationTaskResult;
import software.amazon.awssdk.services.s3.S3AsyncClient;

/**
 * Interface for serverless curation tasks.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 **/
public interface ServerlessCurationTask extends CurationTask {

    CurationTaskResult initPerform(Context context, S3AsyncClient amazonS3, ScheduledCurationTask scheduledTask,
                                   String processId);

    void finalizeTask(Context context, Item item, CurationTaskResult CurationTaskResult)
         throws CurationTaskException;

    List<Bitstream> getProcessableBitstreams(Context context, String task, Item item) throws CurationTaskException;

    String getTaskName();

}
