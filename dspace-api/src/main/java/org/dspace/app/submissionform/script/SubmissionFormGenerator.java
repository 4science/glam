/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.util.GregorianCalendar;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.ListUtils;
import org.dspace.app.submissionform.script.builder.IInputFormFixBuilder;
import org.dspace.app.submissionform.script.checker.InputFormMetadataFieldChecker;
import org.dspace.app.submissionform.script.checker.InputFormRulesChecker;
import org.dspace.app.submissionform.script.checker.SubmissionDefinitionRulesChecker;
import org.dspace.app.submissionform.script.checker.SubmissionStepRulesChecker;
import org.dspace.app.submissionform.script.dto.InputFormDTO;
import org.dspace.app.submissionform.script.builder.InputFormErrorBuilder;
import org.dspace.app.submissionform.script.dto.InputFormExcel;
import org.dspace.app.submissionform.script.exception.InputFormException;
import org.dspace.app.submissionform.script.service.InputFormDefinitions;
import org.dspace.app.submissionform.script.service.InputFormValuePairs;
import org.dspace.app.submissionform.script.service.InputSubmissionMap;
import org.dspace.app.submissionform.script.service.StepDefinitions;
import org.dspace.app.submissionform.script.service.SubmissionDefinitions;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.kernel.ServiceManager;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.utils.DSpace;
import org.jdom2.Element;
import org.jdom2.DocType;
import org.jdom2.Document;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 * Class that will generate the submission-forms XML files
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class SubmissionFormGenerator extends DSpaceRunnable<SubmissionFormGeneratorScriptConfiguration> {

    public static final String SUBMISSION_FORM_GENERATOR_SCRIPT_NAME = "generate-submission-forms";
    private static final String SUBMISSION_FORMS_FILE_NAME = "submission-forms.xml";
    private static final String ITEM_SUBMISSION_FILE_NAME = "item-submission.xml";

    private Context context;
    private String fileExcel;
    private String outputPath;
    private boolean forceUpload;
    private String defaultDefinition;

    @Override
    public void setup() throws ParseException {
        parseCommandLineOptions();
    }

    private void parseCommandLineOptions() {
        this.forceUpload = commandLine.hasOption('f');
        if (commandLine.hasOption('p')) {
            String param = commandLine.getOptionValue('p');
            outputPath = param.endsWith("/") ? param : param + "/";
        } else {
            outputPath = "./";
        }
        this.defaultDefinition = commandLine.hasOption('d') ? commandLine.getOptionValue('d') : "";
        this.fileExcel = commandLine.hasOption('e') ? commandLine.getOptionValue('e') : "input-forms.xls";
    }

    @Override
    public void internalRun() throws Exception {
        assignCurrentUserInContext();
        assignSpecialGroupsInContext();

        String submissionFormFilePath = this.outputPath + SUBMISSION_FORMS_FILE_NAME;
        File submissionFormFile = new File(submissionFormFilePath);
        File itemSubmissionFile = new File(this.outputPath + ITEM_SUBMISSION_FILE_NAME);
        File xlsFile = new File(fileExcel);

        context.turnOffAuthorisationSystem();
        process(xlsFile, submissionFormFile, itemSubmissionFile, null);

        String[] extraLanguages = InputFormExcel.getExtraLanguages();
        for (String extraLang : extraLanguages) {
            String xmlFileExtra = submissionFormFilePath.replaceAll("\\.xml", "") + "_" + extraLang + ".xml";
            File submissionFormFileLocalized = new File(xmlFileExtra);
            process(xlsFile, submissionFormFileLocalized, null, extraLang);
        }
        context.restoreAuthSystemState();
    }

    private InputFormDTO process(File xlsFile, File submissionFormFile, File itemSubmissionFile, String locale)
            throws SQLException, BiffException, IOException {

        // Check xls file
        List<InputFormErrorBuilder> errors = checkFileXls(xlsFile);

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
            if ("".equals(defaultDefinition)) {
                this.defaultDefinition = getDefaultSubmissiondefinitionName(xlsFile);
            }
            if (itemSubmissionFile != null)
                createItemSubmissionXml(xlsFile, itemSubmissionFile);
            if (submissionFormFile != null)
                createSubmissionFormXml(xlsFile, submissionFormFile, locale);

        }
        return new InputFormDTO(errors);
    }

    private List<InputFormErrorBuilder> checkFileXls(File xlsFile) throws BiffException, SQLException, IOException {

        List<InputFormErrorBuilder> errors = new ArrayList<>();

        SubmissionDefinitionRulesChecker submissionCheck = new SubmissionDefinitionRulesChecker();
        List<InputFormErrorBuilder> errorSubmissionCheckExcel = submissionCheck.check(xlsFile, context, defaultDefinition);

        SubmissionStepRulesChecker stepCheck = new SubmissionStepRulesChecker();
        List<InputFormErrorBuilder> errorStepCheckExcel = stepCheck.check(xlsFile, context);


        InputFormRulesChecker check = new InputFormRulesChecker();
        List<InputFormErrorBuilder> errorCheckExcel = check.check(xlsFile, context);

        InputFormMetadataFieldChecker dspaceCheck = new InputFormMetadataFieldChecker();
        List<InputFormErrorBuilder> errorCheckDspace = dspaceCheck.check(xlsFile, context);

        if (errorSubmissionCheckExcel.isEmpty() && errorStepCheckExcel.isEmpty() && errorCheckExcel.isEmpty() && errorCheckDspace.isEmpty()) {
            System.out.println("Validation Success!!!");
        } else {
            errors = ListUtils.union(errorSubmissionCheckExcel, errorStepCheckExcel);
            errors = ListUtils.union(errors, errorCheckExcel);
            errors = ListUtils.union(errors, errorCheckDspace);
            System.out.println("###################Validation Failed!!!#####################");
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

    private void createItemSubmissionXml(File xlsFile, File itemSubmissionFile) throws SQLException, BiffException, IOException {
        DSpace dspace = new DSpace();
        InputSubmissionMap submissionMap = dspace.getServiceManager().getServiceByName("inputFormMapping", InputSubmissionMap.class);
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

        System.out.println("XML created:" + itemSubmissionFile);
    }

    private void createSubmissionFormXml(File xlsFile, File submissionFormFile, String locale) throws SQLException, BiffException, IOException {

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

        System.out.println("XML created:" + submissionFormFile);
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
