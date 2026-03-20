/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.iiif.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.dspace.app.iiif.model.generator.CanvasGenerator;
import org.dspace.app.iiif.model.generator.ImageContentGenerator;
import org.dspace.app.iiif.service.utils.BitstreamIIIFVirtualMetadata;
import org.dspace.app.iiif.service.utils.IIIFUtils;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.core.I18nUtil;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * This service provides methods for creating {@code Canvases}. There should be a single instance of
 * this service per request. The {@code @RequestScope} provides a single instance created and available during
 * complete lifecycle of the HTTP request.
 *
 * @author Michael Spalti  mspalti@willamette.edu
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 */
@RequestScope
@Component
public class CanvasService extends AbstractResourceService {

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(CanvasService.class);

    @Autowired
    ImageContentService imageContentService;

    @Autowired
    IIIFUtils utils;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    BitstreamService bitstreamService;

    protected String[] BITSTREAM_METADATA_FIELDS;

    /**
     * Used when default dimensions are set to -1 in configuration.
     */
    int dynamicDefaultWidth = 0;
    int dynamicDefaultHeight = 0;


    /**
     * Constructor.
     * 
     * @param configurationService the DSpace configuration service.
     */
    public CanvasService(ConfigurationService configurationService) {
        setConfiguration(configurationService);
        BITSTREAM_METADATA_FIELDS = configurationService.getArrayProperty("iiif.metadata.bitstream");
        // Set default dimensions in parent class.
        setDefaultCanvasDimensions();
    }

    /**
     * Checks for "iiif.image.width" metadata in IIIF bundles. When bitstream
     * metadata is not found for the first image in the bundle this method updates the
     * default canvas dimensions for the request based on the actual image dimensions,
     * using the IIIF image service. Called once for each manifest.
     * @param bundles IIIF bundles for this item
     */
    protected void guessCanvasDimensions(Context context, List<Bundle> bundles) {
        int[] imageDims = computeDynamicDimensions(context, bundles);
        // update the fallback dimensions
        defaultCanvasWidthFallback = imageDims[0];
        defaultCanvasHeightFallback = imageDims[1];
        setDefaultCanvasDimensions();
    }

    public int[] computeDynamicDimensions(Context context, List<Bundle> bundles) {
        // find the first bitstream in the bundles that is an IIIF bitstream and does not have width metadata.
        // if found, guess the image dimensions and return them. If not found, return the fallback defaults.
        return bundles.stream()
                      .map(bundle -> computeDynamicDimensions(context, bundle))
                      .filter(Optional::isPresent)
                      .map(Optional::get)
                      .findFirst()
                      .orElseGet(() -> new int[] {defaultCanvasWidthFallback, defaultCanvasHeightFallback});
    }

    private Optional<int[]> computeDynamicDimensions(Context context, Bundle bundle) {
        // find the first bitstream in the bundle that is an IIIF bitstream and does not have width metadata.
        // if found, guess the image dimensions and return them.
        return bundle.getBitstreams()
                     .stream()
                     .filter(bitstream -> utils.isIIIFBitstream(context, bitstream))
                     .findFirst()
                     .filter(bitstream -> !utils.hasWidthMetadata(bitstream))
                     .map(this::guessImageDims);
    }

    private int[] guessImageDims(Bitstream bitstream) {
        int[] imageDims = utils.getImageDimensions(bitstream);
        if (imageDims != null && imageDims.length == 2) {
            return imageDims;
        }
        return null;
    }

    /**
     * Sets the height and width dimensions for all images when "iiif.image.default-width"
     * and "iiif.image.default-height" are set to -1 in DSpace configuration. The values
     * are updated only when the bitstream does not have its own image dimension metadata.
     * @param bitstream
     */
    private void setCanvasDimensions(Bitstream bitstream) {
        int[] imageDims = computeDynamicDefaultSizes(bitstream);
        dynamicDefaultWidth = imageDims[0];
        dynamicDefaultHeight = imageDims[1];
    }

    /**
     * Computes the default canvas dimensions based on the image dimensions retrieved from the image server.
     * The dynamic defaults are used only when the configured defaults are set to -1
     * and the bitstream does not have width metadata.
     *
     * @param bitstream the bitstream for which to compute the default dimensions.
     * @return an array with the width and height dimensions. The first element is the width, the second is the height.
     */
    public int[] computeDynamicDefaultSizes(Bitstream bitstream) {
        // if dynamic default is not enabled or the bitstream has width metadata, return the configured defaults.
        if (!isDynamicComputeEnabled() || utils.hasWidthMetadata(bitstream)) {
            return new int[] {DEFAULT_CANVAS_WIDTH, DEFAULT_CANVAS_WIDTH};
        }

        // get the dimensions of the image from the image server.
        int[] imageDims = guessImageDims(bitstream);
        // if the image server is not available or does not return dimensions,
        // log an error and use the fallback defaults
        if (imageDims == null) {
            log.error(
                "Unable to retrieve dimensions from the image server for: "
                    + bitstream.getID() + " Using default dimensions."
            );
            return new int[] {defaultCanvasWidthFallback, defaultCanvasHeightFallback};
        }

        return imageDims;
    }

    private boolean isDynamicComputeEnabled() {
        return !isDefaultCanvasSizeSet() || isDefaultCanvasDynamic();
    }

    private boolean isDefaultCanvasDynamic() {
        return DEFAULT_CANVAS_HEIGHT == -1 && DEFAULT_CANVAS_WIDTH == -1;
    }

