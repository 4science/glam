/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.builder;

import java.util.List;

/**
 * InputFormErrorBuilder class to manage errors and warnings
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class InputFormErrorBuilder {

    public enum Level { ERROR, WARN, INFO }

    private Level level;
    private StringBuilder errorMsg;
    private IInputFormFixBuilder fixWarn;

    public InputFormErrorBuilder(StringBuilder errorMsg, Level severity) {
        super();
        this.errorMsg = errorMsg;
        this.level = severity;
    }

    public InputFormErrorBuilder(StringBuilder errorMsg, Level severity, IInputFormFixBuilder fixWarn) {
        super();
        this.errorMsg = errorMsg;
        this.level = severity;
        this.fixWarn = fixWarn;
    }

    public static void manageError(List<InputFormErrorBuilder> errors, StringBuilder errorMessage) {
        InputFormErrorBuilder errorInputForm = new InputFormErrorBuilder(errorMessage, Level.ERROR);
        errors.add(errorInputForm);
    }

    public static void manageWarning(List<InputFormErrorBuilder> errors, StringBuilder errorMessage,
                                     IInputFormFixBuilder fixWarningInputForm) {
        InputFormErrorBuilder errorInputForm = new InputFormErrorBuilder(errorMessage, Level.WARN, fixWarningInputForm);
        errors.add(errorInputForm);
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level severity) {
        this.level = severity;
    }

    public StringBuilder getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(StringBuilder errorMsg) {
        this.errorMsg = errorMsg;
    }

    public IInputFormFixBuilder getFixWarn() {
        return fixWarn;
    }

    public void setFixWarn(IInputFormFixBuilder fixWarn) {
        this.fixWarn = fixWarn;
    }

}