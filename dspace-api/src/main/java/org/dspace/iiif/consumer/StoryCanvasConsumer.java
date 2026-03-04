/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.iiif.consumer;

import static org.dspace.content.Item.ANY;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.util.UUIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer that intercepts modifications to Story items and populates
 * dc.relation.story on the Items owning the bitstreams referenced
 * by glam.bitstream metadata.
 *
 * <p>When a Story is saved/modified, for each {@code glam.bitstream} metadata
 * (whose authority contains the bitstream UUID), this consumer:
 * <ol>
 *   <li>Resolves the owning Item via {@code BitstreamService.findItemByBitstreamId}</li>
 *   <li>Clears any existing {@code dc.relation.story} pointing to this Story</li>
 *   <li>Adds {@code dc.relation.story} with value = Story title and authority = Story UUID</li>
 * </ol>
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class StoryCanvasConsumer implements Consumer {

    private static final Logger log = LoggerFactory.getLogger(StoryCanvasConsumer.class);

    private Set<UUID> itemsToProcess = new HashSet<>();

    private ItemService itemService;
    private BitstreamService bitstreamService;

    @Override
    public void initialize() throws Exception {
        this.itemService = ContentServiceFactory.getInstance().getItemService();
        this.bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    }

    @Override
    public void consume(Context context, Event event) throws Exception {
        if (Constants.ITEM != event.getSubjectType()) {
            return;
        }
        Item item = (Item) event.getSubject(context);
        if (item == null || !item.isArchived()) {
            return;
        }
        if (isStory(item)) {
            itemsToProcess.add(item.getID());
        }
    }

    private void processStory(Context context, Item story) {
        try {
            List<MetadataValue> canvasMetadata = itemService.getMetadata(story, "glam", "bitstream", "canvasid", ANY);
            for (MetadataValue mv : canvasMetadata) {
                String bitstreamUuid = mv.getValue();
                if (StringUtils.isBlank(bitstreamUuid) || !UUIDUtils.isUUID(bitstreamUuid)) {
                    continue;
                }
                processCanvas(context, bitstreamUuid, story.getName(), story.getID());
            }
        } catch (SQLException | AuthorizeException e) {
            log.error("Error processing Story {}: {}", story.getID(), e.getMessage(), e);
        }
    }

    private void processCanvas(Context context, String bitstreamUuid, String storyTitle, UUID storyUUID)
            throws SQLException, AuthorizeException {
        Item ownerItem = bitstreamService.findItemByBitstreamId(context, UUID.fromString(bitstreamUuid));
        if (ownerItem == null) {
            log.warn("No owning Item found for bitstream {}", bitstreamUuid);
            return;
        }

        List<MetadataValue> existingRelations = itemService.getMetadata(ownerItem, "dc", "relation", "story", ANY);
        boolean isExist = existingRelations.stream()
                                           .filter(mv -> StringUtils.equals(mv.getAuthority(), storyUUID.toString()))
                                           .findFirst()
                                           .isPresent();

        // Add dc.relation.story with Story title as value and Story UUID as authority
        if (!isExist) {
            itemService.addMetadata(context, ownerItem, "dc", "relation", "story", null,
                                    storyTitle, storyUUID.toString(), 600);
        }

        itemService.update(context, ownerItem);
    }

    private boolean isStory(Item item) {
        String entityType = itemService.getMetadataFirstValue(item, "dspace", "entity", "type", Item.ANY);
        return StringUtils.equals("Story", entityType);
    }

    @Override
    public void end(Context context) throws Exception {
        for (UUID storyUUID : itemsToProcess) {
            Item storyItem = itemService.find(context, storyUUID);
            processStory(context, storyItem);
        }
        itemsToProcess.clear();
    }

    @Override
    public void finish(Context ctx) throws Exception { }

}
