/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.dto;

import jxl.Cell;

import org.apache.commons.lang3.StringUtils;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputFormExcel {

    private static final Logger log = LoggerFactory.getLogger(InputFormExcel.class);

    private static ConfigurationService config = DSpaceServicesFactory.getInstance().getConfigurationService();
    
    public static int SUBMISSIONSDEFINITION_SHEET_NAME = 0;
    public static int STEPSDEFINITION_SHEET_NAME = 1;
    public static int INPUTFORM_SHEET_NAME = 2;
    public static int VALUEPAIRS_SHEET_NAME = 3;
    public static int SHEET_COLS_LIMIT= 255;
    
    /** **/
	public static String LANG_SEPARATOR = "######";
	
	public static String EMPTY_STRING_HOLDER = "EMPTY-STRING";
 
    public static int posSubmissionId = 0;
    public static int posSubmissionStepId = 1;   
    public static int posSubmissionStepOrder = 2;   

    public static int posStepId = 0;
    public static int posStepType = 1;   
    public static int posStepRequired = 2;
    public static int posStepVisibility = 3;
    public static int posStepOpened = 4;

    public static int posFormName = 0;
    public static int posRowNumber = 1;
    public static int posFieldStyle = 2;   
    public static int posParent = 3;
    public static int posDcSchema = 4;
    public static int posDcElement = 5;
    public static int posDcQualifier = 6;
    public static int posInputType = 7;
    public static int posListName = 8;
    public static int posValidation = 9;
    public static int posRepeatable = 10;
    public static int posVisibility = 11;
    public static int posLabel = 12;
    public static int posRequired = 13;
    public static int posHint = 14;
    public static int posTypeBind = 15;
    public static int posDisplayItem = 16;
    public static int posFormatta = 17;
    public static int posVocabulary = 18;
    public static int posClosedVocabulary = 19;
    public static int posMultilanguageValuePairs = 20;
    public static int posI18nStartOnInputForm = 21;
    
    public static String stepHeadingPrefix = "submit.progressbar.";
    public static String collectionStepClass = "org.dspace.app.rest.submit.step.CollectionStep";
    public static String formStepClass = "org.dspace.app.rest.submit.step.DescribeStep";
    public static String uploadStepClass = "org.dspace.app.rest.submit.step.UploadStep";
    public static String licenseStepClass = "org.dspace.app.rest.submit.step.LicenseStep";
    public static String duplicateStepClass = "org.dspace.app.rest.submit.step.DetectPotentialDuplicateStep";
    public static String extractionStepClass = "org.dspace.app.rest.submit.step.ExtractMetadataStep";
    public static String cclicenseStepClass = "org.dspace.app.rest.submit.step.CCLicenseStep";
    public static String accessesStepClass = "org.dspace.app.rest.submit.step.AccessConditionStep";
    public static String customUrlStepClass = "org.dspace.app.rest.submit.step.CustomUrlStep";
    public static String correctionStepClass = "org.dspace.app.rest.submit.step.CorrectionStep";
    public static String sherpaStepClass = "org.dspace.app.rest.submit.step.SherpaPolicyStep";
    public static String unpaywallStepClass = "org.dspace.app.rest.submit.step.UnpaywallStep";   
    public static String identifiersStepClass = "org.dspace.app.rest.submit.step.ShowIdentifiersStep";
    public static String externalUploadStepClass = "org.dspace.app.rest.submit.step.ExternalUploadStep";

    public static String[] i18nExtraLangs = config.getArrayProperty("inputforms.additional-languages");
    public static String CHAR_ENCODING = "Cp1252";
    public static String[] cellLabel={"label", "required", "hint"};
	
	protected Cell[] sheetRow;
	
	/**
	 * Get cell on index
	 * @param index
	 * @return
	 */
	public String get(int index) {
		try {
		    return this.sheetRow[index].getContents().trim();		    
		}
		catch (RuntimeException e) {			
		    log.warn(e.getMessage() + ": assuming empty string");
		    return "";
		}		
	}
	
	/**
	 * Get cell in multi language LANG_SEPARATOR separated base on start index
	 * @param index
	 * @param indexOnMulti
	 * @return
	 */
	public String getMultiLangCellUnion(int index, int indexOnMulti, String locale) {
		StringBuilder multiCellStringConcat = new StringBuilder();
		if (StringUtils.isNotBlank(locale)) {
			int extraLanguageCount = getIndexExtraLanguages(locale);
			multiCellStringConcat.append(get(posI18nStartOnInputForm + indexOnMulti + (extraLanguageCount * cellLabel.length)));
		} else {
			multiCellStringConcat.append(get(index));
		}

		return multiCellStringConcat.toString();
	}
	
	public static String[] getI18nExtraLangs() {
		return i18nExtraLangs;
	}
	
	public static int getValuePairColumnsDelta() {
		// default | language (>=0) | stored
		int length = getExtraLanguages().length;
	    return length + 2;
	}
	
	/**
	 * Get extra language as array (from string comma separated)
	 * 
	 * @return
	 */
	public static String[] getExtraLanguages() {
		String[] extraLang = getI18nExtraLangs();
		if (extraLang == null || (extraLang.length == 1 && extraLang[0].isEmpty())) {
			return new String[0];
		} else {
			return extraLang;
		}
	}
	
	/**
	 * 
	 * @param index
	 * @return
	 */
	public static String getLanguageCode(int index) {
		String [] extraLanguageCode = getExtraLanguages();
		
		try {
			return  extraLanguageCode[index];
		} catch (RuntimeException e) {
			return "err";
		}		
	}
	
	/**
	 * Return list of multi language cell
	 * @return
	 */
	public static String[] getMultiLangInputFormField() {
		return cellLabel;
	}

	public static int getIndexExtraLanguages(String locale) {
		String[] extraLang = getExtraLanguages();
		int index = 0;
		for (String ee : extraLang) {
			if (StringUtils.equals(ee, locale)) {
				return index;
			}
			index++;
		}
		return 0;
	}

	/**
	 * Split value in array string for i18n
	 * @param value
	 * @return
	 */
	public static String[] splitValuesForLanguage(String value) {
		if (value != null) {
			return value.split("\\s*" + LANG_SEPARATOR + "\\s*");
		} else {
			return new String[0];
		}
	}

}