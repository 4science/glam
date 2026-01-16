/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.service;

import java.util.List;

public class InputFormMapDefinition {

    private String collectionName;
    private List<String> collectionList;

    public InputFormMapDefinition(String collectionName, List<String> collectionList) {
        super();
        this.collectionName = collectionName;
        this.collectionList = collectionList;
    }

    @Override
    public boolean equals(Object obj) {
        return this.collectionName.equals(obj);
    }

    @Override
    public int hashCode() {
        return this.collectionName != null ? this.collectionName.hashCode() : super.hashCode();
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public List<String> getCollectionList() {
        return collectionList;
    }

    public void setCollectionList(List<String> collectionList) {
        this.collectionList = collectionList;
    }

}
