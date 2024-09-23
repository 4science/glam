/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.dspace.core.Context;

/**
 * Composite of multiple {@link ChecksumResultsCollector}
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class ChecksumResultCollectorComposite implements ChecksumResultsCollector {

    final List<ChecksumResultsCollector> checksumResultsCollectors;

    public ChecksumResultCollectorComposite(
        List<ChecksumResultsCollector> checksumResultsCollectors
    ) {
        this.checksumResultsCollectors = checksumResultsCollectors;
    }

    @Override
    public void collect(Context context, MostRecentChecksum info) throws SQLException {
        for (ChecksumResultsCollector checksumResultsCollector : checksumResultsCollectors) {
            checksumResultsCollector.collect(context, info);
        }
    }

    @Override
    public void complete(Context context) throws SQLException {
        for (ChecksumResultsCollector checksumResultsCollector : checksumResultsCollectors) {
            checksumResultsCollector.complete(context);
        }
    }

    @Override
    public List<File> output(Context context) throws Exception {
        List<File> files = new ArrayList<>(checksumResultsCollectors.size());
        for (ChecksumResultsCollector checksumResultsCollector : checksumResultsCollectors) {
            files.addAll(checksumResultsCollector.output(context));
        }
        return files;
    }
}
