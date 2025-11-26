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

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.submissionform.script.dto.InputFormExcel;
import org.dspace.app.util.DCInput;
import org.jdom2.Element;

public class StepDefinitions extends InputFormExcel {

    public void create(Element stepDefinitions, File fileExcel) throws BiffException, IOException {
        String formName;
        String rowNumber;

        // DOM element for form, page, field and input type
        Element stepEl;
        Element page;
        Element field;
        Element inputType;

        // Set encoding for workbook
        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding(CHAR_ENCODING);
        ws.setSuppressWarnings(true);

        Workbook workbook = Workbook.getWorkbook(fileExcel, ws);

        // Sheet input form
        Sheet sheet = workbook.getSheet(STEPSDEFINITION_SHEET_NAME);

        // Sheet rows
        int sheetRows = sheet.getColumn(0).length;
        int indexSheetRow = 1;

        // First input form information row (row=1)
        this.sheetRow = sheet.getRow(indexSheetRow);

        // For each row on sheet
        while (indexSheetRow < sheetRows) {
            // New step for current sheet
            String stepId = get(posStepId);
            String stepType = get(posStepType);
            String restrictions = get(posStepVisibility);
            boolean required = Boolean.parseBoolean(get(posStepRequired));
            Boolean opened = get(posStepOpened).isEmpty() ? null : Boolean.parseBoolean(get(posStepOpened));

            stepEl = createStepDefinitionElement(stepId, stepType, required, restrictions, opened);
            stepDefinitions.addContent(stepEl);

            indexSheetRow++;
            if (indexSheetRow < sheetRows) {
                // get next sheet row
                sheetRow = sheet.getRow(indexSheetRow);
            }
        }
    }

    private Element createStepDefinitionElement(String stepId, String stepType, boolean required, String restrictions,
                                                Boolean opened) {
        Element stepEl = new Element("step-definition");
        stepEl.setAttribute("id", stepId);

        if (required) {
            stepEl.setAttribute("mandatory", "true");
        } else {
            stepEl.setAttribute("mandatory", "false");
        }

        if (opened != null) {
            stepEl.setAttribute("opened", opened.toString());
        }
        stepEl.addContent(new Element("heading").setText(getHeadingByStepTypeAndId(stepType, stepId)));
        stepEl.addContent(new Element("processing-class").setText(getProcessingClassByStepType(stepType)));
        stepEl.addContent(new Element("type").setText(stepType));

        if (StringUtils.isNotBlank(restrictions)) {
            // New element
            Element restrictionEl = new Element("scope");
            if (restrictions.equalsIgnoreCase("hidden")) {
                restrictionEl.setText("submission");
                restrictionEl.setAttribute("visibility", "hidden");
                restrictionEl.setAttribute("visibilityOutside", "hidden");
            } else if (restrictions.equalsIgnoreCase("readonly")) {
                restrictionEl.setText("submission");
                restrictionEl.setAttribute("visibility", "read-only");
                restrictionEl.setAttribute("visibilityOutside", "read-only");
            } else {
                // New element
                if (StringUtils.containsIgnoreCase(restrictions, DCInput.WORKFLOW_SCOPE)) {
                    restrictionEl.setText(DCInput.WORKFLOW_SCOPE);
                } else if (StringUtils.containsIgnoreCase(restrictions, "submission")) {
                    restrictionEl.setText(DCInput.SUBMISSION_SCOPE);
                } else {
                    restrictionEl.setText("all");
                }

                if (StringUtils.containsIgnoreCase(restrictions, "readonly")) {
                    restrictionEl.setAttribute("visibility", "read-only");
                }

                if (StringUtils.containsIgnoreCase(restrictions, "limited")) {
                    restrictionEl.setAttribute("visibilityOutside", "hidden");
                }
            }
            stepEl.addContent(restrictionEl);
        }
        return stepEl;
    }

    private String getProcessingClassByStepType(String stepType) {
        String processingClass;
        switch (stepType) {
            case "collection":
                processingClass = collectionStepClass;
                break;
            case "submission-form":
                processingClass = formStepClass;
                break;
            case "upload":
                processingClass = uploadStepClass;
                break;
            case "license":
                processingClass = licenseStepClass;
                break;
            case "detect-duplicate":
                processingClass = duplicateStepClass;
                break;
            case "extract":
                processingClass = extractionStepClass;
                break;
            case "cclicense":
                processingClass = cclicenseStepClass;
                break;
            case "accessCondition":
                processingClass = accessesStepClass;
                break;
            case "custom-url":
                processingClass = customUrlStepClass;
                break;
            case "correction":
                processingClass = correctionStepClass;
                break;
            case "sherpaPolicy":
                processingClass = sherpaStepClass;
                break;
            case "unpaywall":
                processingClass = unpaywallStepClass;
                break;
            case "identifiers":
                processingClass = identifiersStepClass;
                break;
            case "external-upload":
                processingClass = externalUploadStepClass;
                break;
            default:
                processingClass = "";
        }
        return processingClass;
    }

    private String getHeadingByStepTypeAndId(String stepType, String stepId) {
        String heading;
        switch (stepType) {
            case "submission-form":
                heading = stepHeadingPrefix + "describe." + stepId;
                break;
            case "extract":
                heading = "submit.progressbar.ExtractMetadataStep";
                break;
            case "cclicense":
                heading = "submit.progressbar.CClicense";
                break;
            default:
                heading = stepHeadingPrefix + stepId;
        }
        return heading;
    }

}
