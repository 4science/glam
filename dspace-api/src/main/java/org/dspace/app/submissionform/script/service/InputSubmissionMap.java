/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.dspace.content.Collection;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.core.Context;
import org.jdom2.Element;

public abstract class InputSubmissionMap {

    public abstract String buildMapping(Collection col);

    public List<InputFormMapDefinition> create(Element formMap, Context context, String defaultDefinition)
            throws SQLException {

        String handle;
        Element nameMap;

        nameMap = new Element("name-map");
        nameMap.setAttribute("collection-handle", "default");
        nameMap.setAttribute("submission-name", defaultDefinition);
        formMap.addContent(nameMap);

        CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();
        List<Collection> collections = collectionService.findAll(context);
        List<InputFormMapDefinition> collectionDefinitionList = new ArrayList<InputFormMapDefinition>();

        for (Collection col : collections) {
            if (col.getSubmitters() == null) {
                continue;
            }
            String collectionDefinition = buildMapping(col);

            if (collectionDefinition != null) {
                handle = col.getHandle();
                handle = handle.replace(" ", "");
                boolean definitionExisting = false;

                for (InputFormMapDefinition currentDefinition : collectionDefinitionList) {
                    if (currentDefinition.getCollectionName().equals(collectionDefinition)) {
                        currentDefinition.getCollectionList().add(handle);
                        definitionExisting = true;
                        break;
                    }
                }

                if (!definitionExisting) {
                    List<String> collezioneList = new ArrayList<String>();
                    collezioneList.add(handle);
                    collectionDefinitionList.add(new InputFormMapDefinition(collectionDefinition, collezioneList));
                }

                nameMap = new Element("name-map");
                nameMap.setAttribute("collection-handle", handle);
                nameMap.setAttribute("submission-name", collectionDefinition);
                formMap.addContent(nameMap);
            } else {
                handle = col.getHandle();
                System.out.println("Mapping not found for : " + handle + " - use default mapping (no mapping)");
            }
        }
        return collectionDefinitionList;
    }

}