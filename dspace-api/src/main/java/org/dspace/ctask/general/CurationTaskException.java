/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.general;

/**
 *  This is a common Exception thrown by CurationTasks
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class CurationTaskException extends Exception {

    public CurationTaskException(String message, Throwable exception) {
        super(message, exception);
    }
}
