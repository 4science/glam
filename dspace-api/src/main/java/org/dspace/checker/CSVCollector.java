/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generic collector that can transform an object {@code <T>} into a CSV. <br/>
 * This class uses the {@link CSVCollector#lineMappers} to determine the fields of the header and of the rows. <br/>
 * Separates each field of the CSV by using the given {@link CSVCollector#fieldSeparator}
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class CSVCollector<T> {

    protected String fieldSeparator;
    protected Map<String, ? extends Function<T, String>> lineMappers;

    public CSVCollector<T> with(String fieldSeparator) {
        setFieldSeparator(fieldSeparator);
        return this;
    }

    public CSVCollector<T> with(Map<String, ? extends Function<T, String>> lineMappers) {
        setLineMappers(lineMappers);
        return this;
    }

    private void setLineMappers(Map<String, ? extends Function<T, String>> lineMappers) {
        this.lineMappers = lineMappers;
    }

    protected void setFieldSeparator(String fieldSeparator) {
        this.fieldSeparator = fieldSeparator;
    }

    protected String getHeader() {
        if (lineMappers == null) {
            throw new IllegalStateException("Please set line-mappers.");
        }
        if (fieldSeparator == null) {
            throw new IllegalStateException("Please set field-separator.");
        }
        return joinRowElements(getHeaderElements());
    }

    public String mapToRow(T rowElement) {
        return joinRowElements(mapToElements(getHeaderElements(), rowElement));
    }

    public Collection<String> mapToRows(Collection<T> rowElements) {
        return rowElements.stream().map(this::mapToRow).collect(Collectors.toList());
    }

    protected Collection<String> mapToElements(Collection<String> header, T row) {
        if (row == null) {
            return List.of();
        }
        return header.stream().map(entry -> lineMappers.get(entry).apply(row)).collect(Collectors.toList());
    }

    private String joinRowElements(Collection<String> rowElements) {
        return rowElements.stream().collect(Collectors.joining(fieldSeparator));
    }

    private Collection<String> getHeaderElements() {
        return lineMappers.keySet();
    }

}
