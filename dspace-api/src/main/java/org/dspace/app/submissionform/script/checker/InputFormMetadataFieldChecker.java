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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.submissionform.script.builder.InputFormErrorBuilder;
import org.dspace.app.submissionform.script.builder.MetadataRegistryFixBuilder;
import org.dspace.app.submissionform.script.dto.InputFormExcel;
import org.dspace.app.submissionform.script.util.I18nUtil;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.core.Context;
import org.dspace.importer.external.metadatamapping.MetadataFieldConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputFormMetadataFieldChecker extends InputFormExcel {

    private static final Logger log = LoggerFactory.getLogger(InputFormMetadataFieldChecker.class);

    private static final String I18N_LISTVALUES_CHECK_WARNING = "excel.to.inputform.check.listvalues";
    private static final String I18N_METADATA_CHECK_ERROR = "excel.to.inputform.checkvsdspace.metadata.check";

    public List<InputFormErrorBuilder> check(File fileExcel, Context context)
            throws SQLException, BiffException, IOException {
        List<InputFormErrorBuilder> errors = new ArrayList<>();

        StringBuilder errorMessage;
        Workbook workbook = null;

        try {
            // Set workbook
            WorkbookSettings ws = new WorkbookSettings();
            ws.setEncoding(CHAR_ENCODING);

            // Get workbook
            workbook = Workbook.getWorkbook(fileExcel, ws);
        } catch (BiffException e) {
            log.error(e.getMessage());
            InputFormErrorBuilder.manageError(errors, new StringBuilder(e.getMessage()));
        } catch (IOException e) {
            log.error(e.getMessage());
            InputFormErrorBuilder.manageError(errors, new StringBuilder(e.getMessage()));
        }

        if (workbook != null) {
            // input-form sheet
            Sheet sheet = workbook.getSheet(INPUTFORM_SHEET_NAME);

            // Retrieve element/qualifier from DB
            List<MetadataFieldConfig> elements = new ArrayList<MetadataFieldConfig>(1000);
            List<MetadataFieldConfig> addedElements = new ArrayList<MetadataFieldConfig>(1000);

            MetadataSchemaService schemaService = ContentServiceFactory.getInstance().getMetadataSchemaService();
            MetadataFieldService fieldService = ContentServiceFactory.getInstance().getMetadataFieldService();

            for (MetadataSchema schema : schemaService.findAll(context)) {
                for (MetadataField field : fieldService.findAllInSchema(context, schema)) {
                    elements.add(new MetadataFieldConfig(schema.getName(), field.getElement(), field.getQualifier()));
                }
            }
            // Cells under of posListName and posInputType column number
            Cell[] listNames = sheet.getColumn(posListName);
            Cell[] inputTypes = sheet.getColumn(posInputType);

            String listName;
            String inputType;
            int indexRiga;

            // all sheet rows (based on column 0)
            int rows = sheet.getColumn(0).length;

            for (indexRiga = 1; indexRiga < rows; indexRiga++) {
                // listname value
                if (indexRiga < listNames.length) {
                    listName = listNames[indexRiga].getContents().trim();
                } else {
                    break;
                }
                // input type value
                if (indexRiga < inputTypes.length) {
                    inputType = inputTypes[indexRiga].getContents().trim();
                } else {
                    break;
                }

                // current sheet row
                this.sheetRow = sheet.getRow(indexRiga);

                // label value
                String labelValue = this.get(posLabel);
                // form name value
                String formName = this.get(posFormName);

                // qualdrop
                if (this.get(posInputType).startsWith("qualdrop_")) {
                    // has a list?
                    if (StringUtils.isNotBlank(this.get(posListName))) {
                        // Check if stored value (only dc element) is present on db
                        List<String> storedValues = this.getQualdropValue(workbook, this.get(posListName));
                        if (storedValues != null) {
                            for (String storeValue : storedValues) {
                                if (!"_".equalsIgnoreCase(storeValue)) {
                                    MetadataFieldConfig element = new MetadataFieldConfig(this.get(posDcSchema),
                                                                                          this.get(posDcElement),
                                                                                          storeValue);

                                    if (!elements.contains(element) && !addedElements.contains(element)) {
                                        // ERROR
                                        addedElements.add(element);
                                        errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                                        I18N_METADATA_CHECK_ERROR, new Object[] {element.toString()}));
                                        MetadataRegistryFixBuilder fixRegistry =
                                                                                new MetadataRegistryFixBuilder(element);
                                        InputFormErrorBuilder.manageWarning(errors, errorMessage, fixRegistry);
                                    }
                                }
                            }

                            if (checkNullListValue(workbook, this.get(posListName))) {
                                MetadataFieldConfig element = new MetadataFieldConfig(this.get(posDcSchema),
                                                                                      this.get(posDcElement),
                                                                                      this.get(posListName));
                                errorMessage = new StringBuilder().append(I18nUtil.getMessage(
                                                     I18N_LISTVALUES_CHECK_WARNING, new Object[] {element.toString()}));
                                MetadataRegistryFixBuilder fixRegistry = new MetadataRegistryFixBuilder(element);
                                InputFormErrorBuilder.manageWarning(errors, errorMessage, fixRegistry);
                            }
                        }
                    }
                } else {
                    MetadataFieldConfig element = new MetadataFieldConfig(this.get(posDcSchema),
                                                                          this.get(posDcElement),
                                                                          this.get(posDcQualifier));
                    if (!elements.contains(element) && !addedElements.contains(element)) {
                        addedElements.add(element);
                        errorMessage = new StringBuilder().append(
                                I18nUtil.getMessage(I18N_METADATA_CHECK_ERROR, new Object[] { element.toString() }));
                        MetadataRegistryFixBuilder fixRegistry = new MetadataRegistryFixBuilder(element);
                        InputFormErrorBuilder.manageWarning(errors, errorMessage, fixRegistry);
                    }
                }
            }
        }

        for (InputFormErrorBuilder err : errors) {
            System.out.println("LEVEL:" + err.getLevel() + " ERROR:" + err.getErrorMsg());
        }
        return errors;
    }

    /**
     * Retrieve qualdrop stored value from second sheet based on listname match
     * (if exists)
     * 
     * @param workbook
     * @param listname
     * @return
     */
    private List<String> getQualdropValue(Workbook workbook, String listname) {
        List<String> qualifiers = new ArrayList<>();

        // Sheet 1,2,3... (value-pairs)
        int totalSheets = workbook.getNumberOfSheets();
        Sheet sheet = null;

        // Start from sheet 3
        for (int i = VALUEPAIRS_SHEET_NAME; i < totalSheets; i++) {
            sheet = workbook.getSheet(i);
            if (sheet.getRows() > 0) {
                Cell[] colonnaUserValue;
                Cell[] colonnaStoredValue;
                int indexColonna = 1;
                int numeroColonne = sheet.getRow(0).length;

                // Delta of columns expected base on extra lang
                int delta = getValuePairColumnsDelta();

                // for each column couple (user value, stored value)
                for (int j = 0; j < numeroColonne; j = j + delta) {
                    // column user value
                    colonnaUserValue = sheet.getColumn(j);
                    // column stored value (after language)
                    colonnaStoredValue = sheet.getColumn(j + (delta - 1));

                    if (listname.equals(colonnaUserValue[posFormName].getContents().trim())) {
                        // column found (listname match) -> for each row of
                        // column selected get stored value
                        while (indexColonna < colonnaUserValue.length) {
                            try {
                                String storedValue = "";
                                if (indexColonna < colonnaStoredValue.length) {
                                    storedValue = colonnaStoredValue[indexColonna].getContents().trim();
                                }
                                if (StringUtils.isNotBlank(colonnaUserValue[indexColonna].getContents())) {
                                    qualifiers.add(storedValue);
                                }
                                indexColonna++;
                            } catch (RuntimeException e) {
                                System.out.println("colonna(j): " + j + "  indexColonna(riga): " + indexColonna);
                                log.error(e.getMessage() + " colonna(j): " + j + " indexColonna(riga):" + indexColonna);
                                throw e;
                            }
                        }
                    }
                }
            }
        }
        return qualifiers;
    }

    private boolean checkNullListValue(Workbook workbook, String listname) {
        boolean ret = false;

        // Sheet 1,2,3.. (value-pairs)
        int totalSheets = workbook.getNumberOfSheets();
        Sheet sheet;

        // Start from sheet 1
        for (int i = 2; i < totalSheets; i++) {
            sheet = workbook.getSheet(i);
            if (sheet.getRows() > 0) {
                Cell[] colonnaUserValue;
                Cell[] colonnaStoredValue;
                int numeroColonne = sheet.getRow(0).length;

                // Delta of columns expected base on extra lang
                int delta = getValuePairColumnsDelta();

                // for each column couple (user value, stored value)
                for (int j = 0; j < numeroColonne; j = j + delta) {
                    colonnaUserValue = sheet.getColumn(j);
                    if (listname.equals(colonnaUserValue[posFormName].getContents().trim())) {
                        // column stored value (after language)
                        colonnaStoredValue = sheet.getColumn(j + (delta - 1));
                        if (colonnaUserValue.length > colonnaStoredValue.length) {
                            // Found exit
                            ret = true;
                            break;
                        }
                    }
                }
            }
            if (ret) {
                // Found exit
                break;
            }
        }
        return ret;
    }

}
