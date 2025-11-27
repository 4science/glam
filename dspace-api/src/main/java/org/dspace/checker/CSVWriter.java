/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.io.Closeable;
import java.io.IOException;

/**
 * Common interface that cares about writing a CSV file to a file.
 * The details of the {@code file} needs to be provided by the implementation.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public interface CSVWriter extends Closeable {
    /**
     * Checks if the header has been set.
     * @return true if has been set, false otherwise.
     */
    boolean isHeaderSet();

    /**
     * Writes the header of the CSV file.
     *
     * @param header
     */
    void writeHeader(String header);

    /**
     * Writes a string to the output file just like a simple row.
     *
     * @param rowElement
     */
    void writeRow(String rowElement);

    /**
     * Closes the stream of the file.
     * @throws IOException
     */
    void close() throws IOException;
}
