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
import java.util.List;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.submissionform.script.builder.InputFormErrorBuilder;
import org.dspace.app.submissionform.script.dto.InputFormExcel;
import org.dspace.app.submissionform.script.dto.InputFormFieldElement;
import org.dspace.app.submissionform.script.service.SubmissionFormGeneratorI18nService;
import org.dspace.app.submissionform.script.util.I18nUtil;
import org.dspace.app.util.RegexPatternUtils;
import org.dspace.content.authority.DSpaceControlledVocabulary;
import org.dspace.content.authority.ItemControlledVocabularyService;
import org.dspace.core.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to check rules on forms-definition excel sheet
 *
 * @author Mykhaylo Boychuk (4science.com)
 */
public class InputFormRulesChecker extends InputFormExcel implements ExcelSheetValidator {

    private static final Logger log = LoggerFactory.getLogger(InputFormRulesChecker.class);

    // Input type constants
    private static final String INPUT_TYPE_TAG = "tag";
    private static final String INPUT_TYPE_GROUP = "group";
    private static final String INPUT_TYPE_INLINE_GROUP = "inline-group";
    private static final String INPUT_TYPE_LIST = "list";
    private static final String INPUT_TYPE_DROPDOWN = "dropdown";
    private static final String INPUT_TYPE_OPENDROPDOWN = "opendropdown";
    private static final String INPUT_TYPE_OPENLIST = "openlist";
    private static final String INPUT_TYPE_QUALDROP_VALUE = "qualdrop_value";
    private static final String INPUT_TYPE_YEAR = "year";
    private static final String INPUT_TYPE_YEAR_NOINPRINT = "year_noinprint";
    private static final String INPUT_TYPE_LINK = "link";
    private static final String INPUT_TYPE_ONEBOX = "onebox";

    // Value constants
    private static final String VALUE_TRUE = "true";

    private List<String> inputTypeValues;
    private List<String> visibilityScopeValues;

    private SubmissionFormGeneratorI18nService i18nService;

