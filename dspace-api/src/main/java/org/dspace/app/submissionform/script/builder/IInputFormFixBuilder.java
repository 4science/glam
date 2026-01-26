/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.builder;

import org.dspace.app.submissionform.script.exception.InputFormException;
import org.dspace.core.Context;

public interface IInputFormFixBuilder {

    void fix(Context context) throws InputFormException;

}
