/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.iiif.service;

import static org.dspace.content.Item.ANY;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.iiif.service.utils.IIIFUtils;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for generating IIIF Presentation API 2.0 manifests for Story entities.
 * This service aggregates canvases from multiple items into a single storytelling manifest.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
@Component
public class StorytellingService {

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(StorytellingService.class);

    private static final String IIIF_CONTEXT = "http://iiif.io/api/presentation/2/context.json";
    private static final String SC_MANIFEST = "sc:Manifest";
    private static final String SC_SEQUENCE = "sc:Sequence";
    private static final String SC_CANVAS = "sc:Canvas";
    private static final String OA_ANNOTATION = "oa:Annotation";
    private static final String DCTYPES_IMAGE = "dctypes:Image";
    private static final String MOTIVATION_PAINTING = "sc:painting";
    private static final String RELATED_ITEM_FORMAT = "text/html";

    @Autowired
    private ItemService itemService;
    @Autowired
    private ConfigurationService configurationService;
    @Autowired
    private BitstreamService bitstreamService;
    @Autowired
    private IIIFUtils iiifUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Returns JSON manifest response for a Story item.
     *
     * @param item the DSpace Story Item
     * @param context the DSpace context
     * @return manifest as JSON
     */
    public String getManifest(Item item, Context context) {
        String serverUrl = configurationService.getProperty("dspace.server.url");
        String storyId = item.getID().toString();

        ObjectNode manifest = objectMapper.createObjectNode();
        buildManifest(manifest, item, serverUrl, storyId, context);

        return serializeManifest(manifest, storyId);
    }

    private void buildManifest(ObjectNode manifest, Item item, String serverUrl, String storyId, Context context) {
        manifest.put("@context", IIIF_CONTEXT);
        manifest.put("@id", serverUrl + "/iiif/" + storyId + "/manifest");
        manifest.put("@type", SC_MANIFEST);

        // Label with multilanguage support
        MetadataValue storyTitle = getFirstMetadata(item, "dc", "title", null);
        if (storyTitle != null) {
            manifest.set("label", buildMultilangValue(storyTitle));
        }

        // Description with multilanguage support
        MetadataValue descMv = getFirstMetadata(item, "dc", "description", "abstract");
        if (descMv != null) {
            manifest.set("description", buildMultilangValue(descMv));
        }

        // Sequences
        ArrayNode sequences = manifest.putArray("sequences");
        ObjectNode sequence = sequences.addObject();
        sequence.put("@id", serverUrl + "/iiif/" + storyId + "/sequence/normal");
        sequence.put("@type", SC_SEQUENCE);

        // Sequence label - same as story title
        if (storyTitle != null) {
            sequence.set("label", buildMultilangValue(storyTitle));
        }

        // Canvases
        ArrayNode canvases = sequence.putArray("canvases");
        buildCanvases(canvases, item, serverUrl, storyId, context);
    }

    private void buildCanvases(ArrayNode canvases, Item item, String serverUrl, String storyId, Context context) {
        List<MetadataValue> canvasTitles = itemService.getMetadata(item, "glam", "bitstream", "name", ANY);
        List<MetadataValue> canvasIDs = itemService.getMetadata(item, "glam", "bitstream", "canvasid", ANY);
        List<MetadataValue> relatedItems = itemService.getMetadata(item, "glam", "bitstream", "relatedItem", ANY);

        if (canvasIDs.size() != canvasTitles.size()) {
            log.error("Mismatch in number of canvas titles and canvas IDs for story {}. Titles: {}, IDs: {}",
                      storyId, canvasTitles.size(), canvasIDs.size());
        }

        for (int i = 0; i < canvasIDs.size(); i++) {
            String canvasLabel = canvasTitles.get(i).getValue();
            String bitstreamUuid = canvasIDs.get(i).getValue();
            String relatedItemTitle = CollectionUtils.isEmpty(relatedItems) ? null : relatedItems.get(i).getValue();
            String relatedItemUUID = CollectionUtils.isEmpty(relatedItems) ? null : relatedItems.get(i).getAuthority();

            if (StringUtils.isBlank(bitstreamUuid)) {
                log.warn("Skipping canvas with missing bitstream UUID for story {}", storyId);
                continue;
            }

            ObjectNode canvas = canvases.addObject();
            String canvasId = serverUrl + "/iiif/" + storyId + "/canvas/" + bitstreamUuid;

            canvas.put("@id", canvasId);
            canvas.put("@type", SC_CANVAS);

            // Canvas label
            if (StringUtils.isNotBlank(canvasLabel)) {
                canvas.put("label", canvasLabel);
            }

            // Canvas dimensions - retrieve from bitstream or use configured defaults
            int[] dimensions = getCanvasDimensions(context, bitstreamUuid);
            if (dimensions != null && dimensions.length == 2) {
                canvas.put("width", dimensions[0]);
                canvas.put("height", dimensions[1]);
            } else {
                log.error("Could not retrieve dimensions for canvas:{} in story:{}.", canvasId, storyId);
            }

            // Thumbnail
            buildThumbnail(canvas, bitstreamUuid, context);

            // Images array
            buildImages(canvas, canvasId, bitstreamUuid, context);

            // related - link to the original HTML page
            if (StringUtils.isNotBlank(relatedItemUUID)) {
                var uiUrl = configurationService.getProperty("dspace.ui.url");
                var relatedItemUrl = uiUrl + "/items/" + relatedItemUUID;
                buildRelated(canvas, relatedItemTitle, relatedItemUrl);
            } else {
                log.error("Missing related item UUID for canvas {} in story {}", canvasId, storyId);
            }

            // otherContent - annotation list
            buildOtherContent(canvas, canvasId, serverUrl);
        }
    }

