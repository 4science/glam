/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a standard base {@link CSVWriter} that would be used to handle the writing of a given
 * CSV file {@link BaseCSVWriter#file}.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class BaseCSVWriter implements CSVWriter {

    private static final Logger log = LoggerFactory.getLogger(BaseCSVWriter.class);
    protected PrintWriter writer;
    protected File file;
    private boolean headerSet;

    /**
     * Builder method to declare the {@code file} to set as output
     *
     * @param file
     * @return
     */
    public BaseCSVWriter with(File file) {
        if (this.writer == null) {
            setFile(file);
            try {
                FileWriter fw = new FileWriter(file, true);
                BufferedWriter bw = new BufferedWriter(fw);
                setWriter(new PrintWriter(bw, true));
            } catch (IOException e) {
                log.error("Cannot open the output file for the export!", e);
                throw new RuntimeException(e);
            }
        }
        return this;
    }

    public BaseCSVWriter with(PrintWriter writer) {
        setWriter(writer);
        return this;
    }

    private void setFile(File file) {
        this.file = file;
    }

    protected void setWriter(PrintWriter writer) {
        this.writer = writer;
    }

    @Override
    public boolean isHeaderSet() {
        return headerSet;
    }

    @Override
    public void writeHeader(String header) {
        if (writer == null) {
            throw new IllegalStateException("Cannot write header without a proper writer!");
        }
        if (file != null && file.length() != 0) {
            throw new IllegalStateException("Cannot write header into a non-empty file!");
        }
        writeRow(header);
        writer.flush();
        this.headerSet = true;
    }

    @Override
    public void writeRow(String rowElement) {
        writer.println(rowElement);
    }

    @Override
    public void close() throws IOException {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (Exception e) {
            log.error("Cannot close the csv writer", e);
            throw new IOException("Cannot close the csv writer", e);
        }
    }
}
