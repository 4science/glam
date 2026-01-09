/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.dspace.AbstractUnitTest;
import org.dspace.kernel.config.SpringLoader;
import org.dspace.servicemanager.DSpaceServiceManager;
import org.dspace.servicemanager.config.DSpaceConfigurationService;
import org.dspace.services.ConfigurationService;
import org.junit.Test;

/**
 * Test class for {@link CustomerSpringLoader}.
 * This test verifies that the CustomerSpringLoader properly loads bean definitions
 * from XML files in the CUSTOMER_SPRING_DIR and that the beans are correctly created.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
public class CustomerSpringLoaderTest extends AbstractUnitTest {

    /**
     * Test that getResourcePaths returns empty array when CUSTOMER_SPRING_DIR is not configured.
     */
    @Test
    public void testGetResourcePathsWhenNotConfigured() {
        ConfigurationService configurationService = mock(ConfigurationService.class);
        when(configurationService.getArrayProperty(CustomerSpringLoader.CUSTOMER_SPRING_DIR))
            .thenReturn(null);

        CustomerSpringLoader loader = new CustomerSpringLoader();
        String[] paths = loader.getResourcePaths(configurationService);

        assertNotNull("Paths should not be null", paths);
        assertEquals("Paths should be empty when not configured", 0, paths.length);
    }

    /**
     * Test that getResourcePaths returns empty array when CUSTOMER_SPRING_DIR is empty.
     */
    @Test
    public void testGetResourcePathsWhenEmpty() {
        ConfigurationService configurationService = mock(ConfigurationService.class);
        when(configurationService.getArrayProperty(CustomerSpringLoader.CUSTOMER_SPRING_DIR))
            .thenReturn(new String[0]);

        CustomerSpringLoader loader = new CustomerSpringLoader();
        String[] paths = loader.getResourcePaths(configurationService);

        assertNotNull("Paths should not be null", paths);
        assertEquals("Paths should be empty when empty array", 0, paths.length);
    }

    /**
     * Test that getResourcePaths returns correct paths when CUSTOMER_SPRING_DIR is configured.
     */
    @Test
    public void testGetResourcePathsWhenConfigured() throws Exception {
        ConfigurationService configurationService = mock(ConfigurationService.class);
        String testPath = "/test/path";
        when(configurationService.getArrayProperty(CustomerSpringLoader.CUSTOMER_SPRING_DIR))
            .thenReturn(new String[]{testPath});

        CustomerSpringLoader loader = new CustomerSpringLoader();
        String[] paths = loader.getResourcePaths(configurationService);

        assertNotNull("Paths should not be null", paths);
        assertEquals("Should return one path", 1, paths.length);

        String expectedPath = Path.of(testPath).toUri().toURL() + SpringLoader.XML_SUFFIX;
        assertEquals("Path should match expected format", expectedPath, paths[0]);
    }

    /**
     * Test that getResourcePaths handles multiple directories.
     */
    @Test
    public void testGetResourcePathsMultipleDirectories() throws Exception {
        ConfigurationService configurationService = mock(ConfigurationService.class);
        String[] testPaths = {"/test/path1", "/test/path2"};
        when(configurationService.getArrayProperty(CustomerSpringLoader.CUSTOMER_SPRING_DIR))
            .thenReturn(testPaths);

        CustomerSpringLoader loader = new CustomerSpringLoader();
        String[] paths = loader.getResourcePaths(configurationService);

        assertNotNull("Paths should not be null", paths);
        assertEquals("Should return two paths", 2, paths.length);

        for (int i = 0; i < testPaths.length; i++) {
            String expectedPath = Path.of(testPaths[i]).toUri().toURL() + SpringLoader.XML_SUFFIX;
            assertEquals("Path " + i + " should match expected format", expectedPath, paths[i]);
        }
    }

    /**
     * Test that DSpaceServiceManager correctly integrates CustomerSpringLoader
     * when it's configured in spring.springloader.modules.
     */
    @Test
    public void testDSpaceServiceManagerIntegration() throws Exception {
        DSpaceConfigurationService configurationService = mock(DSpaceConfigurationService.class);

        // Configure the spring loader modules to include CustomerSpringLoader
        String[] springLoaderModules = {
            "org.dspace.app.configuration.APISpringLoader",
            "org.dspace.app.configuration.CustomerSpringLoader"
        };
        when(configurationService.getArrayProperty("spring.springloader.modules"))
            .thenReturn(springLoaderModules);

        // Configure CUSTOMER_SPRING_DIR for the CustomerSpringLoader
        String customerSpringDir = "custom/customer/spring";
        when(configurationService.getArrayProperty(CustomerSpringLoader.CUSTOMER_SPRING_DIR))
            .thenReturn(new String[]{customerSpringDir});

        // Test the getSpringPaths method
        String[] springPaths = DSpaceServiceManager.getSpringPaths(false, null, configurationService);

        // Verify that the paths include the customer spring path
        boolean foundCustomerPath = false;
        String expectedCustomerPath = Path.of(customerSpringDir).toUri().toURL() + SpringLoader.XML_SUFFIX;

        for (String path : springPaths) {
            if (expectedCustomerPath.equals(path)) {
                foundCustomerPath = true;
                break;
            }
        }
        assertTrue("CustomerSpringLoader path should be included in spring paths", foundCustomerPath);
    }


}
