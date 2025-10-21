/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.checker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.checker.BitstreamDispatcher;
import org.dspace.checker.CSVChecksumResultMailCollector;
import org.dspace.checker.CSVDroidChecksumCollector;
import org.dspace.checker.CheckerCommand;
import org.dspace.checker.ChecksumResultsCollector;
import org.dspace.checker.DroidSimpleDispatcher;
import org.dspace.checker.HandleDispatcher;
import org.dspace.checker.IteratorDispatcher;
import org.dspace.checker.LimitedCountDispatcher;
import org.dspace.checker.LimitedDurationDispatcher;
import org.dspace.checker.ResultsLogger;
import org.dspace.checker.ResultsPruner;
import org.dspace.checker.SimpleDispatcher;
import org.dspace.checker.factory.CheckerServiceFactory;
import org.dspace.checker.service.MostRecentChecksumService;
import org.dspace.content.Bitstream;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.core.Utils;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.scripts.handler.DSpaceRunnableHandler;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

public class ChecksumCheckerScript<T extends ChecksumCheckerScriptConfiguration<?>> extends DSpaceRunnable<T> {
    private static final Logger LOG = LogManager.getLogger(ChecksumCheckerScript.class);
    private static final BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    /**
     * Access for bitstream information
     */
    protected final MostRecentChecksumService checksumService =
        CheckerServiceFactory.getInstance().getMostRecentChecksumService();

    protected final ConfigurationService configurationService =
        DSpaceServicesFactory.getInstance().getConfigurationService();

    protected BitstreamDispatcher getSimpleDispatcher(Context context, Date startTime, boolean looping,
                                                                boolean isDroid) {
        if (isDroid) {
            return new DroidSimpleDispatcher(context, startTime, looping);
        }
        return new SimpleDispatcher(context, startTime, looping);
    }


    @Override
    public T getScriptConfiguration() {
        return (T) DSpaceServicesFactory
                .getInstance().getServiceManager()
                .getServiceByName("checksum-checker", ChecksumCheckerScriptConfiguration.class);
    }

    @Override
    public void setup() throws ParseException {
    }

    protected void handleCollectorOutput(
        DSpaceRunnableHandler handler,
        List<ChecksumResultsCollector> collectors,
        Context context
    ) throws SQLException {
        List<File> files = null;
        try {
            files =
                collectors.stream()
                          .flatMap(collector -> {
                              try {
                                  return collector.output(context).stream();
                              } catch (Exception e) {
                                  LOG.error("Cannot retrieve the files file of the collectors!", e);
                                  handler.logError("Cannot retrieve the files file of the collectors!", e);
                              }
                              return Stream.empty();
                          })
                          .toList();
        } catch (Exception e) {
            LOG.error("Cannot retrieve the files file of the collectors!", e);
            handler.logError("Cannot retrieve the files file of the collectors!", e);
        } finally {
            if (files != null) {
                for (File output : files) {
                    writeOutputFile(handler, context, output);
                }
            }
        }
    }

