/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.submissionform.script.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.dspace.content.Collection;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advanced implementation of InputSubmissionMap to manage the form mapping
 * based on the collection name.
 */
public class InputFormMapAdvanced extends InputSubmissionMap {

    private static final Logger log = LoggerFactory.getLogger(InputFormMapAdvanced.class);

    /** Location of config file */
    private static String configFilePath;
    private static Properties crosswalkProps;

    /**
     * Build form mapping based on the name of collection.
     * The formMap.properties contains as keys the handle and as value the form name.
     * The handle will be insert with the notation hdl_<prefix>_<number> e.g. hdl_123456789_1.
     * To manage the advanced mapping based on the collection name will be search
     * the first configuration that contains the key of the collection name (case insensitive).
     * The default mapping overwrite the others.
     */
    public String buildMapping(Collection col) {
        String collectionHandle = col.getHandle();
        String key = "hdl_" + collectionHandle.replace("/", "_");
        if (getCrosswalkProps().getProperty(key) != null) {
            return getCrosswalkProps().getProperty(key).trim();
        }

        String collectionName = col.getName();
        if (getCrosswalkProps().getProperty(collectionName) != null) {
            return getCrosswalkProps().getProperty(collectionName);
        } else {
            for (Object keyO : getCrosswalkProps().keySet()) {
                key = (String) keyO;
                if (collectionName.toLowerCase().contains(key.toLowerCase())) {
                    return getCrosswalkProps().getProperty(key);
                }
            }
            return null;
        }
    }

    public static String getConfigFilePath() {
        if (configFilePath == null) {
            configFilePath = DSpaceServicesFactory.getInstance().getConfigurationService()
                    .getProperty("dspace.dir")
                    + File.separator + "config" + File.separator + "crosswalks"
                    + File.separator + "formMap.properties";

            // Read in configuration
            crosswalkProps = new Properties();
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(configFilePath);
                crosswalkProps.load(fis);
            } catch (IOException e) {
                throw new IllegalArgumentException("InputFormMap configuration error", e);
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException ioe) {
                        log.error(ioe.getMessage(), ioe);
                    }
                }
            }
        }
        return configFilePath;
    }

    public static Properties getCrosswalkProps() {
        if (configFilePath == null) {
            getConfigFilePath();
        }
        return crosswalkProps;
    }

}
