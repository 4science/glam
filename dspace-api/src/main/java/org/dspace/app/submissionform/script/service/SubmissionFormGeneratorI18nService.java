/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.service;

import jxl.Cell;
import jxl.Sheet;

/**
 * Service interface for managing internationalization (i18n) and language-related operations
 * in submission form generation.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public interface SubmissionFormGeneratorI18nService {

    /**
     * Get the list of extra languages configured for submission forms
     *
     * @return array of language codes (e.g., ["de", "fr"])
     */
    String[] getExtraLanguages();

    /**
     * Get the index of a specific locale in the extra languages array
     *
     * @param locale the locale code to find
     * @return the index of the locale, or 0 if not found
     */
    int getIndexForLocale(String locale);

    /**
     * Get the column delta for value pairs (default | language(s) | stored)
     *
     * @return the number of columns per value pair group
     */
    int getValuePairColumnsDelta();

    /**
     * Get a multi-language cell value from a sheet row
     *
     * @param sheetRow the row of cells
     * @param baseIndex the base index for the default language
     * @param indexOnMulti the index within the multi-language group (0=label, 1=required, 2=hint)
     * @param locale the locale code (null for default language)
     * @return the cell content as a string
     */
    String getMultiLangCellValue(Cell[] sheetRow, int baseIndex, int indexOnMulti, String locale);

    /**
     * Get a multi-language display value from a sheet column
     *
     * @param sheet the Excel sheet
     * @param columnIndex the base column index
     * @param rowIndex the row index
     * @param locale the locale code (null for default language)
     * @return the cell content as a string
     */
    String getMultiLangDisplayValue(Sheet sheet, int columnIndex, int rowIndex, String locale);

    /**
     * Reload extra languages from configuration.
     * This method allows refreshing the cached language list if the configuration has changed.
     */
    void reloadExtraLanguages();
}

