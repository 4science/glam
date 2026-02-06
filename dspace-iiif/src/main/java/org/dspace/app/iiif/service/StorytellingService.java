/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.iiif.service;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
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

    @Autowired
    private ItemService itemService;
    @Autowired
    private ConfigurationService configurationService;

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
        buildManifest(manifest, item, serverUrl, storyId);

        return serializeManifest(manifest, storyId);
    }

    private void buildManifest(ObjectNode manifest, Item item, String serverUrl, String storyId) {
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
        buildCanvases(canvases, item, serverUrl, storyId);
    }

    private void buildCanvases(ArrayNode canvases, Item item, String serverUrl, String storyId) {
        List<MetadataValue> canvasMetadata = itemService.getMetadata(item, "iiif", "canvas", "id", Item.ANY);

        for (MetadataValue mv : canvasMetadata) {
            String canvasLabel = mv.getValue();
            String bitstreamUuid = mv.getAuthority();

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

            // Images array
            buildImages(canvas, canvasId, bitstreamUuid, serverUrl);

            // otherContent - annotation list
            buildOtherContent(canvas, canvasId, serverUrl);
        }
    }

    private void buildImages(ObjectNode canvas, String canvasId, String bitstreamUuid, String serverUrl) {
        ArrayNode images = canvas.putArray("images");
        ObjectNode annotation = images.addObject();

        annotation.put("@id", canvasId + "/painting/annotation");
        annotation.put("@type", OA_ANNOTATION);
        annotation.put("motivation", MOTIVATION_PAINTING);
        annotation.put("on", canvasId);

        // Resource - bitstream download URL, NO service
        ObjectNode resource = annotation.putObject("resource");
        resource.put("@id", serverUrl + "/api/core/bitstreams/" + bitstreamUuid + "/content");
        resource.put("@type", DCTYPES_IMAGE);
        resource.put("format", "image/jpeg");
    }

    private void buildOtherContent(ObjectNode canvas, String canvasId, String serverUrl) {
        ArrayNode otherContent = canvas.putArray("otherContent");
        otherContent.add(serverUrl + "/annotation/search?uri=" + canvasId);
    }

    /**
     * Builds a multilanguage value object: {"value": "...", "@lang": "..."}
     */
    private ObjectNode buildMultilangValue(MetadataValue mv) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("value", mv.getValue());
        String lang = mv.getLanguage();
        if (StringUtils.isNotBlank(lang)) {
            node.put("@lang", lang);
        }
        return node;
    }

    private MetadataValue getFirstMetadata(Item item, String schema, String element, String qualifier) {
        List<MetadataValue> values = itemService.getMetadata(item, schema, element, qualifier, Item.ANY);
        return values.isEmpty() ? null : values.get(0);
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
