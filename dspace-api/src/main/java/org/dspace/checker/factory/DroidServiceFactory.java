/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.factory;

import org.dspace.checker.service.DroidCheckResultService;
import org.dspace.checker.service.DroidCheckStatusService;
import org.dspace.checker.service.DroidHistoryService;
import org.dspace.checker.service.DroidValidationService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public abstract class DroidServiceFactory {

    public abstract DroidCheckResultService getDroidCheckResultService();
    public abstract DroidCheckStatusService getDroidCheckStatusService();
    public abstract DroidHistoryService getDroidHistoryService();
    public abstract DroidValidationService getDroidValidationService();


    public static DroidServiceFactory getInstance() {
        return DSpaceServicesFactory.getInstance()
                                    .getServiceManager()
                                    .getServiceByName("droidServiceFactory", DroidServiceFactory.class);
    }
}
