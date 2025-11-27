/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.submissionform.script.builder.IInputFormFixBuilder;
import org.dspace.app.submissionform.script.builder.InputFormErrorBuilder;
import org.dspace.app.submissionform.script.checker.ExcelSheetValidator;
import org.dspace.app.submissionform.script.dto.InputFormExcel;
import org.dspace.app.submissionform.script.exception.InputFormException;
import org.dspace.app.submissionform.script.service.SubmissionFormGeneratorI18nService;
import org.dspace.app.submissionform.script.service.SubmissionFormXmlGenerator;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.kernel.ServiceManager;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;

/**
 * Class that will generate the submission-forms XML files
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class SubmissionFormGenerator
        extends DSpaceRunnable<SubmissionFormGeneratorScriptConfiguration<SubmissionFormGenerator>> {

    private final static Logger log = LogManager.getLogger(SubmissionFormGenerator.class);

    public static final String SUBMISSION_FORM_GENERATOR_SCRIPT_NAME = "generate-submission-forms";
    private static final String ITEM_SUBMISSION_FILE_NAME = "item-submission.xml";
    private static final String SUBMISSION_FORMS_FILE_NAME = "submission-forms";
    private static final String OUTPUT_ZIP_FILE_NAME = "submission-forms.zip";
    private static final String ZIP_TYPE = "application/zip";
    private static final String XML_TYPE = ".xml";


    private Context context;
    private String fileExcel;
    private boolean forceUpload;
    private String outputPath = "";
    private String defaultDefinition;

    // Services
    private SubmissionFormXmlGenerator xmlGenerator;
    private List<ExcelSheetValidator> excelSheetValidators;
    private SubmissionFormGeneratorI18nService i18nService;

    @Override
    public void setup() throws ParseException {
        ServiceManager sm = new DSpace().getServiceManager();
        this.excelSheetValidators = sm.getServicesByType(ExcelSheetValidator.class);
        this.xmlGenerator = sm.getServiceByName("submissionFormXmlGenerator", SubmissionFormXmlGenerator.class);
        this.i18nService = sm.getServiceByName("submissionFormGeneratorI18nService",
                                               SubmissionFormGeneratorI18nService.class);
        parseCommandLineOptions();
    }

    private void parseCommandLineOptions() {
        this.forceUpload = commandLine.hasOption('f');
        this.fileExcel = commandLine.getOptionValue('e');
        this.defaultDefinition = commandLine.getOptionValue('d');
        if (commandLine.hasOption('p')) {
            String param = commandLine.getOptionValue('p');
            outputPath = param.endsWith("/") ? param : param + "/";
        }
    }

    @Override
    public void internalRun() throws Exception {
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        File xlsFile = getFile();
        File itemSubmissionFile = generateFile(ITEM_SUBMISSION_FILE_NAME);
        File submissionFormFile = generateFile(SUBMISSION_FORMS_FILE_NAME + XML_TYPE);

        context.turnOffAuthorisationSystem();
        // Validate xls file
        List<InputFormErrorBuilder> errors = validateFileXls(xlsFile);

        boolean hasNoErrors = errors.isEmpty();
        boolean hasBlockingErrors = hasBlockingErrors(errors);

        // Try to fix warnings if force upload is enabled and there are no blocking errors
        if (!hasBlockingErrors && this.forceUpload) {
            errors = tryFixWarnings(errors);
            hasBlockingErrors = hasBlockingErrors(errors);
        }
        logErrors(errors);
        if (canProceedWithGeneration(hasNoErrors, hasBlockingErrors)) {
            generateXmlFiles(xlsFile, submissionFormFile, itemSubmissionFile, null);
        } else {
            throw new InputFormException("Blocking errors found, cannot proceed to XML generation.");
        }

        File zipFile = createTempZip();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addFileToZip(zipOutputStream, submissionFormFile);
            addFileToZip(zipOutputStream, itemSubmissionFile);

            for (String extraLanguage : i18nService.getExtraLanguages()) {
                String xmlFileExtra = SUBMISSION_FORMS_FILE_NAME + "_" + extraLanguage + XML_TYPE;
                File submissionFormFileLocalized = generateFile(xmlFileExtra);
                log.info("Processing xml for extra language:{}", extraLanguage);
                generateXmlFiles(xlsFile, submissionFormFileLocalized, null, extraLanguage);
                addFileToZip(zipOutputStream, submissionFormFileLocalized);
            }
        }
        attachZipToProcess(zipFile);
        copyZipToOutputPath(zipFile);
        context.restoreAuthSystemState();
    }

    private File generateFile(String name) {
        File file = new File(name);
        file.deleteOnExit();
        return file;
    }

    private File createTempZip() throws IOException {
        File zipFile = File.createTempFile("submission-forms-", ".zip");
        zipFile.deleteOnExit();
        return zipFile;
    }

    private void addFileToZip(ZipOutputStream zipOutputStream, File file) throws IOException {
        log.info("Adding file to zip:{}", file.getName());
        ZipEntry zipEntry = new ZipEntry(file.getName());
        zipOutputStream.putNextEntry(zipEntry);
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            fileInputStream.transferTo(zipOutputStream);
        }
        zipOutputStream.closeEntry();
    }

    private void attachZipToProcess(File zipFile) {
        log.info("Attaching zip file to the process");
        try {
            handler.writeFilestream(context, OUTPUT_ZIP_FILE_NAME, new FileInputStream(zipFile), ZIP_TYPE);
        } catch (IOException | SQLException | AuthorizeException e) {
            log.error("Error attaching zip file to the process", e);
            throw new RuntimeException(e);
        }
    }

    private void copyZipToOutputPath(File zipFile) throws IOException {
        if (StringUtils.isNotBlank(this.outputPath)) {
            log.info("Copying zip file to output path:{}", this.outputPath);
            File newZip = new File(this.outputPath + OUTPUT_ZIP_FILE_NAME);
            FileUtils.copyFile(zipFile, newZip);
        }
    }

    private File getFile() throws AuthorizeException, IOException {
        var error = "Error reading file, the file couldn't be found for filename: " + fileExcel;
        InputStream inputStream = handler.getFileStream(context, fileExcel)
                                         .orElseThrow(() -> new IllegalArgumentException(error));
        File xlsFile = File.createTempFile("submission-form-", ".xls");
        try (inputStream; FileOutputStream out = new FileOutputStream(xlsFile)) {
            inputStream.transferTo(out);
        }
        return xlsFile;
    }

    private boolean hasBlockingErrors(List<InputFormErrorBuilder> errors) {
        return errors.stream()
                     .anyMatch(error -> error.getLevel() == InputFormErrorBuilder.Level.ERROR);
    }

    private List<InputFormErrorBuilder> tryFixWarnings(List<InputFormErrorBuilder> errors) throws SQLException {
        List<InputFormErrorBuilder> remainingErrors = new ArrayList<>(errors);
        for (InputFormErrorBuilder error : errors) {
            if (error.getLevel() != InputFormErrorBuilder.Level.WARN) {
                log.info("Skipping non-warning error: {}", error.getErrorMsg());
                continue;
            }

            IInputFormFixBuilder fixBuilder = error.getFixWarn();
            if (fixBuilder != null) {
                try {
                    log.info("Attempting to fix warning:{}", error.getErrorMsg());
                    fixBuilder.fix(this.context);
                    this.context.commit();
                    remainingErrors.remove(error);
                } catch (InputFormException e) {
                    log.error("Failed to fix warning: {}", error.getErrorMsg(), e);
                    StringBuilder errorMessage = new StringBuilder("Failed input-forms force upload");
                    InputFormErrorBuilder.manageError(remainingErrors, errorMessage);
                    break;
                }
            }
        }
        return remainingErrors;
    }

    private void logErrors(List<InputFormErrorBuilder> errors) {
        errors.forEach(e -> handler.logError("LEVEL:" + e.getLevel() + " ERROR:" + e.getErrorMsg()));
    }

    private boolean canProceedWithGeneration(boolean hasNoErrors, boolean hasBlockingErrors) {
        return hasNoErrors || (!hasBlockingErrors && this.forceUpload);
    }

    private void generateXmlFiles(File xlsFile, File submissionFormFile, File itemSubmissionFile, String locale)
            throws SQLException, BiffException, IOException, InputFormException {
        if (StringUtils.isBlank(defaultDefinition)) {
            this.defaultDefinition = getDefaultSubmissiondefinitionName(xlsFile);
        }
        if (itemSubmissionFile != null) {
            xmlGenerator.generateItemSubmissionXml(xlsFile, itemSubmissionFile, context, defaultDefinition);
            handler.logInfo("**********************************");
            handler.logInfo("** Created: " + itemSubmissionFile.getName() + " **");
            handler.logInfo("**********************************");
        }
        if (submissionFormFile != null) {
            xmlGenerator.generateSubmissionFormXml(xlsFile, submissionFormFile, locale);
            handler.logInfo("***********************************");
            handler.logInfo("** Created: " + submissionFormFile.getName() + " **");
            handler.logInfo("***********************************");
        }
    }

    private List<InputFormErrorBuilder> validateFileXls(File xlsFile) throws BiffException, SQLException, IOException {
        List<InputFormErrorBuilder> errors = new ArrayList<>();
        for (ExcelSheetValidator excelValidator : excelSheetValidators) {
            List<InputFormErrorBuilder> excelErrors = excelValidator.check(xlsFile, context, defaultDefinition);
            errors.addAll(excelErrors);
        }
        printValidationMessage(errors);
        return errors;
    }

    private void printValidationMessage(List<InputFormErrorBuilder> errors) {
        if (errors.isEmpty()) {
            handler.logInfo("######################################");
            handler.logInfo("####     Validation Success!!!    ####");
            handler.logInfo("######################################");
        } else {
            handler.logInfo("######################################");
            handler.logInfo("####     Validation Failed!!!    #####");
            handler.logInfo("######################################");
        }
    }

    private String getDefaultSubmissiondefinitionName(File fileExcel) throws BiffException, IOException {
        // Set encoding for workbook
        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding(InputFormExcel.CHAR_ENCODING);
        ws.setSuppressWarnings(true);

        Workbook workbook = Workbook.getWorkbook(fileExcel, ws);
        try {
            // Sheet input form
            Sheet sheet = workbook.getSheet(InputFormExcel.SUBMISSIONSDEFINITION_SHEET_NAME);
            return sheet.getRow(1)[0].getContents().trim();
        } finally {
            workbook.close();
        }
    }


    private void assignCurrentUserInContext() throws SQLException {
        this.context = new Context();
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(this.context, uuid);
            this.context.setCurrentUser(ePerson);
        }
    }

    private void assignSpecialGroupsInContext() {
        handler.getSpecialGroups()
               .forEach(uuid -> context.setSpecialGroup(uuid));
    }

    @Override
    public SubmissionFormGeneratorScriptConfiguration getScriptConfiguration() {
        ServiceManager serviceManager = new DSpace().getServiceManager();
        return serviceManager.getServiceByName(SUBMISSION_FORM_GENERATOR_SCRIPT_NAME,
                                               SubmissionFormGeneratorScriptConfiguration.class);
    }

}
