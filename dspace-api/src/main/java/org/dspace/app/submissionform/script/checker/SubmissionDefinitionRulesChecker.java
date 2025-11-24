/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.checker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.submissionform.script.builder.InputFormErrorBuilder;
import org.dspace.app.submissionform.script.dto.InputFormExcel;
import org.dspace.app.submissionform.script.util.I18nUtil;
import org.dspace.core.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that checks the submission-definition sheet rules
 */
public class SubmissionDefinitionRulesChecker extends InputFormExcel implements ExcelSheetValidator {

    private static final Logger log = LoggerFactory.getLogger(SubmissionDefinitionRulesChecker.class);

    @Override
    public List<InputFormErrorBuilder> check(File fileExcel, Context context, String defaultDefinition) {
        List<InputFormErrorBuilder> errors = new ArrayList<>();

        Workbook workbook = null;
        StringBuilder errorMessage;
        try {
            // Set workbook
            WorkbookSettings ws = new WorkbookSettings();
            ws.setEncoding(CHAR_ENCODING);
            // Get workbook
            workbook = Workbook.getWorkbook(fileExcel, ws);
        } catch (BiffException | IOException e) {
            log.error(e.getMessage());
            InputFormErrorBuilder.manageError(errors, new StringBuilder(e.getMessage()));
        }

        if (workbook != null) {
            // Array containing all defined step id
            List<String> stepIdList = new ArrayList<>();
            // Form name list
            List<String> definitionNameList = new ArrayList<>();
            String lastDefinitionName = "";
            String currDefinitionName;

            int lastStepOrder = 1;
            int currStepOrder;

            //Input form row
            Sheet sheet = workbook.getSheet(SUBMISSIONSDEFINITION_SHEET_NAME);
            //all sheet rows (based on column 0)
            int rows = sheet.getColumn(0).length;

            //current definition id and step id
            String definitionId;
            String stepId;

            int indexRow;
            int sheetRowNumber;

            for (indexRow = 1; indexRow < rows; indexRow++) {
                sheetRowNumber = indexRow + 1;
                //current sheet row
                this.sheetRow = sheet.getRow(indexRow);
                definitionId = this.get(posSubmissionId);
                stepId = this.get(posSubmissionStepId);
                if (!stepIdList.contains(stepId)) {
                    stepIdList.add(stepId);
                }

                // No right step id set on cell
                if (StringUtils.isBlank(stepId)) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                    "excel.to.inputform.check.stepid.required", new Object[]{sheetRowNumber}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // No right order set on cell or is not a number
                if (StringUtils.isBlank(this.get(posSubmissionStepOrder))) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                    "excel.to.inputform.check.step.order.required", new Object[]{sheetRowNumber}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                } else {
                    try {
                        Integer.parseInt(this.get(posSubmissionStepOrder));
                    } catch (NumberFormatException e) {
                        errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                        "excel.to.inputform.check.step.order.valid", new Object[]{sheetRowNumber}));
                        InputFormErrorBuilder.manageError(errors, errorMessage);
                    }
                }

