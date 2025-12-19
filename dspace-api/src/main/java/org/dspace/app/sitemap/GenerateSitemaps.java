/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.sitemap;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dspace.utils.DSpace;

/**
 * Command-line utility for generating HTML and Sitemaps.org protocol Sitemaps.
 *
 * @author Robert Tansley
 * @author Stuart Lewis
 */
public class GenerateSitemaps {

    private static final SitemapService sitemapService = new DSpace().getServiceManager()
                                                .getServiceByName("sitemapService", SitemapService.class);
    /**
     * Default constructor
     */
    private GenerateSitemaps() { }

    public static void main(String[] args) throws Exception {
        final String usage = GenerateSitemaps.class.getCanonicalName();

        CommandLineParser parser = new DefaultParser();
        HelpFormatter hf = new HelpFormatter();

        Options options = new Options();

        options.addOption("h", "help", false, "help");
        options.addOption("s", "no_sitemaps", false,
                          "do not generate sitemaps.org protocol sitemap");
        options.addOption("b", "no_htmlmap", false,
                          "do not generate a basic HTML sitemap");
        options.addOption("d", "delete", false,
                "delete sitemaps dir and its contents");

        CommandLine line = null;

        try {
            line = parser.parse(options, args);
        } catch (ParseException pe) {
            hf.printHelp(usage, options);
            System.exit(1);
        }

        if (line.hasOption('h')) {
            hf.printHelp(usage, options);
            System.exit(0);
        }

        if (line.getArgs().length != 0) {
            System.err.println("No arguments expected");
            hf.printHelp(usage, options);
            System.exit(1);
        }

        if (line.getOptions().length > 1 ) {
            System.err.println("Too many options specified");
            hf.printHelp(usage, options);
            System.exit(1);
        }

        // Note the negation (CLI options indicate NOT to generate a sitemap)
        if (!line.hasOption('b') || !line.hasOption('s')) {
            generateSitemaps(!line.hasOption('b'), !line.hasOption('s'));
        }

        if (line.hasOption('d')) {
            deleteSitemaps();
        }

        System.exit(0);
    }

    /**
     * Runs generate-sitemaps without any params for the scheduler (task-scheduler.xml).
     *
     * @throws SQLException if a database error occurs.
     * @throws IOException  if IO error occurs.
     */
    public static void generateSitemapsScheduled() throws IOException, SQLException {
        sitemapService.generateSitemaps(true, true);
    }

    /**
     * Delete the sitemaps directory and its contents if it exists
     * @throws IOException  if IO error occurs
     */
    public static void deleteSitemaps() throws IOException {
        sitemapService.deleteSitemaps();
    }

    /**
     * Generate sitemap.org protocol and/or basic HTML sitemaps.
     *
     * @param makeHTMLMap    if {@code true}, generate an HTML sitemap.
     * @param makeSitemapOrg if {@code true}, generate an sitemap.org sitemap.
     * @throws SQLException if database error
     *                      if a database error occurs.
     * @throws IOException  if IO error
     *                      if IO error occurs.
     */
    public static void generateSitemaps(boolean makeHTMLMap, boolean makeSitemapOrg) throws SQLException, IOException {
        sitemapService.generateSitemaps(makeHTMLMap, makeSitemapOrg);
    }
}
