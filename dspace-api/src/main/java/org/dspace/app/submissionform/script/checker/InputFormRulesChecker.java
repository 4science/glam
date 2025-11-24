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
import java.util.Iterator;
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
import org.dspace.app.submissionform.script.util.I18nUtil;
import org.dspace.app.util.RegexPatternUtils;
import org.dspace.content.authority.ItemControlledVocabularyService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to check rules on input form excel file
 *
 * @author Mykhaylo Boychuk (4science.com)
 */
public class InputFormRulesChecker extends InputFormExcel implements ExcelSheetValidator {

    private static final Logger log = LoggerFactory.getLogger(InputFormRulesChecker.class);

    private List<String> inputTypeValues;
    private List<String> visibilityScopeValues;

    @Override
    public List<InputFormErrorBuilder> check(File fileExcel, Context context, String defaultDefinition) {
        List<InputFormErrorBuilder> errors = new ArrayList<>();

        StringBuilder errorMessage;
        Workbook workbook = null;

        try {
            // Set workbook
            WorkbookSettings ws = new WorkbookSettings();
            ws.setEncoding(CHAR_ENCODING);
          //Get workbook
            workbook = Workbook.getWorkbook(fileExcel, ws);
        } catch (BiffException e) {
            log.error(e.getMessage());
            InputFormErrorBuilder.manageError(errors, new StringBuilder(e.getMessage()));
        } catch (IOException e) {
            log.error(e.getMessage());
            InputFormErrorBuilder.manageError(errors, new StringBuilder(e.getMessage()));
        }

        if (workbook != null) {
            // Array for list name match in value-pairs (no default value type)
            List<String> listNamesD = new ArrayList<>();

            // list of dc element and dc qualifier in one form name (for duplicating check): no default value
            List<InputFormFieldElement> elements = new ArrayList<>();

            // list of dc element and dc qualifier in one form name (for duplicating check) for default value
            List<InputFormFieldElement> duplicatedElements = new ArrayList<>();

            // Form name list
            List<String> formNames = new ArrayList<>();

            String lastFormName = "";
            String currFormName;

            int lastPageNumber = 1;
            int currPageNumber;

            //Input form row
            Sheet sheet = workbook.getSheet(INPUTFORM_SHEET_NAME);

            //init:
            //list names and input types on sheet
            Cell[] listNames = sheet.getColumn(posListName);
            Cell[] inputTypes = sheet.getColumn(posInputType);

            //all sheet rows (based on column 0)
            int rows = sheet.getColumn(0).length;

            //current list name and input type
            String listName;
            String inputType;
            int indexRow;

            ArrayList<Cell[]> nestedRows = new ArrayList<>();
            ArrayList<InputFormFieldElement> nestedEls = new ArrayList<>();
            ArrayList<String> nestedFormNameList = new ArrayList<>();

            ArrayList<Cell[]> groupFieldRows = new ArrayList<>();
            ArrayList<InputFormFieldElement> groupFieldEls = new ArrayList<>();
            ArrayList<String> groupFieldList = new ArrayList<>();

            for (indexRow = 1; indexRow < rows; indexRow++) {

                // current list name value
                if (indexRow < listNames.length) {
                    listName = listNames[indexRow].getContents().trim();
                } else {
                    listName = "";
                }
                // current input type
                if (indexRow < inputTypes.length) {
                    inputType = inputTypes[indexRow].getContents().trim();
                } else {
                    break;
                }
                //current sheet row
                this.sheetRow = sheet.getRow(indexRow);

                //current label
                String labelValue = this.get(posLabel);

                // form element to add
                InputFormFieldElement element = new InputFormFieldElement(this.get(posFormName),
                                                                          this.get(posDcSchema),
                                                                          this.get(posDcElement),
                                                                          this.get(posDcQualifier));
                // No right form name set on cell
                if (StringUtils.isBlank(this.get(posFormName))) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.formname.required",
                             new Object[]{element.toString()}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // No right page number set on cell or is not a number
                if (StringUtils.isBlank(this.get(posRowNumber))) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.row_number.required",
                            new Object[]{element.toString()}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                } else {
                    try {
                        Integer.parseInt(this.get(posRowNumber));
                    } catch (NumberFormatException e) {
                        errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                "excel.to.inputform.check.row_number.valid",
                                new Object[]{element.toString()}));
                        InputFormErrorBuilder.manageError(errors, errorMessage);
                    }
                }

