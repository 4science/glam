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

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.dspace.app.submissionform.script.dto.InputFormExcel;
import org.jdom2.Element;

public class SubmissionDefinitions extends InputFormExcel {

    public void create(Element submissionDefinitions, File fileExcel) throws BiffException, IOException {
        List<Element> processEls = createSubmissionProcessElement(fileExcel);
        for (Element processEl : processEls) {
            submissionDefinitions.addContent(processEl);
        }
    }

    private List<Element> createSubmissionProcessElement(File fileExcel) throws BiffException, IOException {
        // Set encoding for workbook
        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding(CHAR_ENCODING);
        ws.setSuppressWarnings(true);

        Workbook workbook = Workbook.getWorkbook(fileExcel, ws);
        List<Element> processEls = new ArrayList<Element>();
        // Sheet input form
        Sheet sheet = workbook.getSheet(SUBMISSIONSDEFINITION_SHEET_NAME);

        // Sheet rows
        int sheetRows = sheet.getColumn(0).length;
        int indexSheetRow = 1;

        // First input form information row (row=1)
        this.sheetRow = sheet.getRow(indexSheetRow);

        // For each row on sheet
        while (indexSheetRow < sheetRows) {
            Element processEl = new Element("submission-process");
            String processName = get(posSubmissionId);
            processEl.setAttribute("name", processName);

            // for each cell on submission (base on cell[0] value)
            while (processName.equals(get(posSubmissionId)) && indexSheetRow < sheetRows) {
                // New step for current sheet
                String stepId = get(posSubmissionStepId);
                Element stepEl = new Element("step");
                stepEl.setAttribute("id", stepId);
                processEl.addContent(stepEl);

                indexSheetRow++;
                if (indexSheetRow < sheetRows) {
                    // get next sheet row
                    sheetRow = sheet.getRow(indexSheetRow);
                }
            }
            processEls.add(processEl);
        }
        return processEls;
    }

}
