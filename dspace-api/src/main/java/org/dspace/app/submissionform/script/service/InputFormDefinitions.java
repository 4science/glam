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

public class InputFormDefinitions extends InputFormExcel {

    public void create(Element formDefinitions, File fileExcel, String locale) throws BiffException, IOException {
        String formName;
        String rowNumber;
        // DOM element for form, page, field and input type
        Element form;
        Element page;
        Element field;
        Element inputType;
        // Set encoding for workbook
        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding(CHAR_ENCODING);
        ws.setSuppressWarnings(true);
        Workbook workbook = Workbook.getWorkbook(fileExcel, ws);

        // Sheet input form
        Sheet sheet = workbook.getSheet(INPUTFORM_SHEET_NAME);
        // First input form name
        formName = sheet.getCell(0, 1).getContents();

        // Sheet rows
        int sheetRows = sheet.getColumn(0).length;
        int indexSheetRow = 1;

        // First input form information row (row=1)
        this.sheetRow = sheet.getRow(indexSheetRow);

        // For each row on sheet
        while (indexSheetRow < sheetRows) {
            // New form for current sheet
            form = new Element("form");
            formDefinitions.addContent(form);
            formName = get(posFormName);
            form.setAttribute("name", formName);

            // for each cell on form (base on cell[0] value)
            while (formName.equals(get(posFormName)) && indexSheetRow < sheetRows) {
                // New page for current form
                page = new Element("row");
                form.addContent(page);
                rowNumber = get(posRowNumber);

                // for each cell on same form and page (base on cell[0] and
                // cell[1] value)
                while (formName.equals(get(posFormName)) && rowNumber.equals(get(posRowNumber)) &&
                       indexSheetRow < sheetRows) {
                    String fieldStyle = get(posFieldStyle);
                    String schemaValue = get(posDcSchema);
                    String elementValue = get(posDcElement);
                    String qualifierValue = get(posDcQualifier);
                    String parentValue = get(posParent);
                    String labelValue = getMultiLangCellUnion(posLabel, 0, locale);
                    String requiredValue = getMultiLangCellUnion(posRequired, 1, locale);
                    String hint = getMultiLangCellUnion(posHint, 2, locale);
                    String listNameValue = get(posListName);
                    String typeBindValue = get(posTypeBind);

                    String restrictions = get(posVisibility);
                    String typeValue = get(posInputType);
                    String validation = get(posValidation);
                    String vocabulary = get(posVocabulary);
                    String closedvocabulary = get(posClosedVocabulary);
                    String multilanguage = i18nExtraLangs.length > 0 ? get(posMultilanguageValuePairs) : "";

                    // New element for current page on current form
                    field = new Element("field");
                    page.addContent(field);

                    field.addContent(new Element("dc-schema").setText(schemaValue));
                    field.addContent(new Element("dc-element").setText(elementValue));
                    if (StringUtils.isNotBlank(qualifierValue)) {
                        field.addContent(new Element("dc-qualifier").setText(qualifierValue));
                    }

                    field.addContent(new Element("label").setText(labelValue));

                    // New input type element
                    inputType = new Element("input-type");

                    if (typeValue.equals("dropdown")
                        || typeValue.equals("qualdrop_value")
                        || typeValue.equals("opendropdown")
                        || typeValue.equals("openlist")
                        || typeValue.equals("list")) {

                        if (StringUtils.isNotBlank(listNameValue)) {
                            inputType.setAttribute("value-pairs-name", listNameValue);
                        }
                    }

                    field.addContent(inputType.setText(typeValue));
                    field.addContent(new Element("repeatable").setText(get(posRepeatable)));
                    field.addContent(new Element("required").setText(requiredValue));

                    if (StringUtils.isNotBlank(fieldStyle)) {
                        field.addContent(new Element("style").setText(fieldStyle));
                    }

                    if (hint == null || hint.equals("")) {
                        hint = " ";
                    }

                    field.addContent(new Element("hint").setText(hint));

                    if (StringUtils.isNotBlank(typeBindValue)) {
                        field.addContent(new Element("type-bind").setText(typeBindValue));
                    }

                    // Scope
                    if (!restrictions.equals("")) {
                        Element restrictionEl;
                        if (StringUtils.containsIgnoreCase(restrictions, "readonly")) {
                            // New element
                            restrictionEl = new Element("readonly");
                            if (StringUtils.containsIgnoreCase(restrictions, DCInput.WORKFLOW_SCOPE)) {
                                restrictionEl.setText(DCInput.WORKFLOW_SCOPE);
                            } else if (StringUtils.containsIgnoreCase(restrictions, "submission")) {
                                restrictionEl.setText(DCInput.SUBMISSION_SCOPE);
                            } else {
                                restrictionEl.setText("all");
                            }
                            field.addContent(restrictionEl);
                        }

                        if (StringUtils.containsIgnoreCase(restrictions, "limited")) {
                            // New element
                            restrictionEl = new Element("visibility");
                            if (StringUtils.containsIgnoreCase(restrictions, DCInput.WORKFLOW_SCOPE)) {
                                restrictionEl.setText(DCInput.WORKFLOW_SCOPE);
                            } else if (StringUtils.containsIgnoreCase(restrictions, "submission")) {
                                restrictionEl.setText(DCInput.SUBMISSION_SCOPE);
                            } else {
                                restrictionEl.setText("all");
                            }
                            field.addContent(restrictionEl);
                        } else if (StringUtils.containsIgnoreCase(restrictions, "hidden")) {
                            // New element
                            restrictionEl = new Element("visibility");
                            if (StringUtils.containsIgnoreCase(restrictions, DCInput.WORKFLOW_SCOPE)) {
                                restrictionEl.setText(DCInput.SUBMISSION_SCOPE);
                            } else if (StringUtils.containsIgnoreCase(restrictions, "submission")) {
                                restrictionEl.setText(DCInput.WORKFLOW_SCOPE);
                            } else {
                                restrictionEl.setText("hidden");
                            }
                            field.addContent(restrictionEl);
                        }
                    }

                    if (StringUtils.isNotBlank(vocabulary)) {
                        Element elementVocabulary = new Element("vocabulary");
                        elementVocabulary.setText(vocabulary);
                        if (Boolean.parseBoolean(closedvocabulary)) {
                            elementVocabulary.setAttribute("closed", closedvocabulary);
                        }
                        field.addContent(elementVocabulary);
                    }

                    if (StringUtils.isNotBlank(validation)) {
                        field.addContent(new Element("regex").setText(validation));
                    }

                    if (StringUtils.isNotBlank(multilanguage)) {
                        Element elementMultilanguage = new Element("language");
                        elementMultilanguage.setText("true");
                        elementMultilanguage.setAttribute("value-pairs-name", multilanguage);
                        field.addContent(elementMultilanguage);
                    }

                    indexSheetRow++;

                    if (indexSheetRow < sheetRows) {
                        // get next sheet row
                        sheetRow = sheet.getRow(indexSheetRow);
                    }
                } // end while for form name and row number
            } // end while for form name
        } // end while for sheet
    }

}
