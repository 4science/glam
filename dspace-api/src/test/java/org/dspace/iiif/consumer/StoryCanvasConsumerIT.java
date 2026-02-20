/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.iiif.consumer;

import static org.dspace.app.matcher.MetadataValueMatcher.withNoPlace;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for {@link StoryCanvasConsumer}.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class StoryCanvasConsumerIT extends AbstractIntegrationTestWithDatabase {

    private ItemService itemService;

    private Collection storyCol;
    private Collection pubblicationCol;

    @Before
    public void setup() {
        itemService = ContentServiceFactory.getInstance().getItemService();

        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context)
                                          .withName("Parent Community")
                                          .build();

        storyCol = CollectionBuilder.createCollection(context, parentCommunity)
                                    .withName("Story Collection")
                                    .withEntityType("Story")
                                    .build();
        pubblicationCol = CollectionBuilder.createCollection(context, parentCommunity)
                                           .withName("Publication Collection")
                                           .withEntityType("Publication")
                                           .build();
        context.restoreAuthSystemState();
    }

    @Test
    public void testStoryConsumerAddsRelationToOwnerItem() throws Exception {
        context.turnOffAuthorisationSystem();

        Item pubblicationItem = ItemBuilder.createItem(context, pubblicationCol)
                                           .withTitle("Pubblication Item")
                                           .build();
        Bitstream bitstream;
        try (InputStream is = new ByteArrayInputStream("test content".getBytes())) {
            bitstream = BitstreamBuilder.createBitstream(context, pubblicationItem, is)
                                        .withName("image.jpg")
                                        .withMimeType("image/jpeg")
                                        .build();
        }

        Item storyItem = ItemBuilder.createItem(context, storyCol)
                                    .withTitle("My Story")
                                    .withMetadata("glam", "bitstream", "canvasid", bitstream.getID().toString())
                                    .build();

        context.restoreAuthSystemState();
        pubblicationItem = context.reloadEntity(pubblicationItem);

        assertThat(pubblicationItem.getMetadata(),
              hasItem(withNoPlace("dc.relation.story", "My Story", storyItem.getID().toString())));
    }

    @Test
    public void testStoryConsumerDoesNotDuplicateRelation() throws Exception {
        context.turnOffAuthorisationSystem();
        Item pubblicationItem = ItemBuilder.createItem(context, pubblicationCol)
                                           .withTitle("Pubblication Item")
                                           .build();

        Bitstream bitstream;
        try (InputStream is = new ByteArrayInputStream("test content".getBytes())) {
            bitstream = BitstreamBuilder.createBitstream(context, pubblicationItem, is)
                                        .withName("image.jpg")
                                        .withMimeType("image/jpeg")
                                        .build();
        }

        Item storyItem = ItemBuilder.createItem(context, storyCol)
                                    .withTitle("My Story")
                                    .withMetadata("glam", "bitstream", "canvasid", bitstream.getID().toString())
                                    .build();

        context.restoreAuthSystemState();

        // first pass should have added dc.relation.story
        assertThat(pubblicationItem.getMetadata(),
              hasItem(withNoPlace("dc.relation.story", "My Story", storyItem.getID().toString())));

        // modify the Story to trigger the consumer again
        context.turnOffAuthorisationSystem();
        itemService.addMetadata(context, storyItem, "dc", "description", "abstract", null, "some description");
        itemService.update(context, storyItem);
        context.restoreAuthSystemState();

        pubblicationItem = context.reloadEntity(pubblicationItem);

        // verify no duplicate
        long count = pubblicationItem.getMetadata().stream()
                                     .filter(mv -> "dc.relation.story".equals(mv.getMetadataField().toString('.'))
                                             && storyItem.getID().toString().equals(mv.getAuthority()))
                                     .count();

        assertEquals("dc.relation.story should not be duplicated",1, count);
    }

    @Test
    public void testStoryConsumerIgnoresNonStoryItems() throws Exception {
        context.turnOffAuthorisationSystem();
        Item pubblicationItem = ItemBuilder.createItem(context, pubblicationCol)
                                           .withTitle("Pubblication Item")
                                           .build();

        Bitstream bitstream;
        try (InputStream is = new ByteArrayInputStream("test content".getBytes())) {
            bitstream = BitstreamBuilder.createBitstream(context, pubblicationItem, is)
                                        .withName("image.jpg")
                                        .withMimeType("image/jpeg")
                                        .build();
        }

        // Create a non-Story item with glam.bitstream.canvasid
        ItemBuilder.createItem(context, pubblicationCol)
                   .withTitle("Publication Item")
                   .withEntityType("Publication")
                   .withMetadata("glam", "bitstream", "canvasid", bitstream.getID().toString())
                   .build();

        context.restoreAuthSystemState();
        pubblicationItem = context.reloadEntity(pubblicationItem);

        assertThat(itemService.getMetadataByMetadataString(pubblicationItem, "dc.relation.story"), empty());
    }

    @Test
    public void testStoryConsumerWrongValue() throws Exception {
        context.turnOffAuthorisationSystem();
        Item pubblicationItem = ItemBuilder.createItem(context, pubblicationCol)
                                           .withTitle("Pubblication Item")
                                           .build();

        // Story with glam.bitstream.canvasid but wrong value (not a bitstream UUID)
        ItemBuilder.createItem(context, storyCol)
                   .withTitle("Story Without Authority")
                   .withMetadata("glam", "bitstream", "canvasid", "wrong-value")
                   .build();

        context.restoreAuthSystemState();
        pubblicationItem = context.reloadEntity(pubblicationItem);

        assertThat(itemService.getMetadataByMetadataString(pubblicationItem, "dc.relation.story"), empty());
    }

    @Test
    public void testStoryConsumerWithMultipleCanvases() throws Exception {
        context.turnOffAuthorisationSystem();

        Item pubblicationItem = ItemBuilder.createItem(context, pubblicationCol)
                                           .withTitle("Pubblication Item")
                                           .build();

        Item pubblicationItem2 = ItemBuilder.createItem(context, pubblicationCol)
                                            .withTitle("Pubblication Item 2")
                                            .build();

        Bitstream bitstream1;
        try (InputStream is = new ByteArrayInputStream("test content".getBytes())) {
            bitstream1 = BitstreamBuilder.createBitstream(context, pubblicationItem, is)
                                        .withName("image.jpg")
                                        .withMimeType("image/jpeg")
                                        .build();
        }

        Bitstream bitstream2;
        try (InputStream is2 = new ByteArrayInputStream("test content 2".getBytes())) {
            bitstream2 = BitstreamBuilder.createBitstream(context, pubblicationItem2, is2)
                                         .withName("image.jpg")
                                         .withMimeType("image/jpeg")
                                         .build();
        }

        // Story referencing two canvases (bitstreams from different items)
        Item storyItem = ItemBuilder.createItem(context, storyCol)
                                    .withTitle("Multi Canvas Story")
                                    .withMetadata("glam", "bitstream", "canvasid", bitstream1.getID().toString())
                                    .withMetadata("glam", "bitstream", "canvasid", bitstream2.getID().toString())
                                    .build();

        context.restoreAuthSystemState();

        pubblicationItem = context.reloadEntity(pubblicationItem);
        pubblicationItem2 = context.reloadEntity(pubblicationItem2);

        assertThat(pubblicationItem.getMetadata(),
              hasItem(withNoPlace("dc.relation.story", "Multi Canvas Story", storyItem.getID().toString())));
        assertThat(pubblicationItem2.getMetadata(),
              hasItem(withNoPlace("dc.relation.story", "Multi Canvas Story", storyItem.getID().toString())));
    }

}
