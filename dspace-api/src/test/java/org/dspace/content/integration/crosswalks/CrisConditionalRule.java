/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.integration.crosswalks;

import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * JUnit Rule to handle conditional test execution based on test.skip.cris configuration property.
 *
 * - Tests annotated with @SkipIfCrisDisabled run ONLY when test.skip.cris is false
 * - Tests annotated with @SkipIfCrisEnabled run ONLY when test.skip.cris is true
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
public class CrisConditionalRule implements TestRule {

    private final ConfigurationService configurationService;

    public CrisConditionalRule() {
        this.configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        SkipIfCrisDisabled skipIfDisabled = description.getAnnotation(SkipIfCrisDisabled.class);
        SkipIfCrisEnabled skipIfEnabled = description.getAnnotation(SkipIfCrisEnabled.class);

        if (skipIfDisabled != null) {
            boolean skipCris = configurationService.getBooleanProperty("test.skip.cris", true);
            // Test should run ONLY when test.skip.cris is false (CRIS is enabled)
            return new Statement() {
                @Override
                public void evaluate() {
                    Assume.assumeFalse("Skipping test because test.skip.cris is true (CRIS disabled)", skipCris);
                }
            };
        }

        if (skipIfEnabled != null) {
            boolean skipCris = configurationService.getBooleanProperty("test.skip.cris", true);
            // Test should run ONLY when test.skip.cris is true (CRIS is disabled)
            return new Statement() {
                @Override
                public void evaluate() {
                    Assume.assumeTrue("Skipping test because test.skip.cris is false (CRIS enabled)", skipCris);
                }
            };
        }

        // No conditional annotation or condition met, execute the test
        return base;
    }
}