                // No right repeat value set on cell
                if (StringUtils.isNotBlank(this.get(posParent)) && this.get(posRepeatable).equals("true")
                    && !this.get(posInputType).equals("list")) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.repeatable.parent",
                            new Object[]{element.toString()}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // No right dc element set on cell
                if (StringUtils.isBlank(this.get(posDcElement))) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.dc_element.required",
                            new Object[]{element.toString()}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // No right input type set on cell
                if (StringUtils.isBlank(this.get(posInputType))) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.input_type.required",
                            new Object[]{element.toString()}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }


                String validationString = this.get(posValidation);
                if (StringUtils.isNotBlank(validationString)) {
                    try {
                        RegexPatternUtils.computePattern(validationString);
                    } catch (Exception e) {
                        errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                "excel.to.inputform.check.validation.invalid",
                                new Object[]{element.toString(), validationString}));
                        InputFormErrorBuilder.manageError(errors, errorMessage);
                    }
                }

                // No right label value set on cell
                if (StringUtils.isBlank(labelValue)) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.label.required",
                            new Object[]{element.toString()}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // No right repeat value set on cell
                if (StringUtils.isBlank(this.get(posRepeatable))) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.repeatable.valid",
                            new Object[]{element.toString()}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // No right scope and required set on cell
                if (StringUtils.isNotBlank(this.get(posVisibility)) && this.get(posRequired).equals("true")) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.required.valid",
                            new Object[]{element.toString()}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // No right scope set on cell
                if (!visibilityScopeValues.contains(this.get(posVisibility))) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.scope.valid",
                            new Object[] { element.toString() }));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // No right input type set on cell
                if (!inputTypeValues.contains(this.get(posInputType))) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.inputtype.valid",
                            new Object[] { element.toString() }));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // if tag must be repeatable
                if (inputType.equals("tag") && !this.get(posRepeatable).equals("true")) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.repeatable.tag",
                            new Object[]{element.toString()}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // if group must be repeatable
                if ((inputType.equals("group") || inputType.equals("inline-group"))
                        && !this.get(posRepeatable).equals("true")) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.repeatable.group",
                            new Object[]{element.toString()}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // if qualdrop_value no dc qualifier must be set
                if (inputType.equals("qualdrop_value") && StringUtils.isNotBlank(this.get(posDcQualifier))) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.posDcQualifier.qualdropvalue",
                            new Object[]{element.toString()}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // This input type must be a list name
                if ((inputType.equals("dropdown")
                        || inputType.equals("qualdrop_value")
                        || inputType.equals("opendropdown")
                        || inputType.equals("openlist")
                        || inputType.equals("list"))
                        && listName.equals("")) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.listname",
                            new Object[]{element.toString(), inputType}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // For this input type, list name must be empty
                if (!(inputType.equals("dropdown")
                        || inputType.equals("opendropdown")
                        || inputType.equals("qualdrop_value")
                        || inputType.equals("year")
                        || inputType.equals("year_noinprint")
                        || inputType.equals("openlist")
                        || inputType.equals("list")
                        || inputType.equals("link"))
                        && !listName.equals("")) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.not.listname",
                            new Object[]{element.toString(), inputType}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // No char _ in element or qualifier
                if (this.get(posDcElement).indexOf("_") != -1 || this.get(posDcQualifier).indexOf("_") != -1) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.format.dc",
                            new Object[]{element.toString(), inputType}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                // Set listNamesD
                if (!listNamesD.contains(listName)) {
                    listNamesD.add(listName);
                }

                // Check for dc-element e dc-qualifier duplicate in same form
                if (!elements.contains(element)) {
                    elements.add(element);
                } else {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.form_name.checkduplicate",
                            new Object[]{element.toString(), inputType}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }

                if (inputType.equals("group") || inputType.equals("inline-group")) {
                    groupFieldRows.add(this.sheetRow);
                    groupFieldEls.add(element);
                    groupFieldList.add(element.getElementName().replace(".", "_"));
                }

                if (StringUtils.isNotBlank(this.get(posParent))) {
                    nestedRows.add(this.sheetRow);
                    nestedEls.add(element);
                    if (!nestedFormNameList.contains(this.get(posFormName))) {
                        nestedFormNameList.add(this.get(posFormName));
                    }
                }

                String vocabulary = this.get(posVocabulary);
                if (StringUtils.isNotBlank(vocabulary)) {
                    if (inputType.equals("onebox") || inputType.equals("tag")) {
                        // check if file exist
                        File file = getVocabularyFile(vocabulary);
                        if (!isItemControlledVocabulary(vocabulary) && !file.exists()) {
                            // errors you should define terms of vocabulary,
                            // file xml not found
                            errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                            "excel.to.inputform.check.vocabulary.notfound",
                                            new Object[] { element.toString(), inputType, file.getAbsolutePath() }));
                            InputFormErrorBuilder.manageError(errors, errorMessage);
                        }
                    } else {
                        errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                                "excel.to.inputform.check.vocabulary.incompatible.type",
                                                new Object[] { element.toString(), inputType, vocabulary }));
                        InputFormErrorBuilder.manageError(errors, errorMessage);
                    }
                }


                // Check page and form name break
                currFormName = this.get(posFormName);
                try {
                    currPageNumber = Integer.parseInt(this.get(posRowNumber));
                    if (!currFormName.equals(lastFormName)) {
                        if (!formNames.contains(currFormName)) {
                            formNames.add(currFormName);
                            if (currPageNumber != 1) {
                                errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                        "excel.to.inputform.check.form_name.check.page",
                                        new Object[]{element.toString(), inputType}));
                                InputFormErrorBuilder.manageError(errors, errorMessage);
                            }
                        } else {
                            errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                    "excel.to.inputform.check.form_name.check",
                                    new Object[]{element.toString(), lastFormName}));
                            InputFormErrorBuilder.manageError(errors, errorMessage);
                        }
                    } else {
                        if (!(lastPageNumber == currPageNumber || lastPageNumber == currPageNumber - 1)) {
                            errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                    "excel.to.inputform.check.form_name.check.all_rows",
                                    new Object[]{element.toString(), inputType}));
                            InputFormErrorBuilder.manageError(errors, errorMessage);
                        }
                    }

                    lastPageNumber = currPageNumber;
                    lastFormName = currFormName;
                } catch (NumberFormatException e) {
                    // the errors are already in the output so just log
                    log.debug("Errore nel formato. ",e);
                }
            }

            // Check wrong group fields definition
            for (int i = 0; i < groupFieldRows.size(); i++) {
                InputFormFieldElement groupElement = groupFieldEls.get(i);
                String groupFormName = groupElement.getFormName() + "-" +
                                       groupElement.getElementName().replace(".", "-");
                if (!nestedFormNameList.contains(groupFormName)) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.group.form_defined",
                            new Object[]{groupElement.toString(), groupFormName }));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }
            }

            // Check wrong nested fields definition
            for (int i = 0; i < nestedRows.size(); i++) {
                InputFormFieldElement nestedElement = nestedEls.get(i);
                String parentFieldName = nestedRows.get(i)[posParent].getContents().trim();
                if (!groupFieldList.contains(parentFieldName)) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.nested.parent_defined",
                            new Object[]{nestedElement.toString(), parentFieldName }));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }
            }

            // Check list name on value pair
            // Sheet #0 -> submissiondefinition
            // Sheet #1 -> steps-definition
            // Sheet #2 -> forms-definition
            // Sheet #3... value pair
            int totalSheets = workbook.getNumberOfSheets();
            List listNameOnValuePairSheet = new ArrayList();

            // Start from sheet number 3 -> build listNameOnValuePairSheet
            for (int j = InputFormExcel.VALUEPAIRS_SHEET_NAME; j < totalSheets; j++) {
                sheet = workbook.getSheet(j);
                // Delta of columns expected base on extra lang
                int delta = getValuePairColumnsDelta();
                if (sheet.getRows() > 0) {
                    //For each column 0,delta... etc get list name value
                    this.sheetRow = sheet.getRow(0);
                    for (int i = 0; i < this.sheetRow.length; i = i + delta) {
                        if (!this.sheetRow[i].getContents().equals("")) {
                            listNameOnValuePairSheet.add(this.sheetRow[i].getContents().trim());
                        }
                    }
                }
            }

            //Check if all list name on sheet 0 is set on sheet 1 or 2 ....
            Iterator iterListNamesD = listNamesD.iterator();
            while (iterListNamesD.hasNext()) {
                String listaS = (String) iterListNamesD.next();
                listaS = listaS.trim();
                if (!listNameOnValuePairSheet.contains(listaS) && !listaS.equals("")) {
                    errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                            "excel.to.inputform.check.formvaluepairs", new Object[]{listaS}));
                    InputFormErrorBuilder.manageError(errors, errorMessage);
                }
            }
        }
        return errors;
    }

    private boolean isItemControlledVocabulary(String vocabulary) {
        return List.of(ItemControlledVocabularyService.getPluginNames()).contains(vocabulary);
    }

    private File getVocabularyFile(String vocabulary) {
        ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        var dspaceDir = configurationService.getProperty("dspace.dir");
        var path2Vocabulary = File.separator + "config" + File.separator + "controlled-vocabularies" + File.separator;
        return new File(dspaceDir + path2Vocabulary + vocabulary + ".xml");
    }

    public void setInputTypeValues(List<String> inputTypeValues) {
        this.inputTypeValues = inputTypeValues;
    }

    public void setVisibilityScopeValues(List<String> visibilityScopeValues) {
        this.visibilityScopeValues = visibilityScopeValues;
    }

}