    @Override
    public List<InputFormErrorBuilder> check(File fileExcel, Context context, String defaultDefinition) {
        log.info("Starting validation of input form Excel file: {}", fileExcel.getName());
        Workbook workbook = null;
        List<InputFormErrorBuilder> errors = new ArrayList<>();
        try {
            // Set workbook
            WorkbookSettings ws = new WorkbookSettings();
            ws.setEncoding(CHAR_ENCODING);
            ws.setSuppressWarnings(true);

            //Get workbook
            workbook = Workbook.getWorkbook(fileExcel, ws);
        } catch (BiffException e) {
            log.error("Failed to read Excel file '{}':file may be corrupted or in wrong format", fileExcel.getName(),e);
            addErrorFromException(errors, e);
        } catch (IOException e) {
            log.error("IO error reading Excel file '{}': {}", fileExcel.getName(), e.getMessage(), e);
            addErrorFromException(errors, e);
        }

        try {
            if (workbook != null) {
                // Array for list name match in value-pairs (no default value type)
                List<String> listNamesFromInputForm = new ArrayList<>();

                // list of dc element and dc qualifier in one form name (for duplicating check): no default value
                List<InputFormFieldElement> elements = new ArrayList<>();

                // Form name list
                List<String> formNames = new ArrayList<>();

                String lastFormName = "";
                String currFormName;

                int lastPageNumber = 1;
                int currPageNumber;

                // forms-definition sheet
                Sheet sheet = workbook.getSheet(INPUTFORM_SHEET_NAME);

                //list names and input types on sheet
                Cell[] inputTypes = sheet.getColumn(posInputType);
                Cell[] listNames = sheet.getColumn(posListName);

                // all sheet rows (based on column 0)
                int rows = sheet.getColumn(0).length;
                log.info("Processing {} rows from forms-definition sheet", rows - 1);

                // current list name and input type
                int indexRow;
                String listName;
                String inputType;

                ArrayList<Cell[]> nestedRows = new ArrayList<>();
                ArrayList<String> nestedFormNameList = new ArrayList<>();
                ArrayList<InputFormFieldElement> nestedElements = new ArrayList<>();

                ArrayList<Cell[]> groupFieldRows = new ArrayList<>();
                ArrayList<String> groupFieldList = new ArrayList<>();
                ArrayList<InputFormFieldElement> groupFieldElements = new ArrayList<>();

                for (indexRow = 1; indexRow < rows; indexRow++) {
                    // current list-name value
                    if (indexRow < listNames.length) {
                        listName = listNames[indexRow].getContents().trim();
                    } else {
                        listName = "";
                    }
                    // current input-type
                    if (indexRow < inputTypes.length) {
                        inputType = inputTypes[indexRow].getContents().trim();
                    } else {
                        break;
                    }
                    // current sheet row
                    this.sheetRow = sheet.getRow(indexRow);

                    // form element to add
                    InputFormFieldElement element = new InputFormFieldElement(this.get(posFormName),
                                                                              this.get(posDcSchema),
                                                                              this.get(posDcElement),
                                                                              this.get(posDcQualifier));

                    // Validate basic required fields
                    validateBasicFields(element, errors);
                    validateRowNumber(element, errors);
                    validateRepeatableRules(element, errors);
                    validatePattern(element, errors);
                    validateVisibilityAndRequired(element, errors);

                    // Validate input type specific rules
                    validateInputTypeSpecificRules(element, inputType, listName, errors);
                    validateDcFormat(element, inputType, errors);
                    validateVocabulary(element, inputType, errors);

                    // Set listNamesFromInputForm
                    if (StringUtils.isNotBlank(listName) && !listNamesFromInputForm.contains(listName)) {
                        listNamesFromInputForm.add(listName);
                    }

                    // Check for dc-element e dc-qualifier duplicate in same form
                    if (!elements.contains(element)) {
                        elements.add(element);
                    } else {
                        log.warn("Duplicate element found at row {}: {}", indexRow + 1, element);
                        addError(errors, "excel.to.inputform.check.form_name.checkduplicate",
                                 element.toString(), inputType);
                    }

                    if (isGroupType(inputType)) {
                        groupFieldRows.add(this.sheetRow);
                        groupFieldElements.add(element);
                        groupFieldList.add(element.getElementName().replace(".", "_"));
                    }

                    if (StringUtils.isNotBlank(this.get(posParent))) {
                        nestedRows.add(this.sheetRow);
                        nestedElements.add(element);
                        if (!nestedFormNameList.contains(this.get(posFormName))) {
                            nestedFormNameList.add(this.get(posFormName));
                        }
                    }

                    // Check page and form name break
                    currFormName = this.get(posFormName);
                    try {
                        currPageNumber = Integer.parseInt(this.get(posRowNumber));
                        validatePageNumbering(element, inputType, currFormName, currPageNumber, formNames, lastFormName,
                                              lastPageNumber, errors);
                        lastPageNumber = currPageNumber;
                        lastFormName = currFormName;
                    } catch (NumberFormatException e) {
                        // the errors are already in the output so just log
                        log.error("Error parsing row number format", e);
                    }
                }

                // Check wrong group fields definition
                validateGroupFields(groupFieldRows, groupFieldElements, nestedFormNameList, errors);

                // Check wrong nested fields definition
                validateNestedFields(nestedRows, nestedElements, groupFieldList, errors);

                // Check list name on value pair sheets
                validateValuePairs(workbook, listNamesFromInputForm, errors);
            } else {
                log.error("Cannot validate input form: workbook is null after file reading attempt");
            }
        } finally {
            if (workbook != null) {
                workbook.close();
                log.debug("Workbook closed successfully");
            }
        }
        log.info("Validation completed. Found {} error(s) in input form file: {}", errors.size(), fileExcel.getName());
        return errors;
    }

    public void setInputTypeValues(List<String> inputTypeValues) {
        this.inputTypeValues = inputTypeValues;
    }

    public void setVisibilityScopeValues(List<String> visibilityScopeValues) {
        this.visibilityScopeValues = visibilityScopeValues;
    }