                // Check page and form name break
                currDefinitionName = this.get(posSubmissionId);
                try {
                    currStepOrder = Integer.parseInt(this.get(posSubmissionStepOrder));
                    if (!currDefinitionName.equals(lastDefinitionName)) {
                        if (!(definitionNameList.contains(currDefinitionName))) {
                            definitionNameList.add(currDefinitionName);
                            if (currStepOrder != 1) {
                                errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                        "excel.to.inputform.check.step.order.check.page",
                                        new Object[]{sheetRowNumber}));
                                InputFormErrorBuilder.manageError(errors, errorMessage);
                            }
                        } else {
                            errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                    "excel.to.inputform.check.step.order.check",
                                    new Object[]{sheetRowNumber, currDefinitionName}));
                            InputFormErrorBuilder.manageError(errors, errorMessage);
                        }
                    } else {
                        if (currStepOrder != 1 && lastStepOrder != (currStepOrder - 1)) {
                            errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                    "excel.to.inputform.check.step.order.check.all",
                                    new Object[]{sheetRowNumber}));
                            InputFormErrorBuilder.manageError(errors, errorMessage);
                        }
                    }
                    lastStepOrder = currStepOrder;
                    lastDefinitionName = currDefinitionName;
                } catch (NumberFormatException e) {
                    // the errors are already in the output so just log
                    log.debug("Errore nel formato. ",e);
                }
            }

            // Check if submissions don't have duplicate metadata
            List<List<String>> sectionNames = new ArrayList<>();
            for (int i = 0; i < definitionNameList.size(); i++) {
                sectionNames.add(new ArrayList<>());
            }

            for (int i = 0; i < definitionNameList.size(); i++) {
                for (indexRow = 1; indexRow < rows; indexRow++) {
                    if (definitionNameList.get(i).equals(sheet.getRow(indexRow)[posSubmissionId].getContents())) {
                        sectionNames.get(i).add(sheet.getRow(indexRow)[posSubmissionStepId].getContents());
                    }
                }
            }

            List<Set<String>> metadataNamesGroupedBySubmission = new ArrayList<>();
            for (int i = 0; i < definitionNameList.size(); i++)  {
                metadataNamesGroupedBySubmission.add(new HashSet<String>());
            }

            Sheet inputFormSheet = workbook.getSheet(INPUTFORM_SHEET_NAME);
            int inputFormRows = inputFormSheet.getColumn(0).length;

            for (int i = 0; i < definitionNameList.size(); i++) {
                for (indexRow = 1; indexRow < inputFormRows; indexRow++) {
                    String[] content = inputFormSheet.getRow(indexRow)[posFormName].getContents().split("-");
                    if (sectionNames.get(i).contains(content[0])) {
                        String metadata = getMetadataByRowFromSheet(inputFormSheet, indexRow,
                                posDcSchema, posDcElement, posDcQualifier);
                        if (content.length > 1) {
                            continue;
                        }

                        if (metadataNamesGroupedBySubmission.get(i).contains(metadata)) {
                            errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                    "excel.to.inputform.metadata.checkduplicate",
                                    new Object[]{definitionNameList.get(i), metadata}));
                            InputFormErrorBuilder.manageError(errors, errorMessage);
                        } else {
                            metadataNamesGroupedBySubmission.get(i).add(metadata);
                        }
                    }
                }
            }

            List<String> stepsOnStepsDefinitionSheet = new ArrayList<>();
            Sheet stepsSheet = workbook.getSheet(STEPSDEFINITION_SHEET_NAME);

            if (stepsSheet.getRows() > 0) {
                int formRows = stepsSheet.getColumn(0).length;
                for (int i = 1; i < formRows; i++) {
                    String stepName = stepsSheet.getRow(i)[posStepId].getContents().trim();
                    if (!stepName.equals("") && !stepsOnStepsDefinitionSheet.contains(stepName)) {
                        stepsOnStepsDefinitionSheet.add(stepName);
                    }
                }
            }

            // Check if all form steps have a form definition
            for (int i = 0; i < stepIdList.size(); i++) {
                String stepIdEntry = stepIdList.get(i);
                stepIdEntry = stepIdEntry.trim();
                if (!stepsOnStepsDefinitionSheet.contains(stepIdEntry) && !stepIdEntry.equals("")) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.submission.step.not_valid",
                            new Object[]{stepIdEntry}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }
            }

            if (StringUtils.isNotBlank(defaultDefinition) && !definitionNameList.contains(defaultDefinition)) {
                errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                "excel.to.inputform.check.default.definition", new Object[]{defaultDefinition}));
                InputFormErrorBuilder.manageError(errors, errorMessage);
            }
        }
        return errors;
    }

    private String getMetadataByRowFromSheet(Sheet sheet, int rowIndex,
                                             int schemaIndex, int elementIndex, int qualifierIndex) {
        String metadata = sheet.getRow(rowIndex)[schemaIndex].getContents() + "_"
                + sheet.getRow(rowIndex)[elementIndex].getContents();
        String qualifierContent = sheet.getRow(rowIndex)[qualifierIndex].getContents();
        if (qualifierContent != null && !qualifierContent.isEmpty()) {
            return metadata + "_" + qualifierContent;
        }
        return metadata;
    }

}