    protected void writeOutputFile(DSpaceRunnableHandler handler, Context context, File file)
        throws SQLException {
        if (file == null || !file.exists() || !file.isFile()) {
            return;
        }
        String tempDir = getTempDir();

        File tempDirFile = new File(tempDir);
        if (!tempDirFile.exists()) {
            if (!tempDirFile.mkdirs()) {
                LOG.error("Unable to create the tempDir folder: {}", tempDir);
                handler.logError("Unable to create the tempDir folder: " + tempDir);
            }
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            context.turnOffAuthorisationSystem();
            handler.writeFilestream(
                context,
                Paths.get(tempDir, file.getName()).toAbsolutePath().toString(),
                fis,
                FilenameUtils.getExtension(file.getName())
            );
        } catch (IOException | AuthorizeException e) {
            LOG.error("Cannot retrieve the output of the process!", e);
            handler.logError("Cannot retrieve the output of the process!", e);
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private String getTempDir() {
        return Optional.ofNullable(
                           configurationService.getProperty("checksum-checker.collect.files.output.dir")
                       ).or(() -> Optional.ofNullable(configurationService.getProperty("upload.temp.dir")))
                       .orElseGet(() -> System.getProperty("java.io.tmpdir"));
    }

    @Override
    public void internalRun() throws Exception {
        // user asks for help
        if (commandLine.hasOption('h')) {
            printHelp();
        }
        Context context = null;
        try {
            context = new Context();

            // Prune stage
            if (commandLine.hasOption('p')) {
                ResultsPruner rp = null;
                try {
                    rp = (commandLine.getOptionValue('p') != null) ? ResultsPruner
                            .getPruner(context, commandLine.getOptionValue('p')) : ResultsPruner
                            .getDefaultPruner(context);
                } catch (FileNotFoundException e) {
                    LOG.error("File not found", e);
                    System.exit(1);
                }
                int count = rp.prune();
                System.out.println("Pruned " + count + " old results from the database.");
            }

            Date processStart = Calendar.getInstance(TimeZone.getDefault()).getTime();

            BitstreamDispatcher dispatcher = null;
            boolean isDroidCheck = commandLine.hasOption('D');

            // process should loop infinitely through
            // most_recent_checksum table
            if (commandLine.hasOption('b')) {
                // check only specified bitstream(s)
                String[] ids = commandLine.getOptionValues('b');
                List<Bitstream> bitstreams = new ArrayList<>(ids.length);

                for (String id : ids) {
                    try {
                        bitstreams.add(bitstreamService.find(context, UUID.fromString(id)));
                    } catch (IllegalArgumentException ie) {
                        handler.logError("The following argument: " + id + " is not an UUID", ie);
                        throw ie;
                    }
                }
                dispatcher = new IteratorDispatcher(bitstreams.iterator());
            } else if (commandLine.hasOption('a')) {
                dispatcher = new HandleDispatcher(context, commandLine.getOptionValue('a'));
            } else {
                // dispatchers with indefinite loops
                if (commandLine.hasOption('l')) {
                    dispatcher = getSimpleDispatcher(context, processStart, false, isDroidCheck);
                } else if (commandLine.hasOption('L')) {
                    dispatcher = getSimpleDispatcher(context, processStart, true, isDroidCheck);
                } else if (commandLine.hasOption('d')) {
                    // run checker process for specified duration
                    try {
                        dispatcher = new LimitedDurationDispatcher(
                            getSimpleDispatcher(context, processStart, true, isDroidCheck),
                            new Date(System.currentTimeMillis() + Utils.parseDuration(commandLine.getOptionValue('d')))
                        );
                    } catch (Exception e) {
                        handler.logError("Couldn't parse " + commandLine.getOptionValue('d')
                                             + " as a duration: ", e);
                        throw e;
                    }
                } else if (commandLine.hasOption('c')) {
                    int count = Integer.valueOf(commandLine.getOptionValue('c'));

                    // run checker process for specified number of bitstreams
                    dispatcher = new LimitedCountDispatcher(
                        getSimpleDispatcher(context, processStart, false, isDroidCheck),
                        count
                    );
                } else {
                    dispatcher = new LimitedCountDispatcher(
                        getSimpleDispatcher(context, processStart, false, isDroidCheck),
                        1
                    );
                }
            }


            List<ChecksumResultsCollector> collectors = new ArrayList<>();
            boolean isReportVerbose = commandLine.hasOption('v');
            boolean isMailReport = commandLine.hasOption('m');

            if (isReportVerbose) {
                collectors.add(new ResultsLogger(processStart));
            }

            if (isDroidCheck) {
                collectors.add(new CSVDroidChecksumCollector());
            }

            if (isMailReport) {
                collectors.add(new CSVChecksumResultMailCollector());
            }

            CheckerCommand checker =
                new CheckerCommand(context)
                    .setProcessStartDate(processStart)
                    .setDispatcher(dispatcher)
                    .setDroidCheck(isDroidCheck)
                    .setReportVerbose(isReportVerbose)
                    .setMailReport(isMailReport)
                    .setHandler(handler)
                    .setCollectors(collectors);

            checker.process();

            if (configurationService.getBooleanProperty("checksum-checker.collect.files", false)) {
                handleCollectorOutput(handler, collectors, context);
            }

            context.complete();
            context = null;
        } finally {
            if (context != null) {
                context.abort();
            }
        }
    }

}
