/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.viaf;

import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * Factory that handles {@code ViafImportMetadataSourceServiceImpl} instance
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public abstract class ViafServiceFactory {

    public abstract ViafImportMetadataSourceServiceImpl getRorImportMetadataSourceService();

    public static ViafServiceFactory getInstance() {
        return DSpaceServicesFactory.getInstance().getServiceManager()
                                    .getServiceByName("viafServiceFactory", ViafServiceFactory.class);
    }

}
