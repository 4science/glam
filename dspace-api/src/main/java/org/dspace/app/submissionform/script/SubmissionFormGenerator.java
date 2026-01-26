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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
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
    public static final String ITEM_SUBMISSION_FILE_NAME = "item-submission";
    public static final String SUBMISSION_FORMS_FILE_NAME = "submission-forms";
    public static final String XML_TYPE = ".xml";
    public static final String OUTPUT_ZIP_FILE_NAME = "submission-forms.zip";
    public static final String XML_SUBMISSION_FORMS_FILE = SUBMISSION_FORMS_FILE_NAME + XML_TYPE;
    public static final String XML_ITEM_SUBMISSION_FILE_NAME = ITEM_SUBMISSION_FILE_NAME + XML_TYPE;
    public static final String ZIP_TYPE = "application/zip";

    protected Context context;
    protected String fileExcel;
    protected boolean forceUpload;
    protected String outputPath = "";
    protected String defaultDefinition;

    // Services
    protected SubmissionFormXmlGenerator xmlGenerator;
    protected List<ExcelSheetValidator> excelSheetValidators;
    protected SubmissionFormGeneratorI18nService i18nService;
    protected ConfigurationService configurationService;

    @Override
    public void setup() throws ParseException {
        ServiceManager sm = new DSpace().getServiceManager();
        this.excelSheetValidators = sm.getServicesByType(ExcelSheetValidator.class);
        this.xmlGenerator = sm.getServiceByName("submissionFormXmlGenerator", SubmissionFormXmlGenerator.class);
        this.i18nService = sm.getServiceByName("submissionFormGeneratorI18nService",
                                               SubmissionFormGeneratorI18nService.class);
        this.configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
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

        File xlsFile = null;
        File tempZipFile = null;
        File itemSubmissionFile = null;
        File submissionFormFile = null;
        try {
            try {
                xlsFile = loadXlsFile();
            } catch (AuthorizeException e) {
                log.error("Authorization error accessing the file: {}", fileExcel, e);
                handler.logError("Authorization error accessing the file:" + fileExcel, e);
                throw new RuntimeException(e);
            } catch (IOException e) {
                log.error("I/O error accessing the file: {}", fileExcel, e);
                handler.logError("I/O error accessing the file" + fileExcel, e);
                throw new RuntimeException(e);
            }

            try {
                if (StringUtils.isBlank(defaultDefinition)) {
                    this.defaultDefinition = getDefaultSubmissiondefinitionName(xlsFile);
                }
            } catch (BiffException e) {
                log.error("Error reading Excel file: {}", fileExcel, e);
                handler.logError("Error reading Excel file " + fileExcel, e);
                throw new RuntimeException(e);
            }

            handleErrors(validateFileXls(xlsFile));

            itemSubmissionFile = Files.createTempFile(ITEM_SUBMISSION_FILE_NAME, XML_TYPE).toFile();
            submissionFormFile = Files.createTempFile(SUBMISSION_FORMS_FILE_NAME, XML_TYPE).toFile();

            generateXmlFiles(xlsFile, submissionFormFile, itemSubmissionFile, null);

            Path tempZip = Files.createTempFile("submission-forms-", ".zip");

            tempZipFile = tempZip.toFile();
            tempZipFile.deleteOnExit();

            try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(tempZipFile))) {
                addFileToZip(zipOutputStream, submissionFormFile, XML_SUBMISSION_FORMS_FILE);
                addFileToZip(zipOutputStream, itemSubmissionFile, XML_ITEM_SUBMISSION_FILE_NAME);

                for (String extraLanguage : i18nService.getExtraLanguages()) {
                    File submissionFormFileLocalized = null;
                    try {
                        String xmlFileExtra = SUBMISSION_FORMS_FILE_NAME + "_" + extraLanguage;
                        submissionFormFileLocalized = Files.createTempFile(xmlFileExtra, XML_TYPE).toFile();
                        log.info("Processing xml for extra language:{}", extraLanguage);
                        generateXmlFiles(xlsFile, submissionFormFileLocalized, null, extraLanguage);
                        addFileToZip(zipOutputStream, submissionFormFileLocalized,
                                     SUBMISSION_FORMS_FILE_NAME + "_" + extraLanguage + XML_TYPE);
                    } finally {
                        if (submissionFormFileLocalized != null && submissionFormFileLocalized.exists()) {
                            submissionFormFileLocalized.delete();
                        }
                    }
                }
            }
            // Attach to process and copy to output path
            attachZipToProcess(tempZip);
        } finally {
            if (tempZipFile != null && tempZipFile.exists()) {
                tempZipFile.delete();
            }
            if (xlsFile != null && xlsFile.exists()) {
                xlsFile.delete();
            }
            if (submissionFormFile != null && submissionFormFile.exists()) {
                submissionFormFile.delete();
            }
            if (itemSubmissionFile != null && itemSubmissionFile.exists()) {
                itemSubmissionFile.delete();
            }
        }

    }

    private void handleErrors(List<InputFormErrorBuilder> errors) throws SQLException, InputFormException {
        boolean hasNoErrors = errors.isEmpty();
        boolean hasBlockingErrors = hasBlockingErrors(errors);

        // Try to fix warnings if force upload is enabled and there are no blocking errors
        if (!hasBlockingErrors && this.forceUpload) {
            errors = tryFixWarnings(errors);
            hasBlockingErrors = hasBlockingErrors(errors);
        }

        logErrors(errors);

        if (!canProceedWithGeneration(hasNoErrors, hasBlockingErrors)) {
            var errorMessage = "Cannot proceed to XML generation due to errors in the input Excel file.";
            handler.logError(errorMessage);
            throw new InputFormException(errorMessage);
        }
    }

    private void addFileToZip(ZipOutputStream zipOutputStream, File file, String name) throws IOException {
        log.info("Adding file to zip:{}", file.getAbsoluteFile());
        handler.logInfo("Adding file to zip: " + file.getAbsoluteFile());
        ZipEntry zipEntry = new ZipEntry(name);
        zipOutputStream.putNextEntry(zipEntry);
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            fileInputStream.transferTo(zipOutputStream);
        }
        zipOutputStream.closeEntry();
    }


    protected void attachZipToProcess(Path zipFile) {
        log.info("Attaching zip file to the process");
        context.turnOffAuthorisationSystem();
        try {
            handler.writeFilestream(context, OUTPUT_ZIP_FILE_NAME, Files.newInputStream(zipFile), ZIP_TYPE);
        } catch (IOException | SQLException | AuthorizeException e) {
            log.error("Error attaching zip file to the process", e);
            throw new RuntimeException(e);
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private File loadXlsFile() throws AuthorizeException, IOException {
        Path tempPath = Files.createTempFile("submission-form-", ".xls");
        File xlsFile = tempPath.toFile();
        xlsFile.deleteOnExit();
        context.turnOffAuthorisationSystem();
        try (InputStream inputStream = handler.getFileStream(context, fileExcel)
                                              .orElseThrow(
                                                  () -> new IllegalArgumentException(
                                                      "Error reading file, the file couldn't be found for filename: "
                                                          + fileExcel
                                                  )
                                              )) {
            // Use NIO Files.copy which internally uses buffering for improved performance
            Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            context.restoreAuthSystemState();
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
        if (itemSubmissionFile != null) {
            xmlGenerator.generateItemSubmissionXml(xlsFile, itemSubmissionFile, context, defaultDefinition);
            handler.logInfo("**********************************");
            handler.logInfo("** Created: item-submission.xml **");
            handler.logInfo("**********************************");
        }
        if (submissionFormFile != null) {
            xmlGenerator.generateSubmissionFormXml(xlsFile, submissionFormFile, locale);
            handler.logInfo("***********************************");
            String localeInfo = StringUtils.isNotBlank(locale) ? "_" + locale : "";
            handler.logInfo("** Created: submission-forms" + localeInfo + ".xml **");
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
            handler.logWarning("######################################");
            handler.logWarning("####     Validation Failed!!!    #####");
            handler.logWarning("######################################");
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
        initContext();
        UUID uuid = getEpersonIdentifier();
        if (uuid != null) {
            EPerson ePerson = EPersonServiceFactory.getInstance().getEPersonService().find(this.context, uuid);
            this.context.setCurrentUser(ePerson);
        }
    }

    protected void initContext() {
        this.context = new Context();
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
