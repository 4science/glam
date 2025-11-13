/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.viaf;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class ViafServiceFactoryImpl extends ViafServiceFactory {

    protected ViafImportMetadataSourceServiceImpl metadataSourceService;

    public ViafServiceFactoryImpl(ViafImportMetadataSourceServiceImpl metadataSourceService) {
        this.metadataSourceService = metadataSourceService;
    }

    @Override
    public ViafImportMetadataSourceServiceImpl getRorImportMetadataSourceService() {
        return metadataSourceService;
    }
}
