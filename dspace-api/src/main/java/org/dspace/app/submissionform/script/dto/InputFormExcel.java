/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.dto;

import jxl.Cell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InputFormExcel - Base class to manage the input form definition from excel file
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class InputFormExcel {

    private static final Logger log = LoggerFactory.getLogger(InputFormExcel.class);

    public static int SUBMISSIONSDEFINITION_SHEET_NAME = 0;
    public static int STEPSDEFINITION_SHEET_NAME = 1;
    public static int INPUTFORM_SHEET_NAME = 2;
    public static int VALUEPAIRS_SHEET_NAME = 3;

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
    public static String coarNotifyStepClass = "org.dspace.app.rest.submit.step.NotifyStep";
    public static String duplicateDetectionStepClass = "org.dspace.app.rest.submit.step.DuplicateDetectionStep";

    public static String CHAR_ENCODING = "Cp1252";

    protected Cell[] sheetRow;

    /**
     * Retrieves the cell content at the specified index from the Excel sheet row.
     * 
     * @param index the cell index in the row (0-based)
     * @return the trimmed cell content
     */
    public String get(int index) {
        try {
            if (sheetRow == null || index < 0 || index >= sheetRow.length) {
                log.error("Invalid sheet row or index out of bounds: index={}", index);
                return "";
            }
            Cell cell = sheetRow[index];
            if (cell == null) {
                log.error("Cell at index {} is null - assuming empty string", index);
                return "";
            }
            String contents = cell.getContents();
            return contents != null ? contents.trim() : "";
        } catch (RuntimeException e) {
            log.error("Error reading cell at index {}: {} - assuming empty string", index, e.getMessage(), e);
            return "";
        }
    }

}