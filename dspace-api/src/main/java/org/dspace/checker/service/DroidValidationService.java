/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.service;

import org.dspace.checker.DroidValidationException;
import org.dspace.checker.service.AbstractDroidValidationService.DroidValidationState;
import org.dspace.content.Bitstream;
import org.dspace.core.Context;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public interface DroidValidationService {
    DroidValidationState validate(Context context, Bitstream bitstream) throws DroidValidationException;
}
