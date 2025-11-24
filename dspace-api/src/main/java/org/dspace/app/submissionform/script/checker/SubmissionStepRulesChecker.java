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
import org.dspace.services.factory.DSpaceServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to check the rules on the steps-definition sheet
 */
public class SubmissionStepRulesChecker extends InputFormExcel implements ExcelSheetValidator {

    private static final Logger log = LoggerFactory.getLogger(SubmissionStepRulesChecker.class);

    private List<String> stepTypeValues;

    @Override
    public List<InputFormErrorBuilder> check(File fileExcel, Context context, String defaultDefinition) {
        Workbook workbook = null;
        StringBuilder errorMessage;
        List<InputFormErrorBuilder> errors = new ArrayList<>();
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
            // Array containing all form step id
            List<String> formStepIdList = new ArrayList<>();
            //Input form row
            Sheet sheet = workbook.getSheet(STEPSDEFINITION_SHEET_NAME);

            //init:
            //all sheet rows (based on column 0)
            int rows = sheet.getColumn(0).length;
            //current list name and input type
            String stepId;
            String stepType;
            int indexRow;
            int sheetRowNumber;

            for (indexRow = 1; indexRow < rows; indexRow++) {
                sheetRowNumber = indexRow + 1;
                //current sheet row
                this.sheetRow = sheet.getRow(indexRow);
                stepId = this.get(posStepId);
                stepType = this.get(posStepType);

                // No right step id set on cell
                if (stepId.equals("")) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.stepid.required", new Object[]{sheetRowNumber}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // No step type set on cell
                if (stepType.equals("")) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.input_type.required", new Object[]{sheetRowNumber}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                } else {
                    // Retrieve possible custom step types
                    String[] customStepTypes = DSpaceServicesFactory.getInstance().getConfigurationService()
                            .getArrayProperty("inputforms.custom.step-type", new String[0]);

                    // No right step type set on cell
                    if (!stepTypeValues.contains(stepType) && !Arrays.asList(customStepTypes).contains(stepType)) {
                        errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                "excel.to.inputform.check.step.type.valid", new Object[] {sheetRowNumber}));
                        InputFormErrorBuilder.manageError(errors, errorMessage);
                    }
                }
                // No right repeat value set on cell
                if (this.get(posStepRequired).equals("") || (!this.get(posStepRequired).equals("true")
                        && !this.get(posStepRequired).equals("false"))) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.step.required.valid", new Object[]{sheetRowNumber}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                if (stepIdList.contains(stepId)) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.stepid.checkduplicate", new Object[]{sheetRowNumber}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                } else {
                    stepIdList.add(stepId);
                }

                if (stepType.equals("submission-form")) {
                    formStepIdList.add(stepId);
                }
            }

            List<String> formsOnFormsDefinitionSheet = new ArrayList<>();
            Sheet formSheet = workbook.getSheet(INPUTFORM_SHEET_NAME);

            if (formSheet.getRows() > 0) {
                int formRows = formSheet.getColumn(0).length;
                for (int i = 1; i < formRows; i++) {
                    String formName = formSheet.getRow(i)[posFormName].getContents().trim();
                    if (!formName.equals("") && !formsOnFormsDefinitionSheet.contains(formName)) {
                        formsOnFormsDefinitionSheet.add(formName);
                    }
                }
            }
            // Check if all form steps have a form definition
            for (int i = 1; i < formStepIdList.size(); i++) {
                String stepIdEntry = formStepIdList.get(i);
                stepIdEntry = stepIdEntry.trim();
                if (!formsOnFormsDefinitionSheet.contains(stepIdEntry) && !stepIdEntry.equals("")) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.step.form.required", new Object[]{ stepIdEntry }));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }
            }
        }
        return errors;
    }

    public void setStepTypeValues(List<String> stepTypeValues) {
        this.stepTypeValues = stepTypeValues;
    }

}