    /**
     * Validate basic required fields for a form element
     */
    private void validateBasicFields(InputFormFieldElement element, List<InputFormErrorBuilder> errors) {
        if (StringUtils.isBlank(this.get(posFormName))) {
            addError(errors, "excel.to.inputform.check.formname.required", element.toString());
        }
        if (StringUtils.isBlank(this.get(posDcElement))) {
            addError(errors, "excel.to.inputform.check.dc_element.required", element.toString());
        }
        if (StringUtils.isBlank(this.get(posInputType))) {
            addError(errors, "excel.to.inputform.check.input_type.required", element.toString());
        }
        if (StringUtils.isBlank(this.get(posLabel))) {
            addError(errors, "excel.to.inputform.check.label.required", element.toString());
        }
        if (StringUtils.isBlank(this.get(posRepeatable))) {
            addError(errors, "excel.to.inputform.check.repeatable.valid", element.toString());
        }
    }

    /**
     * Validate row number is present and is a valid integer
     */
    private void validateRowNumber(InputFormFieldElement element, List<InputFormErrorBuilder> errors) {
        if (StringUtils.isBlank(this.get(posRowNumber))) {
            addError(errors, "excel.to.inputform.check.row_number.required", element.toString());
        } else {
            try {
                Integer.parseInt(this.get(posRowNumber));
            } catch (NumberFormatException e) {
                addError(errors, "excel.to.inputform.check.row_number.valid", element.toString());
            }
        }
    }

    /**
     * Validate repeatable field rules
     */
    private void validateRepeatableRules(InputFormFieldElement element, List<InputFormErrorBuilder> errors) {
        if (StringUtils.isNotBlank(this.get(posParent)) && isRepeatable() && !isInputType(INPUT_TYPE_LIST)) {
            addError(errors, "excel.to.inputform.check.repeatable.parent", element.toString());
        }
    }

    /**
     * Validate validation pattern if present
     */
    private void validatePattern(InputFormFieldElement element, List<InputFormErrorBuilder> errors) {
        String validationString = this.get(posValidation);
        if (StringUtils.isNotBlank(validationString)) {
            try {
                RegexPatternUtils.computePattern(validationString);
            } catch (Exception e) {
                addError(errors, "excel.to.inputform.check.validation.invalid", element.toString(), validationString);
            }
        }
    }

    /**
     * Validate visibility scope and required field rules
     */
    private void validateVisibilityAndRequired(InputFormFieldElement element, List<InputFormErrorBuilder> errors) {
        if (StringUtils.isNotBlank(this.get(posVisibility)) && isRequired()) {
            addError(errors, "excel.to.inputform.check.required.valid", element.toString());
        }
        if (visibilityScopeValues != null && !visibilityScopeValues.contains(this.get(posVisibility))) {
            addError(errors, "excel.to.inputform.check.scope.valid", element.toString());
        }
        if (inputTypeValues != null && !inputTypeValues.contains(this.get(posInputType))) {
            addError(errors, "excel.to.inputform.check.inputtype.valid", element.toString());
        }
    }

    /**
     * Validate input type specific rules
     */
    private void validateInputTypeSpecificRules(InputFormFieldElement element, String inputType, String listName,
                                                 List<InputFormErrorBuilder> errors) {
        // Tag must be repeatable
        if (inputType.equals(INPUT_TYPE_TAG) && !isRepeatable()) {
            addError(errors, "excel.to.inputform.check.repeatable.tag", element.toString());
        }

        // Group must be repeatable
        if ((inputType.equals(INPUT_TYPE_GROUP) || inputType.equals(INPUT_TYPE_INLINE_GROUP)) && !isRepeatable()) {
            addError(errors, "excel.to.inputform.check.repeatable.group", element.toString());
        }

        // Qualdrop_value must not have dc qualifier
        if (inputType.equals(INPUT_TYPE_QUALDROP_VALUE) && StringUtils.isNotBlank(this.get(posDcQualifier))) {
            addError(errors, "excel.to.inputform.check.posDcQualifier.qualdropvalue", element.toString());
        }

        // Input types that require list name
        if (requiresListName(inputType) && listName.equals("")) {
            addError(errors, "excel.to.inputform.check.listname", element.toString(), inputType);
        }

        // Input types that must not have list name
        if (!allowsListName(inputType) && !listName.equals("")) {
            addError(errors, "excel.to.inputform.check.not.listname", element.toString(), inputType);
        }
    }

