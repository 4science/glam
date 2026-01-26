/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.service;

import jxl.Cell;
import jxl.Sheet;
import org.apache.commons.lang3.StringUtils;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * Implementation of the i18n language service for submission form generation.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class SubmissionFormGeneratorI18nServiceImpl implements SubmissionFormGeneratorI18nService {

    private static final String[] CELL_LABEL = { "label", "required", "hint" };
    private static final int I18N_START_ON_INPUT_FORM = 21;

    private String[] extraLanguages;

    public SubmissionFormGeneratorI18nServiceImpl() {
        loadExtraLanguages();
    }

    private void loadExtraLanguages() {
        ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        String[] configLangs = configurationService.getArrayProperty("inputforms.additional-languages");
        if (configLangs == null || (configLangs.length == 1 && configLangs[0].isEmpty())) {
            this.extraLanguages = new String[0];
        } else {
            this.extraLanguages = configLangs;
        }
    }

    @Override
    public void reloadExtraLanguages() {
        loadExtraLanguages();
    }

    @Override
    public String[] getExtraLanguages() {
        return extraLanguages.clone();
    }

    @Override
    public int getIndexForLocale(String locale) {
        if (StringUtils.isBlank(locale)) {
            return 0;
        }
        for (int i = 0; i < extraLanguages.length; i++) {
            if (StringUtils.equals(extraLanguages[i], locale)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public int getValuePairColumnsDelta() {
        // default | language (>=0) | stored
        return extraLanguages.length + 2;
    }

    @Override
    public String getMultiLangCellValue(Cell[] sheetRow, int baseIndex, int indexOnMulti, String locale) {
        if (StringUtils.isNotBlank(locale)) {
            int extraLanguageCount = getIndexForLocale(locale);
            int targetIndex = I18N_START_ON_INPUT_FORM + indexOnMulti + (extraLanguageCount * CELL_LABEL.length);
            return getCellContent(sheetRow, targetIndex);
        } else {
            return getCellContent(sheetRow, baseIndex);
        }
    }

    @Override
    public String getMultiLangDisplayValue(Sheet sheet, int columnIndex, int rowIndex, String locale) {
        if (StringUtils.isNotBlank(locale)) {
            int extraLanguageCount = getIndexForLocale(locale);
            return sheet.getColumn(columnIndex + extraLanguageCount + 1)[rowIndex].getContents().trim();
        } else {
            return sheet.getColumn(columnIndex)[rowIndex].getContents().trim();
        }
    }

    private String getCellContent(Cell[] sheetRow, int index) {
        try {
            return sheetRow[index].getContents().trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

}

