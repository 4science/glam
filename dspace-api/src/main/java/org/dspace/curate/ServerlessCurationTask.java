/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.sql.SQLException;
import java.util.List;

import com.amazonaws.services.s3.AmazonS3;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.curate.service.CurationTaskResult;

/**
 * Interface for serverless curation tasks.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 **/
public interface ServerlessCurationTask extends CurationTask {

    CurationTaskResult initPerform(Context context, AmazonS3 amazonS3, ScheduledCurationTask scheduledTask,
                                   String processId);

    void finalizeTask(Context context, Item item, CurationTaskResult CurationTaskResult)
         throws SQLException, AuthorizeException;

    List<Bitstream> getProcessableBitstreams(Context context, Item item) throws SQLException;

}