    /**
     * Validate DC element and qualifier format (no underscore)
     */
    private void validateDcFormat(InputFormFieldElement element, String inputType, List<InputFormErrorBuilder> errors) {
        if (this.get(posDcElement).contains("_") || this.get(posDcQualifier).contains("_")) {
            addError(errors, "excel.to.inputform.check.format.dc", element.toString(), inputType);
        }
    }

    /**
     * Validate vocabulary if present
     */
    private void validateVocabulary(InputFormFieldElement element, String inputType,
                                    List<InputFormErrorBuilder> errors) {
        String vocabulary = this.get(posVocabulary);
        if (StringUtils.isNotBlank(vocabulary)) {
            if (inputType.equals(INPUT_TYPE_ONEBOX) || inputType.equals(INPUT_TYPE_TAG)) {
                if (!ItemControlledVocabularyService.isItemControlledVocabulary(vocabulary) &&
                    !DSpaceControlledVocabulary.isControlledVocabulary(vocabulary)) {
                    log.warn("Vocabulary '{}' not found for element {} with input type {}",
                            vocabulary, element.toString(), inputType);
                    addError(errors, "excel.to.inputform.check.vocabulary.notfound",
                            element.toString(), inputType, vocabulary);
                }
            } else {
                log.warn("Vocabulary '{}' is incompatible with input type '{}' for element {}",
                         vocabulary, inputType, element.toString());
                addError(errors, "excel.to.inputform.check.vocabulary.incompatible.type",
                         element.toString(), inputType, vocabulary);
            }
        }
    }

    /**
     * Validate page numbering and form name consistency
     */
    private void validatePageNumbering(InputFormFieldElement element, String inputType, String currFormName,
                                       int currPageNumber, List<String> formNames, String lastFormName,
                                       int lastPageNumber, List<InputFormErrorBuilder> errors) {
        if (!currFormName.equals(lastFormName)) {
            if (!formNames.contains(currFormName)) {
                formNames.add(currFormName);
                if (currPageNumber != 1) {
                    addError(errors, "excel.to.inputform.check.form_name.check.page", element.toString(), inputType);
                }
            } else {
                addError(errors, "excel.to.inputform.check.form_name.check", element.toString(), lastFormName);
            }
        } else {
            if (!(lastPageNumber == currPageNumber || lastPageNumber == currPageNumber - 1)) {
                addError(errors, "excel.to.inputform.check.form_name.check.all_rows", element.toString(), inputType);
            }
        }
    }

    /**
     * Validate group fields definition
     */
    private void validateGroupFields(List<Cell[]> groupFieldRows, List<InputFormFieldElement> groupFieldElements,
                                      List<String> nestedFormNameList, List<InputFormErrorBuilder> errors) {
        for (int i = 0; i < groupFieldRows.size(); i++) {
            InputFormFieldElement groupElement = groupFieldElements.get(i);
            String groupFormName = groupElement.getFormName() + "-" +
                                   groupElement.getElementName().replace(".", "-");
            if (!nestedFormNameList.contains(groupFormName)) {
                log.warn("Group field '{}' is defined but corresponding form '{}' is not found in nested form names",
                        groupElement, groupFormName);
                addError(errors, "excel.to.inputform.check.group.form_defined", groupElement.toString(), groupFormName);
            }
        }
    }

