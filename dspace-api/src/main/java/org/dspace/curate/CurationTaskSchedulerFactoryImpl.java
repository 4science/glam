/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * The implementation for the {@link CurationTaskSchedulerFactory}
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class CurationTaskSchedulerFactoryImpl extends CurationTaskSchedulerFactory {

    @Autowired(required = true)
    private CurationTaskScheduler curationTaskScheduler;

    @Override
    public CurationTaskScheduler getCurationTaskScheduler() {
        return curationTaskScheduler;
    }

}
