/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.service;

import java.util.List;

import org.dspace.checker.DroidCheckResult;
import org.dspace.checker.service.AbstractDroidValidationService.DroidValidationState;
import org.dspace.core.Context;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public abstract class AbstractDroidCheckResultService implements DroidCheckResultService {

    protected List<DroidCheckResult> toCheckResults(
        Context context,
        AbstractDroidValidationMapper mapper,
        DroidValidationState state
    ) {
        return mapper.map(context, state);
    }

    protected static abstract class AbstractDroidValidationMapper {
        abstract protected List<DroidCheckResult> map(Context context, DroidValidationState state);
    }
}
