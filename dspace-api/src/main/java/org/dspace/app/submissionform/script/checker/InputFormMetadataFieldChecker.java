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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.submissionform.script.builder.InputFormErrorBuilder;
import org.dspace.app.submissionform.script.builder.MetadataRegistryFixBuilder;
import org.dspace.app.submissionform.script.dto.InputFormExcel;
import org.dspace.app.submissionform.script.service.SubmissionFormGeneratorI18nService;
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

/**
 * Class to check if all metadata field used in form-definition are present in metadata registry.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class InputFormMetadataFieldChecker extends InputFormExcel implements ExcelSheetValidator {

    private static final Logger log = LoggerFactory.getLogger(InputFormMetadataFieldChecker.class);

    private static final int INITIAL_METADATA_FIELDS_CAPACITY = 1000;
    private static final int VALUE_PAIRS_SHEETS_START_INDEX = 2;

    private static final String QUALDROP_PLACEHOLDER_VALUE = "_";
    private static final String I18N_LISTVALUES_CHECK_WARNING = "excel.to.inputform.check.listvalues";
    private static final String I18N_METADATA_CHECK_ERROR = "excel.to.inputform.checkvsdspace.metadata.check";

    private SubmissionFormGeneratorI18nService i18nService;

    @Override
    public List<InputFormErrorBuilder> check(File fileExcel, Context context, String defaultDefinition)
            throws SQLException, BiffException, IOException {

        List<InputFormErrorBuilder> errors = new ArrayList<>();
        Workbook workbook = openWorkbook(fileExcel, errors);
        if (workbook == null) {
            return errors;
        }
        try {
            Sheet sheet = workbook.getSheet(INPUTFORM_SHEET_NAME);
            Set<MetadataFieldConfig> elements = new HashSet<>(INITIAL_METADATA_FIELDS_CAPACITY);
            Set<MetadataFieldConfig> addedElements = new HashSet<>(INITIAL_METADATA_FIELDS_CAPACITY);

            loadMetadataFromDatabase(context, elements);
            validateFormRows(sheet, workbook, elements, addedElements, errors);
        } finally {
            workbook.close();
        }
        return errors;
    }

    private Workbook openWorkbook(File fileExcel, List<InputFormErrorBuilder> errors) {
        try {
            WorkbookSettings ws = new WorkbookSettings();
            ws.setEncoding(CHAR_ENCODING);
            ws.setSuppressWarnings(true);
            return Workbook.getWorkbook(fileExcel, ws);
        } catch (BiffException e) {
            log.error(e.getMessage());
            InputFormErrorBuilder.manageError(errors, new StringBuilder(e.getMessage()));
        } catch (IOException e) {
            log.error(e.getMessage());
            InputFormErrorBuilder.manageError(errors, new StringBuilder(e.getMessage()));
        }
        return null;
    }

    private void validateFormRows(Sheet sheet,
                                  Workbook workbook,
                                  Set<MetadataFieldConfig> elements,
                                  Set<MetadataFieldConfig> addedElements,
                                  List<InputFormErrorBuilder> errors) {

        int rows = sheet.getColumn(0).length;
        Cell[] listNames = sheet.getColumn(posListName);
        Cell[] inputTypes = sheet.getColumn(posInputType);

        for (int rowIndex = 1; rowIndex < rows; rowIndex++) {
            if (rowIndex >= listNames.length || rowIndex >= inputTypes.length) {
                break;
            }

            this.sheetRow = sheet.getRow(rowIndex);
            String inputType = this.get(posInputType);

            if (inputType.startsWith("qualdrop_")) {
                validateQualdropField(workbook, elements, addedElements, errors);
            } else {
                validateRegularField(elements, addedElements, errors);
            }
        }
    }

    /**
     * Validates a qualdrop field by checking if all stored values from the value pairs
     * sheets exist in the metadata registry.
     * Also checks for null values in the list and adds warnings if found.
     *
     * @param workbook      the Excel workbook containing the value pairs sheets
     * @param elements      the set of metadata fields from the database registry
     * @param addedElements the set of elements already added to errors to avoid duplicates
     * @param errors        the list of errors to populate with validation issues
     */
    private void validateQualdropField(Workbook workbook,
                                       Set<MetadataFieldConfig> elements,
                                       Set<MetadataFieldConfig> addedElements,
                                       List<InputFormErrorBuilder> errors) {

        String listName = this.get(posListName);
        if (StringUtils.isBlank(listName)) {
            return;
        }

        List<String> storedValues = this.getQualdropValue(workbook, listName);
        if (storedValues == null) {
            return;
        }

        for (String storeValue : storedValues) {
            if (!QUALDROP_PLACEHOLDER_VALUE.equalsIgnoreCase(storeValue)) {
                MetadataFieldConfig element = getMetadataField(this.get(posDcSchema),
                                                               this.get(posDcElement),
                                                               storeValue);
                if (!elements.contains(element) && !addedElements.contains(element)) {
                    addedElements.add(element);
                    addMetadataError(element, errors);
                }
            }
        }

        if (checkNullListValue(workbook, listName)) {
            MetadataFieldConfig element = getMetadataField(this.get(posDcSchema), this.get(posDcElement), listName);
            addListValueWarning(element, errors);
        }
    }

    private void validateRegularField(Set<MetadataFieldConfig> elements,
                                      Set<MetadataFieldConfig> addedElements,
                                      List<InputFormErrorBuilder> errors) {

        MetadataFieldConfig element = getMetadataField(this.get(posDcSchema),
                                                       this.get(posDcElement),
                                                       this.get(posDcQualifier));
        if (!elements.contains(element) && !addedElements.contains(element)) {
            addedElements.add(element);
            addMetadataError(element, errors);
        }
    }

    private void addMetadataError(MetadataFieldConfig element, List<InputFormErrorBuilder> errors) {
        String message = I18nUtil.getMessage(I18N_METADATA_CHECK_ERROR, new Object[] {element.getField()});
        InputFormErrorBuilder.manageWarning(errors,new StringBuilder(message), new MetadataRegistryFixBuilder(element));
    }

    private void addListValueWarning(MetadataFieldConfig element, List<InputFormErrorBuilder> errors) {
        String message = I18nUtil.getMessage(I18N_LISTVALUES_CHECK_WARNING, new Object[] {element.toString()});
        MetadataRegistryFixBuilder fixRegistry = new MetadataRegistryFixBuilder(element);
        InputFormErrorBuilder.manageWarning(errors, new StringBuilder(message), fixRegistry);
    }

    private void loadMetadataFromDatabase(Context context, Set<MetadataFieldConfig> elements) throws SQLException {
        MetadataSchemaService schemaService = ContentServiceFactory.getInstance().getMetadataSchemaService();
        MetadataFieldService fieldService = ContentServiceFactory.getInstance().getMetadataFieldService();
        for (MetadataSchema schema : schemaService.findAll(context)) {
            for (MetadataField field : fieldService.findAllInSchema(context, schema)) {
                elements.add(new MetadataFieldConfig(schema.getName(), field.getElement(), field.getQualifier()));
            }
        }
    }

    /**
     * Checks if there are null values in a list by comparing the user value column
     * with the stored value column. A null value is detected when the user value
     * column has more rows than the stored value column.
     *
     * @param workbook the Excel workbook containing the value pairs sheets
     * @param listname the name of the list to check for null values
     * @return         true if null values are found in the list, false otherwise
     */
    private boolean checkNullListValue(Workbook workbook, String listname) {
        boolean ret = false;

        int totalSheets = workbook.getNumberOfSheets();
        Sheet sheet;

        for (int i = VALUE_PAIRS_SHEETS_START_INDEX; i < totalSheets; i++) {
            sheet = workbook.getSheet(i);
            if (sheet.getRows() > 0) {
                Cell[] userValueColumn;
                Cell[] storedValueColumn;
                int numberOfColumns = sheet.getRow(0).length;

                int delta = i18nService.getValuePairColumnsDelta();

                for (int j = 0; j < numberOfColumns; j = j + delta) {
                    userValueColumn = sheet.getColumn(j);
                    if (listname.equals(userValueColumn[posFormName].getContents().trim())) {
                        storedValueColumn = sheet.getColumn(j + (delta - 1));
                        if (userValueColumn.length > storedValueColumn.length) {
                            ret = true;
                            break;
                        }
                    }
                }
            }
            if (ret) {
                break;
            }
        }
        return ret;
    }

    /**
     * Retrieves the stored values for a qualdrop field from the value pairs sheets.
     * Searches through all value pairs sheets starting from VALUEPAIRS_SHEET_NAME
     * to find the column matching the given list name, then extracts all stored values from that column.
     *
     * @param workbook the Excel workbook containing the value pairs sheets
     * @param listName the name of the list to search for in the value pairs sheets
     * @return         a list of stored values (qualifiers) for the matching list name
     */
    private List<String> getQualdropValue(Workbook workbook, String listName) {
        List<String> qualifiers = new ArrayList<>();

        Sheet sheet;
        int totalSheets = workbook.getNumberOfSheets();

        for (int i = VALUEPAIRS_SHEET_NAME; i < totalSheets; i++) {
            sheet = workbook.getSheet(i);
            if (sheet.getRows() > 0) {
                Cell[] userValueColumn;
                Cell[] storedValueColumn;
                int columnIndex = 1;
                int numberOfColumns = sheet.getRow(0).length;

                int delta = i18nService.getValuePairColumnsDelta();

                for (int j = 0; j < numberOfColumns; j = j + delta) {
                    userValueColumn = sheet.getColumn(j);
                    storedValueColumn = sheet.getColumn(j + (delta - 1));

                    if (listName.equals(userValueColumn[posFormName].getContents().trim())) {
                        while (columnIndex < userValueColumn.length) {
                            try {
                                String storedValue = "";
                                if (columnIndex < storedValueColumn.length) {
                                    storedValue = storedValueColumn[columnIndex].getContents().trim();
                                }
                                if (StringUtils.isNotBlank(userValueColumn[columnIndex].getContents())) {
                                    qualifiers.add(storedValue);
                                }
                                columnIndex++;
                            } catch (RuntimeException e) {
                                log.error(e.getMessage() + " column(j): " + j + " columnIndex(row):" + columnIndex);
                                throw e;
                            }
                        }
                    }
                }
            }
        }
        return qualifiers;
    }

    private MetadataFieldConfig getMetadataField(String schema, String element, String qualifier) {
        return new MetadataFieldConfig(schema, element, StringUtils.isBlank(qualifier) ? null : qualifier);
    }

    public SubmissionFormGeneratorI18nService getI18nService() {
        return i18nService;
    }

    public void setI18nService(SubmissionFormGeneratorI18nService i18nService) {
        this.i18nService = i18nService;
    }

}
