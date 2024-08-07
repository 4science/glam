/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.script;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.dspace.app.checker.ChecksumChecker;
import org.dspace.scripts.configuration.ScriptConfiguration;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class ChecksumCheckerScriptConfiguration<T extends ChecksumChecker> extends ScriptConfiguration<T> {

    private Class<T> dspaceRunnableClass;

    @Override
    public Class<T> getDspaceRunnableClass() {
        return dspaceRunnableClass;
    }

    /**
     * Generic setter for the dspaceRunnableClass
     * @param dspaceRunnableClass   The dspaceRunnableClass to be set on this ChecksumCheckerScriptConfiguration
     */
    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }


    @Override
    public Options getOptions() {
        Options baseOptions = ChecksumChecker.getOptions();
        baseOptions.addOption(
            Option.builder("D")
                  .longOpt("droid")
                  .optionalArg(true)
                  .desc("Execute verification with DROID (i.e. digital preservation)")
                  .build()
        );
        return baseOptions;
    }
}