    /**
     * Validate nested fields definition
     */
    private void validateNestedFields(List<Cell[]> nestedRows, List<InputFormFieldElement> nestedElements,
                                      List<String> groupFieldList, List<InputFormErrorBuilder> errors) {
        for (int i = 0; i < nestedRows.size(); i++) {
            InputFormFieldElement nestedElement = nestedElements.get(i);
            String parentFieldName = nestedRows.get(i)[posParent].getContents().trim();
            if (!groupFieldList.contains(parentFieldName)) {
                log.warn("Nested field '{}' references parent '{}' which is not defined as a group field",
                         nestedElement.toString(), parentFieldName);
                addError(errors, "excel.to.inputform.check.nested.parent_defined",
                         nestedElement.toString(), parentFieldName);
            }
        }
    }

    /**
     * Validate value pairs sheets
     */
    private void validateValuePairs(Workbook workbook, List<String> listNamesFromInputForm,
                                    List<InputFormErrorBuilder> errors) {
        int totalSheets = workbook.getNumberOfSheets();
        List<String> listNameOnValuePairSheet = new ArrayList<>();

        // Build list of list names from value pair sheets
        for (int j = InputFormExcel.VALUEPAIRS_SHEET_NAME; j < totalSheets; j++) {
            Sheet sheet = workbook.getSheet(j);
            int delta = i18nService.getValuePairColumnsDelta();
            if (sheet.getRows() > 0) {
                this.sheetRow = sheet.getRow(0);
                for (int i = 0; i < this.sheetRow.length; i = i + delta) {
                    if (!this.sheetRow[i].getContents().equals("")) {
                        listNameOnValuePairSheet.add(this.sheetRow[i].getContents().trim());
                    }
                }
            }
        }

        // Check if all list names are defined in value pair sheets
        for (String listName : listNamesFromInputForm) {
            String trimmedListName = listName.trim();
            if (!listNameOnValuePairSheet.contains(trimmedListName) && !trimmedListName.equals("")) {
                log.warn("List name '{}' is used in forms-definition but not found in value pair sheets",
                         trimmedListName);
                addError(errors, "excel.to.inputform.check.formvaluepairs", trimmedListName);
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

    /**
     * Helper method to add error from exception
     */
    private void addErrorFromException(List<InputFormErrorBuilder> errors, Exception e) {
        InputFormErrorBuilder.manageError(errors, new StringBuilder(e.getMessage()));
    }

    /**
     * Helper method to check if field is repeatable
     */
    private boolean isRepeatable() {
        return VALUE_TRUE.equals(this.get(posRepeatable));
    }

    /**
     * Helper method to check if field is required
     */
    private boolean isRequired() {
        return VALUE_TRUE.equals(this.get(posRequired));
    }

    /**
     * Helper method to check input type
     */
    private boolean isInputType(String inputType) {
        return inputType.equals(this.get(posInputType));
    }

    /**
     * Helper method to check if input type is a group type
     */
    private boolean isGroupType(String inputType) {
        return inputType.equals(INPUT_TYPE_GROUP) || inputType.equals(INPUT_TYPE_INLINE_GROUP);
    }

    /**
     * Helper method to check if input type requires list name
     */
    private boolean requiresListName(String inputType) {
        return inputType.equals(INPUT_TYPE_DROPDOWN)
                || inputType.equals(INPUT_TYPE_QUALDROP_VALUE)
                || inputType.equals(INPUT_TYPE_OPENDROPDOWN)
                || inputType.equals(INPUT_TYPE_OPENLIST)
                || inputType.equals(INPUT_TYPE_LIST);
    }

    /**
     * Helper method to check if input type allows list name
     */
    private boolean allowsListName(String inputType) {
        return inputType.equals(INPUT_TYPE_DROPDOWN)
                || inputType.equals(INPUT_TYPE_OPENDROPDOWN)
                || inputType.equals(INPUT_TYPE_QUALDROP_VALUE)
                || inputType.equals(INPUT_TYPE_YEAR)
                || inputType.equals(INPUT_TYPE_YEAR_NOINPRINT)
                || inputType.equals(INPUT_TYPE_OPENLIST)
                || inputType.equals(INPUT_TYPE_LIST)
                || inputType.equals(INPUT_TYPE_LINK);
    }

    public void setI18nService(SubmissionFormGeneratorI18nService i18nService) {
        this.i18nService = i18nService;
    }

}