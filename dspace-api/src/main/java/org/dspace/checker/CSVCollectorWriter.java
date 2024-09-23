/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class can be used to transform a generic entity {@code <T>} to a CSV file with the
 * {@link CSVCollectorWriter#csvCollector} and then just write it out using the {@link CSVCollectorWriter#csvWriter}.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class CSVCollectorWriter<T> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CSVCollectorWriter.class);

    protected CSVCollector<T> csvCollector;
    protected CSVWriter csvWriter;

    public CSVCollectorWriter<T> with(CSVCollector<T> csvCollector) {
        setCsvCollector(csvCollector);
        return this;
    }

    private void setCsvCollector(CSVCollector<T> csvCollector) {
        this.csvCollector = csvCollector;
    }

    public CSVCollectorWriter<T> with(CSVWriter csvWriter) {
        setCSVWriter(csvWriter);
        return this;
    }

    private void setCSVWriter(CSVWriter csvWriter) {
        this.csvWriter = csvWriter;
    }

    public void writeRow(T rowElement) {
        headerCheck();
        csvWriter.writeRow(csvCollector.mapToRow(rowElement));
    }

    public void writeRows(List<T> rowElements) {
        headerCheck();
        for (String rowElement : csvCollector.mapToRows(rowElements)) {
            csvWriter.writeRow(rowElement);
        }
    }

    private void headerCheck() {
        if (csvWriter == null) {
            throw new IllegalStateException("Cannot write line without a proper writer!");
        }
        if (csvWriter.isHeaderSet()) {
            return;
        }
        csvWriter.writeHeader(csvCollector.getHeader());
    }

    @Override
    public void close() throws Exception {
        if (this.csvWriter != null) {
            this.csvWriter.close();
        }
    }
}
