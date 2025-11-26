/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.submissionform.script.dto.InputFormExcel;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InputFormValuePairs - Class to manage the value-pairs form definition from excel file
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class InputFormValuePairs extends InputFormExcel {

    private static final Logger log = LoggerFactory.getLogger(InputFormValuePairs.class);

    /**
     * Genero gli elenchi delle form-value-pairs che ho trovati nel foglio
     * form-value-pairs
     * 
     * @param formValuePairs
     * @param fileExcel
     *            Path del file excel dal quale prendo gli elenchi
     * @throws BiffException
     * @throws IOException
     */
    public void create(Element formValuePairs, File fileExcel, String locale) throws BiffException, IOException {
        // Set encoding for workbook
        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding(CHAR_ENCODING);
        ws.setSuppressWarnings(true);

        Workbook workbook = Workbook.getWorkbook(fileExcel, ws);
        Element valuePairsEl;
        Element pair;
        Sheet sheet;

        // Sheet value-pair
        int totalSheets = workbook.getNumberOfSheets();

        // Start from sheet 3
        for (int i = VALUEPAIRS_SHEET_NAME; i < totalSheets; i++) {
            sheet = workbook.getSheet(i);

            if (sheet.getRows() > 0) {
                int indexRow = 1;
                int numeroColonne = sheet.getRow(0).length;
                int delta = getValuePairColumnsDelta();

                Cell[] columnStoredValue;

                // For each column
                for (int j = 0; j < numeroColonne; j = j + delta) {
                    // Stored value (is at delta - 1)
                    // 0 | 1 | 2 | 3
                    // it |en |stored | (new pair)
                    columnStoredValue = sheet.getColumn(j + (delta - 1));

                    valuePairsEl = new Element("value-pairs");
                    formValuePairs.addContent(valuePairsEl);
                    valuePairsEl.setAttribute("value-pairs-name", sheet.getColumn(j)[posFormName].getContents()
                                                                                                       .trim());
                    valuePairsEl.setAttribute("dc-term", columnStoredValue[posFormName].getContents().trim());

                    // Retrieve stored value and user value for element pair
                    indexRow = 1;
                    while (indexRow < sheet.getColumn(j).length) {
                        try {
                            pair = new Element("pair");
                            String displayedValue = getMultiLangDisplayUnion( sheet, j, indexRow, locale);
                            String storedValue = "";

                            if (indexRow < columnStoredValue.length) {
                                storedValue = columnStoredValue[indexRow].getContents().trim();
                            }

                            if (!displayedValue.equals("")) {
                                pair.addContent(new Element("displayed-value").setText(displayedValue));
                                pair.addContent(new Element("stored-value").setText(storedValue));
                                valuePairsEl.addContent(pair);
                            }
                            indexRow++;
                        } catch (RuntimeException e) {
                            System.out.println("colonna(j): " + j + "  indexColonna(riga): " + indexRow);
                            log.error(e.getMessage() + " colonna(j): " + j + "  indexColonna(riga): " + indexRow);
                            throw e;
                        }
                    }
                }
            }
        }
    }

    public List<String> getQualdropValue(Workbook workbook, String listname) {
        List<String> qualifiers = new ArrayList<>();
        Sheet sheet = workbook.getSheet(1);
        if (sheet.getRows() > 0) {
            Cell[] colonna1;
            Cell[] colonna2;
            int indexColonna = 1;
            int numeroRighe = sheet.getRow(0).length;
            for (int j = 0; j < numeroRighe; j = j + 2) {
                colonna1 = sheet.getColumn(j);
                colonna2 = sheet.getColumn(j + 1);
                if (listname.equals(colonna1[posFormName].getContents().trim())) {
                    while (indexColonna < colonna1.length) {
                        try {
                            String storedValue = colonna2[indexColonna].getContents().trim();
                            if (StringUtils.isNotBlank(colonna1[indexColonna].getContents())) {
                                qualifiers.add(storedValue);
                            }
                            indexColonna++;
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                            System.out.println("colonna(j): " + j + "  indexColonna(riga): " + indexColonna);
                            throw e;
                        }
                    }
                }
            }
        }
        return qualifiers;
    }

    private String getMultiLangDisplayUnion(Sheet sheet, int j, int indexRow, String locale) {
        StringBuilder multiCellStringConcat = new StringBuilder();
        if (StringUtils.isNotBlank(locale)) {
            int extraLanguageCount = getIndexExtraLanguages(locale);
            multiCellStringConcat.append(sheet.getColumn(j + extraLanguageCount + 1)[indexRow].getContents().trim());
        } else {
            multiCellStringConcat.append(sheet.getColumn(j)[indexRow].getContents().trim());
        }
        return multiCellStringConcat.toString();
    }

}
