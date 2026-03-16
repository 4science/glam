/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.configuration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dspace.kernel.config.SpringLoader;
import org.dspace.services.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a specific customer spring bean definition loader.
 * The main folder is configured to be <pre>${dspace.dir}/${customer.dir}/spring</pre>
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class CustomerSpringLoader implements SpringLoader {

    private static final Logger log = LoggerFactory.getLogger(CustomerSpringLoader.class);
    public static final String CUSTOMER_SPRING_DIR = "CUSTOMER_SPRING_DIR";

    @Override
    public String[] getResourcePaths(ConfigurationService configurationService) {
        log.info("Attempting to load customer config from CUSTOMER_SPRING_DIR: {}",
                 Arrays.toString(configurationService.getArrayProperty("CUSTOMER_SPRING_DIR")));
        try {
            String[] customerSpringDir = configurationService.getArrayProperty(CUSTOMER_SPRING_DIR);
            if (customerSpringDir == null || customerSpringDir.length < 1) {
                return new String[0];
            }

            List<String> customerConfigs = new ArrayList<>(customerSpringDir.length);
            for (String config : customerSpringDir) {
                log.info("Loading customer spring configuration from: {}", config);
                try {
                    String configPath = Path.of(config).toUri().toURL() + XML_SUFFIX;
                    customerConfigs.add(configPath);
                } catch (Exception e) {
                    log.error("Cannot load the customer-spring config from {}", config, e);
                }
            }
            return customerConfigs.toArray(String[]::new);
        } catch (Exception e) {
            log.error("Error while loading configurations from CUSTOMER_SPRING_DIR property!", e);
            return new String[0];
        }
    }
}
