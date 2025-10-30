/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.amazonaws.services.s3.AmazonS3;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.core.Context;

/**
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 **/
public interface ServerlessCurationTask extends CurationTask {

    int perform(Context context, Item item, AmazonS3 amazonS3, ScheduledCurationTask scheduledTask, String processId)
            throws IOException;

    List<Bitstream> getProcessableBitstreams(Context context, Item item) throws SQLException;

}
