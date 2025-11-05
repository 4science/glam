/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the Curation Orchestrator Script.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class CurationOrchestratorScriptIT extends AbstractControllerIntegrationTest {

    private Collection collection;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();
        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        this.collection = CollectionBuilder.createCollection(context, parentCommunity)
                                           .withEntityType("Publication")
                                           .withName("Collection Publications")
                                           .build();
    }

    @Test
    public void launchCurationOrchestratorScriptForItemIT() throws Exception {
        context.turnOffAuthorisationSystem();
        Item publication = ItemBuilder.createItem(context, collection)
                                      .withTitle("Publication Item test")
                                      .withAuthor("Amlinger, Carolin")
                                      .withType("content")
                                      .build();

        Bitstream bitstream;
        try (InputStream is = IOUtils.toInputStream("Content for Bitstream 1", StandardCharsets.UTF_8)) {
            bitstream = BitstreamBuilder.createBitstream(context, publication, is)
                                        .withName("test.pdf")
                                        .withMimeType("application/pdf")
                                        .build();
        }
        context.restoreAuthSystemState();

        String scriptName = "curateOrchestrator";
        String[] args = new String[] { scriptName, "-t", "pdfaTransformer", "-id", publication.getID().toString() };

        // run the script
        //runDSpaceScript(args);
    }

}
