/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * Abstract factory to get services for the CurationTaskScheduler,
 * use ScriptServiceFactory.getInstance() to retrieve an implementation
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public abstract class CurationTaskSchedulerFactory {

    private static final String BEAN_NAME = "curationTaskSchedulerFactory";

    /**
     * This method will return an instance of the CurationTaskScheduler
     *
     * @return An instance of the CurationTaskScheduler
     */
    public abstract CurationTaskScheduler getCurationTaskScheduler();

    /**
     * Use this method to retrieve an implementation of the CurationTaskSchedulerFactory
     * to use to retrieve the different beans
     *
     * @return An implementation of the CurationTaskSchedulerFactory
     */
    public static CurationTaskSchedulerFactory getInstance() {
        return DSpaceServicesFactory.getInstance()
                                    .getServiceManager()
                                    .getServiceByName(BEAN_NAME, CurationTaskSchedulerFactory.class);
    }

}
