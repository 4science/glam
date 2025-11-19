/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.builder;

import java.sql.SQLException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.app.submissionform.script.exception.InputFormException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.MetadataSchema;
import org.dspace.content.NonUniqueMetadataException;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.core.Context;
import org.dspace.importer.external.metadatamapping.MetadataFieldConfig;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * Builder to fix metadata registry issues for submission forms
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class MetadataRegistryFixBuilder implements IInputFormFixBuilder {

    private final static Logger log = LogManager.getLogger(MetadataRegistryFixBuilder.class);

    private MetadataFieldConfig metadataField;

    public MetadataRegistryFixBuilder(MetadataFieldConfig metadataField) {
        super();
        this.metadataField = metadataField;
    }

    @Override
    public void fix(Context context) throws InputFormException {
        try {
            String name = metadataField.getSchema();
            MetadataSchemaService service = ContentServiceFactory.getInstance().getMetadataSchemaService();
            MetadataFieldService fieldService = ContentServiceFactory.getInstance().getMetadataFieldService();

            MetadataSchema schema = service.find(context, name);
            if (schema == null) {
                var dspaceUrl = DSpaceServicesFactory.getInstance().getConfigurationService().getProperty("dspace.url");
                schema = service.create(context, name, dspaceUrl + "/" + name);
            }

            String qual = null;
            if (StringUtils.isNotBlank(metadataField.getQualifier()) &&
                !StringUtils.equalsIgnoreCase("''", metadataField.getQualifier())) {
                qual = metadataField.getQualifier();
            }
            fieldService.create(context, schema, metadataField.getElement(), qual, null);
        } catch (SQLException | NonUniqueMetadataException | AuthorizeException e) {
            log.error("Error fixing metadata registry for field: " + metadataField.toString(), e);
            throw new InputFormException();
        }
    }

}
