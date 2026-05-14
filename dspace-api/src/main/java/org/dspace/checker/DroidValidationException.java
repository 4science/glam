/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DroidValidationException extends Throwable {
    public DroidValidationException() {
    }

    public DroidValidationException(String message) {
        super(message);
    }

    public DroidValidationException(String message, Throwable e) {
        super(message, e);
    }

    public DroidValidationException(Throwable cause) {
        super(cause);
    }
}
