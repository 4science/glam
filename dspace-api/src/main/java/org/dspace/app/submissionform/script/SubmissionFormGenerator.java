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

import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.util.GregorianCalendar;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.submissionform.script.builder.IInputFormFixBuilder;
import org.dspace.app.submissionform.script.builder.InputFormErrorBuilder;
import org.dspace.app.submissionform.script.checker.ExcelSheetValidator;
import org.dspace.app.submissionform.script.dto.InputFormDTO;
import org.dspace.app.submissionform.script.dto.InputFormExcel;
import org.dspace.app.submissionform.script.exception.InputFormException;
import org.dspace.app.submissionform.script.service.InputFormDefinitions;
import org.dspace.app.submissionform.script.service.InputFormValuePairs;
import org.dspace.app.submissionform.script.service.InputSubmissionMap;
import org.dspace.app.submissionform.script.service.StepDefinitions;
import org.dspace.app.submissionform.script.service.SubmissionDefinitions;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.kernel.ServiceManager;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;
import org.jdom2.DocType;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 * Class that will generate the submission-forms XML files
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class SubmissionFormGenerator
        extends DSpaceRunnable<SubmissionFormGeneratorScriptConfiguration<SubmissionFormGenerator>> {

    private final static Logger log = LogManager.getLogger(SubmissionFormGenerator.class);

    public static final String SUBMISSION_FORM_GENERATOR_SCRIPT_NAME = "generate-submission-forms";
    private static final String SUBMISSION_FORMS_FILE_NAME = "submission-forms.xml";
    private static final String ITEM_SUBMISSION_FILE_NAME = "item-submission.xml";
    private static final String OUTPUT_ZIP_FILE_NAME = "submission-forms.zip";
    private static final String ZIP_TYPE = "application/zip";

    private Context context;
    private String fileExcel;
    private boolean forceUpload;
    private String outputPath = "./";
    private String defaultDefinition;

    private InputSubmissionMap submissionMap;
    private List<ExcelSheetValidator> excelSheetValidators;

    @Override
    public void setup() throws ParseException {
        ServiceManager serviceManager = new DSpace().getServiceManager();
        this.excelSheetValidators = serviceManager.getServicesByType(ExcelSheetValidator.class);
        this.submissionMap = serviceManager.getServiceByName("inputFormMapping", InputSubmissionMap.class);
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

        String submissionFormFilePath = this.outputPath + SUBMISSION_FORMS_FILE_NAME;
        File submissionFormFile = new File(submissionFormFilePath);
        submissionFormFile.deleteOnExit();
        File itemSubmissionFile = new File(this.outputPath + ITEM_SUBMISSION_FILE_NAME);
        itemSubmissionFile.deleteOnExit();
        File xlsFile = getFile();

        context.turnOffAuthorisationSystem();
        process(xlsFile, submissionFormFile, itemSubmissionFile, null);

        File zipFile = createZipFile();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addFileToZip(zipOutputStream, submissionFormFile);
            addFileToZip(zipOutputStream, itemSubmissionFile);

            for (String extraLanguage : InputFormExcel.getExtraLanguages()) {
                String xmlFileExtra = submissionFormFilePath.replaceAll("\\.xml", "") + "_" + extraLanguage + ".xml";
                File submissionFormFileLocalized = new File(xmlFileExtra);
                log.info("Processing xml for extra language:{}", extraLanguage);
                process(xlsFile, submissionFormFileLocalized, null, extraLanguage);
                addFileToZip(zipOutputStream, submissionFormFileLocalized);
            }
        }
        attachZipToProcess(zipFile);
        context.restoreAuthSystemState();
    }

    private File createZipFile() throws IOException {
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

    private InputFormDTO process(File xlsFile, File submissionFormFile, File itemSubmissionFile, String locale)
            throws SQLException, BiffException, IOException {

        // Validate xls file
        List<InputFormErrorBuilder> errors = validateFileXls(xlsFile);
        errors.forEach(e -> handler.logInfo("LEVEL:" + e.getLevel() + " ERROR:" + e.getErrorMsg()));

        List<InputFormErrorBuilder> errorsFix = new ArrayList<>();
        errorsFix = ListUtils.union(errors, errorsFix);

        // Error
        boolean findNoError = errors.isEmpty();

        // Blocking error
        boolean findBlock = false;

        for (InputFormErrorBuilder errorInputForm : errors) {
            if (errorInputForm.getLevel() == InputFormErrorBuilder.Level.ERROR) {
                findBlock = true;
                break;
            }
        }

        // GO ahead if !blocking errors (only WARNING) and force upload
        if (!findBlock && this.forceUpload) {
            for (InputFormErrorBuilder errorInputForm : errors) {
                if (errorInputForm.getLevel() == InputFormErrorBuilder.Level.WARN) {
                    IInputFormFixBuilder fixWarn = errorInputForm.getFixWarn();
                    if (fixWarn != null) {
                        try {
                            // try to fix the WARNING
                            fixWarn.fix(this.context);
                            this.context.commit();
                            errorsFix.remove(errorInputForm);
                        } catch (InputFormException e) {
                            // Problem to fix WARNING exit
                            findBlock = true;
                            StringBuilder errorMessage = new StringBuilder("Failed input-forms force upload");
                            InputFormErrorBuilder.manageError(errorsFix, errorMessage);
                            break;
                        }
                    }
                }
            }
        }

        errors = errorsFix;

        if (findNoError || !findBlock && this.forceUpload) {
            if (StringUtils.isBlank(defaultDefinition)) {
                this.defaultDefinition = getDefaultSubmissiondefinitionName(xlsFile);
            }
            if (itemSubmissionFile != null) {
                createItemSubmissionXml(xlsFile, itemSubmissionFile);
            }
            if (submissionFormFile != null) {
                createSubmissionFormXml(xlsFile, submissionFormFile, locale);
            }
        }
        return new InputFormDTO(errors);
    }

    private List<InputFormErrorBuilder> validateFileXls(File xlsFile) throws BiffException, SQLException, IOException {
        List<InputFormErrorBuilder> errors = new ArrayList<>();
        for (ExcelSheetValidator excelValidator : excelSheetValidators) {
            List<InputFormErrorBuilder> excelErrors = excelValidator.check(xlsFile, context, defaultDefinition);
            errors.addAll(excelErrors);
        }

        if (errors.isEmpty()) {
            handler.logInfo("######################################");
            handler.logInfo("####     Validation Success!!!    ####");
            handler.logInfo("######################################");
        } else {
            handler.logInfo("######################################");
            handler.logInfo("####     Validation Failed!!!    #####");
            handler.logInfo("######################################");
        }
        return errors;
    }

    private String getDefaultSubmissiondefinitionName(File fileExcel) throws BiffException, IOException {
        // Set encoding for workbook
        WorkbookSettings ws = new WorkbookSettings();
        ws.setEncoding(InputFormExcel.CHAR_ENCODING);

        Workbook workbook = Workbook.getWorkbook(fileExcel, ws);
        // Sheet input form
        Sheet sheet = workbook.getSheet(InputFormExcel.SUBMISSIONSDEFINITION_SHEET_NAME);
        return sheet.getRow(1)[0].getContents().trim();
    }

    private void createItemSubmissionXml(File xlsFile, File itemSubmissionFile)
            throws SQLException, BiffException, IOException {

        StepDefinitions stepDefinitions = new StepDefinitions();
        SubmissionDefinitions submissionDefinitions = new SubmissionDefinitions();

        Element root = new Element("item-submission");
        Element formMapEl = new Element("submission-map");
        Element stepDefinitionsEl = new Element("step-definitions");
        Element submissionDefinitionsEl = new Element("submission-definitions");

        // XML DOCUMENT
        DocType dt = new DocType("item-submission", "item-submission.dtd");
        Document doc = new Document(root, dt);

        // rename old xml file
        renameOldInputForm(itemSubmissionFile);

        // build form
        submissionMap.create(formMapEl, context, defaultDefinition);
        stepDefinitions.create(stepDefinitionsEl, xlsFile);
        submissionDefinitions.create(submissionDefinitionsEl, xlsFile);

        root.addContent(formMapEl);
        root.addContent(stepDefinitionsEl);
        root.addContent(submissionDefinitionsEl);

        FileOutputStream out = new FileOutputStream(itemSubmissionFile);
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat().setEncoding("UTF-8"));
        outputter.output(doc, out);

        out.flush();
        out.close();

        handler.logInfo("**********************************");
        handler.logInfo("** Created: " + ITEM_SUBMISSION_FILE_NAME + " **");
        handler.logInfo("**********************************");
    }

    private void createSubmissionFormXml(File xlsFile, File submissionFormFile, String locale)
            throws BiffException, IOException {
        InputFormDefinitions formDefinitions = new InputFormDefinitions();
        InputFormValuePairs formValuePairs = new InputFormValuePairs();

        Element root = new Element("input-forms");
        Element formMapEl = new Element("submission-map");
        Element formDefinitionsEl = new Element("form-definitions");
        Element formValuePairsEl = new Element("form-value-pairs");

        // XML DOCUMENT
        DocType dt = new DocType("input-forms", "submission-forms.dtd");
        Document doc = new Document(root, dt);

        // rename old xml file
        renameOldInputForm(submissionFormFile);

        // build form
        formDefinitions.create(formDefinitionsEl, xlsFile, locale);
        formValuePairs.create(formValuePairsEl, xlsFile, locale);

        root.addContent(formDefinitionsEl);
        root.addContent(formValuePairsEl);

        FileOutputStream out = new FileOutputStream(submissionFormFile);
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat().setEncoding("UTF-8"));
        outputter.output(doc, out);

        out.flush();
        out.close();

        handler.logInfo("***********************************");
        handler.logInfo("** Created: " + SUBMISSION_FORMS_FILE_NAME + " **");
        handler.logInfo("***********************************");
    }

    private void renameOldInputForm(File xmlFile) {
        File file = new File(xmlFile.getPath());
        GregorianCalendar today = new GregorianCalendar();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String formatDate = sdf.format(today);

        int indexOf = xmlFile.getName().indexOf(".");
        String substring = xmlFile.getName().substring(0, indexOf);
        String subString2 = xmlFile.getName().substring(indexOf);
        StringBuffer name = new StringBuffer(substring.concat(formatDate).concat(subString2));

        file.renameTo(new File(file.getParentFile(), name.toString()));
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