    private void buildRelated(ObjectNode canvas, String relatedItemTitle, String relatedItemUrl) {
        ObjectNode related = canvas.putObject("related");
        related.put("@id", relatedItemUrl);
        related.put("format", RELATED_ITEM_FORMAT);
        related.put("label", relatedItemTitle);
    }

    private void buildThumbnail(ObjectNode canvas, String bitstreamUuid, Context context) {
        var imageServer = configurationService.getProperty("iiif.image.server");
        String imageServiceId = imageServer + bitstreamUuid;

        ObjectNode thumbnail = canvas.putObject("thumbnail");
        thumbnail.put("@id", imageServiceId + "/full/90,/0/default.jpg");

        ObjectNode service = thumbnail.putObject("service");
        service.put("@context", "http://iiif.io/api/image/2/context.json");
        service.put("@id", imageServiceId);
        service.put("profile", "http://iiif.io/api/image/2/level0.json");
        service.put("protocol", "http://iiif.io/api/image");

        String mimeType = getBitstreamMimeType(context, bitstreamUuid);
        if (mimeType != null) {
            thumbnail.put("format", mimeType);
        }
    }

    private void buildImages(ObjectNode canvas, String canvasId, String bitstreamUuid, Context context) {
        ArrayNode images = canvas.putArray("images");
        ObjectNode annotation = images.addObject();

        annotation.put("@type", OA_ANNOTATION);
        annotation.put("motivation", MOTIVATION_PAINTING);

        var imageServer = configurationService.getProperty("iiif.image.server");
        String imageServiceId = imageServer + bitstreamUuid;

        // Resource with IIIF Image API service
        ObjectNode resource = annotation.putObject("resource");
        resource.put("@id", imageServiceId + "/full/full/0/default.jpg");
        resource.put("@type", DCTYPES_IMAGE);

        ObjectNode service = resource.putObject("service");
        service.put("@context", "http://iiif.io/api/image/2/context.json");
        service.put("@id", imageServiceId);
        service.put("profile", "http://iiif.io/api/image/2/level1.json");
        service.put("protocol", "http://iiif.io/api/image");

        String mimeType = getBitstreamMimeType(context, bitstreamUuid);
        if (mimeType != null) {
            resource.put("format", mimeType);
        }

        annotation.put("on", canvasId);
    }

    private void buildOtherContent(ObjectNode canvas, String canvasId, String serverUrl) {
        ArrayNode otherContent = canvas.putArray("otherContent");
        ObjectNode annotationList = otherContent.addObject();
        annotationList.put("@id", serverUrl + "/annotation/search?uri=" + canvasId);
        annotationList.put("@type", "sc:AnnotationList");
    }

    /**
     * Builds a IIIF Presentation 2.0 compliant value.
     * Without language: returns a simple TextNode (plain string).
     * With language: returns {"@value": "...", "@language": "..."}.
     */
    private JsonNode buildMultilangValue(MetadataValue mv) {
        String lang = mv.getLanguage();
        if (StringUtils.isNotBlank(lang)) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("@value", mv.getValue());
            node.put("@language", lang);
            return node;
        }
        return objectMapper.getNodeFactory().textNode(mv.getValue());
    }

    private MetadataValue getFirstMetadata(Item item, String schema, String element, String qualifier) {
        List<MetadataValue> values = itemService.getMetadata(item, schema, element, qualifier, Item.ANY);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * Gets canvas dimensions from bitstream.
     * Retrieve from image server.
     */
    private int[] getCanvasDimensions(Context context, String bitstreamUuid) {
        // try to get dimensions from the bitstream via IIIF image server
        try {
            Bitstream bitstream = bitstreamService.find(context, UUID.fromString(bitstreamUuid));
            if (bitstream != null) {
                return iiifUtils.getImageDimensions(bitstream);
            }
        } catch (SQLException e) {
            log.warn("Could not retrieve bitstream {} for dimensions: {}", bitstreamUuid, e.getMessage());
        }
        return null;
    }

    private String getBitstreamMimeType(Context context, String bitstreamUuid) {
        try {
            Bitstream bitstream = bitstreamService.find(context, UUID.fromString(bitstreamUuid));
            if (bitstream != null) {
                return iiifUtils.getBitstreamMimeType(bitstream, context);
            }
        } catch (SQLException e) {
            log.warn("Could not retrieve mimetype for bitstream {}: {}", bitstreamUuid, e.getMessage());
        }
        return null;
    }

    private String serializeManifest(ObjectNode manifest, String storyId) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
        } catch (JsonProcessingException e) {
            log.error("Error serializing storytelling manifest for item {}", storyId, e);
            throw new RuntimeException("Error generating manifest", e);
        }
    }

}
