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
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.it)
 */
public class SubmissionDefinitionRulesChecker extends InputFormExcel implements ExcelSheetValidator {

    private static final Logger log = LoggerFactory.getLogger(SubmissionDefinitionRulesChecker.class);

    @Override
    public List<InputFormErrorBuilder> check(File fileExcel, Context context, String defaultDefinition) {
        Workbook workbook = null;
        List<InputFormErrorBuilder> errors = new ArrayList<>();
        try {
            // Set workbook
            WorkbookSettings ws = new WorkbookSettings();
            ws.setEncoding(CHAR_ENCODING);
            ws.setSuppressWarnings(true);
            // Get workbook
            workbook = Workbook.getWorkbook(fileExcel, ws);

            if (workbook == null) {
                addError(errors, "excel.to.inputform.check.workbook.invalid");
                return errors;
            }

            // Validate workbook has required sheets
            int numberOfSheets = workbook.getNumberOfSheets();
            if (numberOfSheets <= SUBMISSIONSDEFINITION_SHEET_NAME) {
                addError(errors, "excel.to.inputform.check.sheet.missing", "submissions-definition");
                return errors;
            }

            // Array containing all defined step id
            List<String> stepIdList = new ArrayList<>();
            // Form name list
            List<String> definitionNameList = new ArrayList<>();

            Sheet sheet = workbook.getSheet(SUBMISSIONSDEFINITION_SHEET_NAME);
            if (sheet == null) {
                log.error("Sheet 'submissions-definition' not found in workbook");
                addError(errors, "excel.to.inputform.check.sheet.missing", "submissions-definition");
                return errors;
            }
            // Validate sheet has rows
            if (sheet.getRows() == 0) {
                log.error("Sheet 'submissions-definition' is empty (no rows)");
                addError(errors, "excel.to.inputform.check.sheet.empty", "submissions-definition");
                return errors;
            }
            // all sheet rows (based on column 0)
            int rows = sheet.getColumn(0).length;

            validateStepsAndCollectData(sheet, rows, stepIdList, definitionNameList, errors);

            // Check if submissions don't have duplicate metadata
            checkDuplicateMetadata(workbook, definitionNameList, sheet, rows, errors);

            // Validate step definitions
            validateStepDefinitions(workbook, stepIdList, errors);

            // Validate default definition
            validateDefaultDefinition(defaultDefinition, definitionNameList, errors);
        } catch (BiffException | IOException e) {
            log.error("Error reading Excel workbook from file:{}", fileExcel != null ? fileExcel.getName() : "null", e);
            InputFormErrorBuilder.manageError(errors, new StringBuilder(e.getMessage()));
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (Exception e) {
                    log.warn("Error closing workbook", e);
                }
            }
        }
        return errors;
    }

    /**
     * Validates step IDs and orders, collects step IDs and definition names,
     * and checks step sequence
     */
    private void validateStepsAndCollectData(Sheet sheet, int rows, List<String> stepIdList,
                                             List<String> definitionNameList, List<InputFormErrorBuilder> errors) {
        String lastDefinitionName = "";
        int lastStepOrder = 1;

        for (int indexRow = 1; indexRow < rows; indexRow++) {
            int sheetRowNumber = indexRow + 1;
            this.sheetRow = sheet.getRow(indexRow);
            if (this.sheetRow == null || this.sheetRow.length == 0) {
                continue;
            }

            String stepId = this.get(posSubmissionStepId);
            if (!stepIdList.contains(stepId)) {
                stepIdList.add(stepId);
            }

            validateStepIdAndOrder(sheetRowNumber, stepId, errors);

            try {
                String currDefinitionName = this.get(posSubmissionId);
                int currStepOrder = Integer.parseInt(this.get(posSubmissionStepOrder));
                validateStepSequence(sheetRowNumber, currDefinitionName, currStepOrder, definitionNameList,
                                     lastDefinitionName, lastStepOrder, errors);
                lastStepOrder = currStepOrder;
                lastDefinitionName = currDefinitionName;
            } catch (NumberFormatException e) {
                log.error("Error in format", e);
            }
        }
    }

    /**
     * Validates step ID and order for a row
     */
    private void validateStepIdAndOrder(int sheetRowNumber, String stepId, List<InputFormErrorBuilder> errors) {
        // No right step id set on cell
        if (StringUtils.isBlank(stepId)) {
            addError(errors, "excel.to.inputform.check.stepid.required", sheetRowNumber);
        }

        // No right order set on cell or is not a number
        String stepOrder = this.get(posSubmissionStepOrder);
        if (StringUtils.isBlank(stepOrder)) {
            addError(errors, "excel.to.inputform.check.step.order.required", sheetRowNumber);
        } else {
            try {
                Integer.parseInt(stepOrder);
            } catch (NumberFormatException e) {
                addError(errors, "excel.to.inputform.check.step.order.valid", sheetRowNumber);
            }
        }
    }

    /**
     * Validates step sequence and collects definition names
     */
    private void validateStepSequence(int sheetRowNumber, String currDefinitionName, int currStepOrder,
                                     List<String> definitionNameList, String lastDefinitionName, int lastStepOrder,
                                     List<InputFormErrorBuilder> errors) {
        if (!currDefinitionName.equals(lastDefinitionName)) {
            if (!definitionNameList.contains(currDefinitionName)) {
                definitionNameList.add(currDefinitionName);
                if (currStepOrder != 1) {
                    addError(errors, "excel.to.inputform.check.step.order.check.page", sheetRowNumber);
                }
            } else {
                addError(errors, "excel.to.inputform.check.step.order.check", sheetRowNumber, currDefinitionName);
            }
        } else {
            if (currStepOrder != 1 && lastStepOrder != (currStepOrder - 1)) {
                addError(errors, "excel.to.inputform.check.step.order.check.all", sheetRowNumber);
            }
        }
    }

    /**
     * Checks for duplicate metadata in submissions
     */
    private void checkDuplicateMetadata(Workbook workbook, List<String> definitionNameList,
                                       Sheet sheet, int rows, List<InputFormErrorBuilder> errors) {
        List<List<String>> sectionNames = new ArrayList<>();
        for (int i = 0; i < definitionNameList.size(); i++) {
            sectionNames.add(new ArrayList<>());
        }

        for (int i = 0; i < definitionNameList.size(); i++) {
            for (int indexRow = 1; indexRow < rows; indexRow++) {
                if (definitionNameList.get(i).equals(sheet.getRow(indexRow)[posSubmissionId].getContents())) {
                    sectionNames.get(i).add(sheet.getRow(indexRow)[posSubmissionStepId].getContents());
                }
            }
        }

        List<Set<String>> metadataNamesGroupedBySubmission = new ArrayList<>();
        for (int i = 0; i < definitionNameList.size(); i++) {
            metadataNamesGroupedBySubmission.add(new HashSet<String>());
        }

        Sheet inputFormSheet = workbook.getSheet(INPUTFORM_SHEET_NAME);
        int inputFormRows = inputFormSheet.getColumn(0).length;

        for (int i = 0; i < definitionNameList.size(); i++) {
            for (int indexRow = 1; indexRow < inputFormRows; indexRow++) {
                String[] content = inputFormSheet.getRow(indexRow)[posFormName].getContents().split("-");
                if (content.length == 0) {
                    continue;
                }
                if (sectionNames.get(i).contains(content[0])) {
                    String metadata = getMetadataByRowFromSheet(inputFormSheet, indexRow,
                                                                posDcSchema, posDcElement, posDcQualifier);
                    if (content.length > 1) {
                        continue;
                    }

                    if (metadataNamesGroupedBySubmission.get(i).contains(metadata)) {
                        addError(errors, "excel.to.inputform.metadata.checkduplicate",
                                 definitionNameList.get(i), metadata);
                    } else {
                        metadataNamesGroupedBySubmission.get(i).add(metadata);
                    }
                }
            }
        }
    }

    /**
     * Validates that all form steps have a form definition
     */
    private void validateStepDefinitions(Workbook workbook, List<String> stepIdList,
                                         List<InputFormErrorBuilder> errors) {
        List<String> stepsOnStepsDefinitionSheet = new ArrayList<>();
        Sheet stepsSheet = workbook.getSheet(STEPSDEFINITION_SHEET_NAME);

        if (stepsSheet != null && stepsSheet.getRows() > 0) {
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
            String stepIdEntry = stepIdList.get(i).trim();
            if (!stepsOnStepsDefinitionSheet.contains(stepIdEntry) && !stepIdEntry.equals("")) {
                log.warn("Step ID '{}' not found in steps-definition sheet", stepIdEntry);
                addError(errors, "excel.to.inputform.check.submission.step.not_valid", stepIdEntry);
            }
        }
    }

    /**
     * Validates default definition
     */
    private void validateDefaultDefinition(String defaultDefinition, List<String> definitionNameList,
                                          List<InputFormErrorBuilder> errors) {
        if (StringUtils.isNotBlank(defaultDefinition) && !definitionNameList.contains(defaultDefinition)) {
            addError(errors, "excel.to.inputform.check.default.definition", defaultDefinition);
        }
    }

    /**
     * Helper method to add error to the errors list
     */
    private void addError(List<InputFormErrorBuilder> errors, String messageKey, Object... params) {
        StringBuilder errorMessage = new StringBuilder().append(I18nUtil.getMessage(messageKey, params));
        InputFormErrorBuilder.manageError(errors, errorMessage);
    }

    private String getMetadataByRowFromSheet(Sheet sheet, int rowIndex,
                                             int schemaIndex, int elementIndex, int qualifierIndex) {
        String metadata = sheet.getRow(rowIndex)[schemaIndex].getContents() + "_" +
                          sheet.getRow(rowIndex)[elementIndex].getContents();
        String qualifierContent = sheet.getRow(rowIndex)[qualifierIndex].getContents();
        if (qualifierContent != null && !qualifierContent.isEmpty()) {
            return metadata + "_" + qualifierContent;
        }
        return metadata;
    }

}