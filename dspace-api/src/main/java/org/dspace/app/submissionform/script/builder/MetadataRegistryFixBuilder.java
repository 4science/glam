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
import org.dspace.app.submissionform.script.exception.InputFormException;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.NonUniqueMetadataException;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.core.Context;
import org.dspace.importer.external.metadatamapping.MetadataFieldConfig;
import org.dspace.services.factory.DSpaceServicesFactory;

public class MetadataRegistryFixBuilder implements IInputFormFixBuilder {

    private MetadataFieldConfig eq;

    public MetadataRegistryFixBuilder(MetadataFieldConfig eq) {
        super();
        this.eq = eq;
    }

    @Override
    public void fix(Context context) throws InputFormException {
        MetadataSchema schema;
        try {
            String name = eq.getSchema();
			MetadataSchemaService service = ContentServiceFactory.getInstance().getMetadataSchemaService();
			MetadataFieldService fieldService = ContentServiceFactory.getInstance().getMetadataFieldService();
			
            schema = service.find(context, name);
            if (schema == null) {
            	schema = service.create(context, name, DSpaceServicesFactory.getInstance().getConfigurationService()
            	                .getProperty("dspace.url") + "/" + name);
            }

            String qual = null;
            if (StringUtils.isNotBlank(eq.getQualifier()) && !StringUtils.equalsIgnoreCase("''", eq.getQualifier())) {
                qual = eq.getQualifier();
            }
            MetadataField field = fieldService.create(context, schema, eq.getElement(), qual, null);
        } catch (SQLException e1) {
            throw new InputFormException();
        } catch (AuthorizeException e) {
            throw new InputFormException();
        } catch (NonUniqueMetadataException e) {
            throw new InputFormException();
        }
    }

}
