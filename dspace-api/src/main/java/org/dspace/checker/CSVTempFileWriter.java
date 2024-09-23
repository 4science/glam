/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class can be used to create a temporary file starting from a given configuration. <br/>
 * The CSV collected will be written to that file.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class CSVTempFileWriter implements CSVWriter {

    public static final String TEMP_FILENAME_SEPARATOR = "_";
    public static final String CSV_EXTENSION = ".csv";
    private static final Logger log = LoggerFactory.getLogger(CSVTempFileWriter.class);

    protected String directory;
    protected String prefix;
    protected String suffix;
    protected String separator = TEMP_FILENAME_SEPARATOR;
    protected File outputFile;

    protected CSVWriter csvWriter;

    public CSVTempFileWriter(String directory, String prefix, String suffix) {
        setDirectory(directory);
        setPrefix(prefix);
        setSuffix(suffix);
    }

    public CSVTempFileWriter withSeparator(String separator) {
        setSeparator(separator);
        return this;
    }

    protected void setSeparator(String separator) {
        this.separator = separator;
    }

    public CSVTempFileWriter withPrefix(String prefix) {
        setPrefix(prefix);
        return this;
    }

    protected void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public CSVTempFileWriter withSuffix(String suffix) {
        setSuffix(suffix);
        return this;
    }

    protected void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public CSVTempFileWriter withDirectory(String directoryPath) {
        setDirectory(directoryPath);
        return this;
    }

    protected void setDirectory(String directory) {
        this.directory = directory;
    }

    private File createOutputFile() {
        log.info("Trying to create the output file.");
        try {

            File tempDirectory = getTempDirectory(Paths.get(this.directory));
            outputFile = File.createTempFile(getTempPrefix(), getTempSuffix(), tempDirectory);

            log.info("The results will be saved to {}: ", outputFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputFile;
    }

    protected File getTempDirectory(Path directoryPath) throws IOException {
        File tempDirectory = directoryPath.toFile();
        if (!tempDirectory.exists()) {
            tempDirectory = Files.createDirectory(directoryPath).toFile();
        }
        return tempDirectory;
    }

    protected String getTempSuffix() {
        return separator + suffix + getExtension();
    }

    protected String getTempPrefix() {
        return prefix + separator;
    }

    protected String getExtension() {
        return CSV_EXTENSION;
    }

    public CSVWriter getCSVWriter() {
        if (this.csvWriter == null) {
            this.csvWriter = new BaseCSVWriter().with(createOutputFile());
        }
        return csvWriter;
    }

    @Override
    public boolean isHeaderSet() {
        return csvWriter != null && csvWriter.isHeaderSet();
    }

    @Override
    public void writeHeader(String header) {
        this.getCSVWriter().writeHeader(header);
    }

    @Override
    public void writeRow(String rowElement) {
        this.getCSVWriter().writeRow(rowElement);
    }

    @Override
    public void close() throws IOException {
        this.getCSVWriter().close();
    }
}
