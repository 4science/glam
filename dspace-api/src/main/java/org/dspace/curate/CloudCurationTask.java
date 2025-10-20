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

/**
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 **/
public interface CloudCurationTask extends CurationTask {

    int perform(Context ctx, Item item, ScheduledProcess scheduledProcess);

}
