/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.checker;

import java.sql.SQLException;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.core.Context;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The {@link ScriptConfiguration} for the {@link ChecksumCheckerScript} script
 *
 */
public class ChecksumCheckerScriptConfiguration<T extends ChecksumCheckerScript> extends ScriptConfiguration<T> {

    @Autowired
    private AuthorizeService authorizeService;

    private Class<T> dspaceRunnableClass;

    @Override
    public Class<T> getDspaceRunnableClass() {
        return this.dspaceRunnableClass;
    }

    @Override
    public void setDspaceRunnableClass(Class<T> dspaceRunnableClass) {
        this.dspaceRunnableClass = dspaceRunnableClass;
    }

    @Override
    public boolean isAllowedToExecute(Context context) {
        try {
            return authorizeService.isAdmin(context);
        } catch (SQLException e) {
            throw new RuntimeException("SQLException occurred when checking if the current user is an admin", e);
        }
    }

    @Override
    public Options getOptions() {
        Options options = new Options();

        options.addOption("l", "looping", false, "Loop once through bitstreams");
        options.addOption("L", "continuous", false,
                "Loop continuously through bitstreams");
        options.addOption("h", "help", false, "Help");
        options.addOption("d", "duration", true, "Checking duration");
        options.addOption("c", "count", true, "Check count");
        options.addOption("a", "handle", true, "Specify a handle to check");
        options.addOption("v", "verbose", false, "Report all processing");

        Option option;

        option = Option.builder("b")
                .longOpt("bitstream-ids")
                .hasArgs()
                .desc("Space separated list of bitstream ids")
                .build();
        options.addOption(option);

        option = Option.builder("p")
                .longOpt("prune")
                .optionalArg(true)
                .desc("Prune old results (optionally using specified properties file for configuration)")
                .build();
        options.addOption(option);
        options.addOption(
            Option.builder("D")
                  .longOpt("droid")
                  .optionalArg(true)
                  .desc("Execute verification with DROID (i.e. digital preservation)")
                  .build()
        );
        return options;
    }
}
