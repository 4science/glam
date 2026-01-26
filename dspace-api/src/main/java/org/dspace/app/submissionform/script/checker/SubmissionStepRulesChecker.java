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
import java.util.Arrays;
import java.util.List;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.dspace.app.submissionform.script.builder.InputFormErrorBuilder;
import org.dspace.app.submissionform.script.dto.InputFormExcel;
import org.dspace.app.submissionform.script.util.I18nUtil;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to check the rules on the steps-definition sheet
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.it)
 */
public class SubmissionStepRulesChecker extends InputFormExcel implements ExcelSheetValidator {

    private static final Logger log = LoggerFactory.getLogger(SubmissionStepRulesChecker.class);

    // Step type constants
    private static final String STEP_TYPE_SUBMISSION_FORM = "submission-form";

    private List<String> stepTypeValues;

    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    @Override
    public List<InputFormErrorBuilder> check(File fileExcel, Context context, String defaultDefinition) {
        List<InputFormErrorBuilder> errors = new ArrayList<>();

        Workbook workbook = openWorkbook(fileExcel, errors);
        if (workbook == null) {
            return errors;
        }

        try {
            // Get steps-definition sheet
            Sheet sheet = workbook.getSheet(STEPSDEFINITION_SHEET_NAME);

            List<String> stepIdList = new ArrayList<>();
            List<String> formStepIdList = new ArrayList<>();

            validateSteps(sheet, stepIdList, formStepIdList, errors);

            List<String> formsOnFormsDefinitionSheet = collectFormNames(workbook);
            validateFormSteps(formStepIdList, formsOnFormsDefinitionSheet, errors);
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (Exception e) {
                    log.error("Error closing workbook", e);
                }
            }
        }
        log.info("Validation completed. Found {} error(s) in steps-definition sheet", errors.size());
        return errors;
    }

    private Workbook openWorkbook(File fileExcel, List<InputFormErrorBuilder> errors) {
        try {
            WorkbookSettings ws = new WorkbookSettings();
            ws.setEncoding(CHAR_ENCODING);
            ws.setSuppressWarnings(true);
            return Workbook.getWorkbook(fileExcel, ws);
        } catch (BiffException | IOException e) {
            log.error("Error reading Excel workbook from file:{}", fileExcel != null ? fileExcel.getName() : "null", e);
            InputFormErrorBuilder.manageError(errors, new StringBuilder(e.getMessage()));
            return null;
        }
    }

    private void validateSteps(Sheet sheet, List<String> stepIdList, List<String> formStepIdList,
                               List<InputFormErrorBuilder> errors) {
        if (sheet.getRows() == 0) {
            log.error("Sheet 'steps-definition' is empty (no rows)");
            addError(errors, "excel.to.inputform.check.sheet.empty", "steps-definition");
            return;
        }
        int rows = sheet.getColumn(0).length;

        String[] customStepTypes = configurationService.getArrayProperty("inputforms.custom.step-type", new String[0]);
        List<String> customStepTypesList = Arrays.asList(customStepTypes);

        for (int indexRow = 1; indexRow < rows; indexRow++) {
            int sheetRowNumber = indexRow + 1;
            this.sheetRow = sheet.getRow(indexRow);
            String stepId = this.get(posStepId);
            String stepType = this.get(posStepType);

            validateStepId(stepId, sheetRowNumber, errors);
            validateStepType(stepType, sheetRowNumber, customStepTypesList, errors);
            validateStepRequired(sheetRowNumber, errors);
            checkDuplicateStepId(stepId, stepIdList, sheetRowNumber, errors);

            if (STEP_TYPE_SUBMISSION_FORM.equals(stepType)) {
                formStepIdList.add(stepId);
            }
        }
    }

    private void validateStepId(String stepId, int sheetRowNumber, List<InputFormErrorBuilder> errors) {
        if (stepId.isEmpty()) {
            addError(errors, "excel.to.inputform.check.stepid.required", sheetRowNumber);
        }
    }

    private void validateStepType(String stepType, int sheetRowNumber, List<String> customStepTypesList,
                                  List<InputFormErrorBuilder> errors) {
        if (stepType.isEmpty()) {
            addError(errors, "excel.to.inputform.check.input_type.required", sheetRowNumber);
            return;
        }

        if (!isValidStepType(stepType, customStepTypesList)) {
            addError(errors, "excel.to.inputform.check.step.type.valid", sheetRowNumber);
        }
    }

    private boolean isValidStepType(String stepType, List<String> customStepTypesList) {
        if (stepTypeValues != null && stepTypeValues.contains(stepType)) {
            return true;
        }

        return customStepTypesList.contains(stepType);
    }

    private void validateStepRequired(int sheetRowNumber, List<InputFormErrorBuilder> errors) {
        String required = this.get(posStepRequired);
        if (required.isEmpty() || (!"true".equals(required) && !"false".equals(required))) {
            addError(errors, "excel.to.inputform.check.step.required.valid", sheetRowNumber);
        }
    }

    private void checkDuplicateStepId(String stepId, List<String> stepIdList, int sheetRowNumber,
                                       List<InputFormErrorBuilder> errors) {
        if (stepIdList.contains(stepId)) {
            addError(errors, "excel.to.inputform.check.stepid.checkduplicate", sheetRowNumber);
        } else {
            stepIdList.add(stepId);
        }
    }

    private List<String> collectFormNames(Workbook workbook) {
        List<String> formsOnFormsDefinitionSheet = new ArrayList<>();
        Sheet formSheet = workbook.getSheet(INPUTFORM_SHEET_NAME);

        if (formSheet.getRows() == 0) {
            return formsOnFormsDefinitionSheet;
        }

        int formRows = formSheet.getColumn(0).length;
        for (int i = 1; i < formRows; i++) {
            String formName = formSheet.getRow(i)[posFormName].getContents().trim();
            if (!formName.isEmpty() && !formsOnFormsDefinitionSheet.contains(formName)) {
                formsOnFormsDefinitionSheet.add(formName);
            }
        }
        return formsOnFormsDefinitionSheet;
    }

    private void validateFormSteps(List<String> formStepIdList, List<String> formsOnFormsDefinitionSheet,
                                   List<InputFormErrorBuilder> errors) {
        for (int i = 0; i < formStepIdList.size(); i++) {
            String stepIdEntry = formStepIdList.get(i).trim();
            if (!stepIdEntry.isEmpty() && !formsOnFormsDefinitionSheet.contains(stepIdEntry)) {
                log.warn("Step ID '{}' not found in input-form sheet", stepIdEntry);
                addError(errors, "excel.to.inputform.check.step.form.required", stepIdEntry);
            }
        }
    }

    /**
     * Helper method to add error to the errors list
     */
    private void addError(List<InputFormErrorBuilder> errors, String messageKey, Object... params) {
        StringBuilder errorMessage = new StringBuilder().append(I18nUtil.getMessage(messageKey, params));
        InputFormErrorBuilder.manageError(errors, errorMessage);
    }

    public void setStepTypeValues(List<String> stepTypeValues) {
        this.stepTypeValues = stepTypeValues;
    }

}