    private boolean isDefaultCanvasSizeSet() {
        return configurationService.hasProperty("iiif.canvas.default-height") ||
            configurationService.hasProperty("iiif.canvas.default-width");
    }

    /**
     * Use the dynamic default if the configured default width is -1.
     * @return
     */
    private int getDefaultWidth() {
        if (DEFAULT_CANVAS_WIDTH == -1) {
            return dynamicDefaultWidth;
        }
        return DEFAULT_CANVAS_WIDTH;
    }

    /**
     * Use the dynamic default if the configured default height is -1.
     * @return
     */
    private int getDefaultHeight() {
        if (DEFAULT_CANVAS_HEIGHT == -1) {
            return dynamicDefaultHeight;
        }
        return DEFAULT_CANVAS_HEIGHT;
    }

    protected CanvasGenerator getCanvas(Context context, String manifestId, Bitstream bitstream, Bundle bundle,
            Item item, String canvasId, String mimeType) {
        return getCanvas(context, manifestId, bitstream, bundle, item, canvasId, mimeType, null);
    }

    /**
     * Creates a single {@code CanvasGenerator}.
     *
     * @param context DSpace Context
     * @param manifestId  manifest id
     * @param bitstream DSpace bitstream
     * @param bundle  DSpace bundle
     * @param item  DSpace item
     * @param canvasId  the canvas identifier
     * @param mimeType  bitstream mimetype
     * @param index  the index (1-based)
     * @return a canvas generator
     */
    protected CanvasGenerator getCanvas(Context context, String manifestId, Bitstream bitstream, Bundle bundle,
            Item item, String canvasId, String mimeType, Integer index) {
        String canvasNaming = utils.getCanvasNaming(item, I18nUtil.getMessage("iiif.canvas.default-naming"));
        String defaultLabel = "";
        if (StringUtils.isNotBlank(canvasNaming)) {
            defaultLabel = canvasNaming + " ";
        }
        if (index != null) {
            defaultLabel += index;
        } else {
            defaultLabel += canvasId;
        }
        String label = utils.getIIIFLabel(bitstream, defaultLabel);

        setCanvasDimensions(bitstream);

        int canvasWidth = utils.getCanvasWidth(bitstream, bundle, item, getDefaultWidth());
        int canvasHeight = utils.getCanvasHeight(bitstream, bundle, item, getDefaultHeight());

        ImageContentGenerator image = imageContentService.getImageContent(UUID.fromString(canvasId), mimeType,
                imageUtil.getImageProfile(), IMAGE_PATH);

        ImageContentGenerator thumb = imageContentService.getImageContent(UUID.fromString(canvasId), mimeType,
                thumbUtil.getThumbnailProfile(), THUMBNAIL_PATH);

        return addMetadata(context, bitstream,
                new CanvasGenerator(IIIF_ENDPOINT + manifestId + "/canvas/" + canvasId)
                    .addImage(image.generateResource()).addThumbnail(thumb.generateResource()).setHeight(canvasHeight)
                    .setWidth(canvasWidth).setLabel(label));
    }

    /**
     * Ranges expect the Canvas object to have only an identifier.
     *
     * @param startCanvas the start canvas identifier
     * @return canvas generator
     */
    protected CanvasGenerator getRangeCanvasReference(String startCanvas) {
        return new CanvasGenerator(startCanvas);
    }

    /**
     * Adds metadata to canvas.
     * @param context DSpace context
     * @param bitstream DSpace bitstream
     * @param canvasGenerator canvas generator
     * @return canvas generator
     */
    private CanvasGenerator addMetadata(Context context, Bitstream bitstream, CanvasGenerator canvasGenerator) {
        BitstreamService bService = ContentServiceFactory.getInstance().getBitstreamService();
        for (String field : BITSTREAM_METADATA_FIELDS) {
            if (StringUtils.startsWith(field, "@") && StringUtils.endsWith(field, "@")) {
                String virtualFieldName = field.substring(1, field.length() - 1);
                String beanName = BitstreamIIIFVirtualMetadata.IIIF_BITSTREAM_VIRTUAL_METADATA_BEAN_PREFIX +
                        virtualFieldName;
                BitstreamIIIFVirtualMetadata virtual = applicationContext.getBean(beanName,
                        BitstreamIIIFVirtualMetadata.class);
                List<String> values = virtual.getValues(context, bitstream);
                if (values.size() > 0) {
                    if (values.size() > 1) {
                        canvasGenerator.addMetadata("bitstream.iiif-virtual." + virtualFieldName, values.get(0),
                                values.subList(1, values.size()).toArray(new String[values.size() - 1]));
                    } else {
                        canvasGenerator.addMetadata("bitstream.iiif-virtual." + virtualFieldName, values.get(0));
                    }
                }
            } else {
                String[] eq = field.split("\\.");
                String schema = eq[0];
                String element = eq[1];
                String qualifier = null;
                if (eq.length > 2) {
                    qualifier = eq[2];
                }
                List<MetadataValue> metadata = bService.getMetadata(bitstream, schema, element, qualifier,
                        Item.ANY);
                List<String> values = new ArrayList<String>();
                for (MetadataValue meta : metadata) {
                    if (meta.getValue() != null) {
                        values.add(meta.getValue());
                    }
                }
                if (!values.isEmpty()) {
                    if (values.size() > 1) {
                        canvasGenerator.addMetadata("bitstream." + field, values.get(0),
                                values.subList(1, values.size()).toArray(new String[values.size() - 1]));
                    } else {
                        canvasGenerator.addMetadata("bitstream." + field, values.get(0));
                    }
                }
            }
        }
        return canvasGenerator;
    }

}
