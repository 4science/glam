/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.checker;

import org.dspace.core.Context;
import org.dspace.scripts.configuration.ScriptConfiguration;

/**
 * The {@link ScriptConfiguration} for the {@link ChecksumCheckerCliScript} script
 *
 */
public class ChecksumCheckerCliScriptConfiguration
        extends ChecksumCheckerScriptConfiguration<ChecksumCheckerCliScript> {

    @Override
    public boolean isAllowedToExecute(Context context) {
        return true;
    }
}
