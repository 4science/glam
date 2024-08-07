/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.util.function.Function;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
final class EvaluationContext {

    interface EvaluationContextMapper
        extends Function<EvaluationContext, String> {
    }

    final MostRecentChecksum checksum;
    final DroidCheckResult droidCheckResult;

    public EvaluationContext(MostRecentChecksum checksum, DroidCheckResult droidCheckResult) {
        this.checksum = checksum;
        this.droidCheckResult = droidCheckResult;
    }
}
