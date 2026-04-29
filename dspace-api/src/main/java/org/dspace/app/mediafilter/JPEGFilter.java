/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.mediafilter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * Filter image bitstreams, scaling the image to be within the bounds of
 * thumbnail.maxwidth, thumbnail.maxheight, the size we want our thumbnail to be
 * no bigger than. Creates only JPEGs.
 *
 * @author Jason Sherman jsherman@usao.edu
 */
public class JPEGFilter extends MediaFilter implements SelfRegisterInputFormats {
    private static final Logger log = LogManager.getLogger(JPEGFilter.class);

    @Override
    public String getFilteredName(String oldFilename) {
        return oldFilename + ".jpg";
    }

    /**
     * @return String bundle name
     */
    @Override
    public String getBundleName() {
        return "THUMBNAIL";
    }

    /**
     * @return String bitstreamformat
     */
    @Override
    public String getFormatString() {
        return "JPEG";
    }

    /**
     * @return String description
     */
    @Override
    public String getDescription() {
        return "Generated Thumbnail";
    }

    /**
     * Gets the rotation angle from image's metadata using ImageReader.
     * This method consumes the InputStream, so you need to be careful to don't reuse the same InputStream after
     * computing the rotation angle.
     *
     * @param buf InputStream of the image file
     * @return Rotation angle in degrees (0, 90, 180, or 270)
     */
    public static int getImageRotationUsingImageReader(InputStream buf) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(buf);
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return convertRotationToDegrees(directory.getInt(ExifIFD0Directory.TAG_ORIENTATION));
            }
        } catch (MetadataException | ImageProcessingException | IOException | OutOfMemoryError e) {
            log.error("Error reading image metadata", e);
        }
        return 0;
    }

    /**
     * Converts EXIF orientation tag value to rotation degrees.
     *
     * @param valueNode EXIF orientation value
     * @return rotation angle in degrees
     */
    public static int convertRotationToDegrees(int valueNode) {
        // Common orientation values:
        // 1 = Normal (0°)
        // 6 = Rotated 90° CW
        // 3 = Rotated 180°
        // 8 = Rotated 270° CW
        switch (valueNode) {
            case 6:
                return 90;
            case 3:
                return 180;
            case 8:
                return 270;
            default:
                return 0;
        }
    }

    /**
     * Rotates an image by the specified angle.
     *
     * @param image The original image
     * @param angle The rotation angle in degrees
     * @return Rotated image
     */
    public static BufferedImage rotateImage(BufferedImage image, int angle) {
        if (angle == 0) {
            return image;
        }

        double radians = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int newWidth = (int) Math.round(image.getWidth() * cos + image.getHeight() * sin);
        int newHeight = (int) Math.round(image.getWidth() * sin + image.getHeight() * cos);

        BufferedImage rotated = new BufferedImage(newWidth, newHeight, image.getType());
        Graphics2D g2d = rotated.createGraphics();
        AffineTransform at = new AffineTransform();

        at.translate(newWidth / 2, newHeight / 2);
        at.rotate(radians);
        at.translate(-image.getWidth() / 2, -image.getHeight() / 2);

        g2d.setTransform(at);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        return rotated;
    }

    /**
     * Calculates scaled dimension while maintaining aspect ratio.
     *
     * @param imgSize  Original image dimensions
     * @param boundary Maximum allowed dimensions
     * @return New dimensions that fit within boundary while preserving aspect ratio
     */
    private Dimension getScaledDimension(Dimension imgSize, Dimension boundary) {

        int originalWidth = imgSize.width;
        int originalHeight = imgSize.height;
        int boundWidth = boundary.width;
        int boundHeight = boundary.height;
        int newWidth = originalWidth;
        int newHeight = originalHeight;


        // First check if we need to scale width
        if (originalWidth > boundWidth) {
            // Scale width to fit
            newWidth = boundWidth;
            // Scale height to maintain aspect ratio
            newHeight = (newWidth * originalHeight) / originalWidth;
        }

        // Then check if we need to scale even with the new height
        if (newHeight > boundHeight) {
            // Scale height to fit instead
            newHeight = boundHeight;
            newWidth = (newHeight * originalWidth) / originalHeight;
        }

        return new Dimension(newWidth, newHeight);
    }


    /**
     * @param currentItem item
     * @param source      source input stream
     * @param verbose     verbose mode
     * @return InputStream the resulting input stream
     * @throws Exception if error
     */
    @Override
    public InputStream getDestinationStream(Item currentItem, InputStream source, boolean verbose)
        throws Exception {
        return getThumb(currentItem, source, verbose);
    }

    /**
     * Creates a thumbnail from the given source image stream.
     *
     * @param currentItem the current item
     * @param source      the source image input stream
     * @param verbose     verbose mode
     * @return InputStream the thumbnail as an input stream
     * @throws Exception if error
     */
    public InputStream getThumb(Item currentItem, InputStream source, boolean verbose)
        throws Exception {
        // get config params
        final ConfigurationService configurationService
            = DSpaceServicesFactory.getInstance().getConfigurationService();
        int xmax = configurationService
            .getIntProperty("thumbnail.maxwidth");
        int ymax = configurationService
            .getIntProperty("thumbnail.maxheight");
        boolean blurring = (boolean) configurationService
            .getBooleanProperty("thumbnail.blurring");
        boolean hqscaling = (boolean) configurationService
            .getBooleanProperty("thumbnail.hqscaling");

        return getThumb(currentItem, source, verbose, xmax, ymax, blurring, hqscaling, 0, 0, null);
    }

    /**
     * Reads an image, optionally using subsampling to reduce memory usage for large images.
     * Uses ImageReader directly instead of ImageIO.read() to control subsampling.
     *
     * <p>Subsampling is controlled by two configuration properties:
     * <ul>
     *   <li>{@code thumbnail.subsample.minpixels} - minimum total pixels (width * height) to trigger
     *       subsampling. Images smaller than this are read at full resolution. Default: 0 (disabled).</li>
     *   <li>{@code thumbnail.subsample.factor} - fixed subsampling factor (read every Nth pixel).
     *       If set to 0 or less, the factor is calculated automatically from the image and target
     *       dimensions. Default: 0 (auto).</li>
     * </ul>
     *
     * @param input     the input stream to read from (e.g. FileInputStream from temp file)
     * @param maxWidth  the target maximum width (used for auto factor calculation)
     * @param maxHeight the target maximum height (used for auto factor calculation)
     * @return the (possibly subsampled) BufferedImage
     * @throws IOException if reading fails
     */
    private BufferedImage readWithOptionalSubsampling(InputStream input, int maxWidth, int maxHeight)
        throws IOException {

        final ConfigurationService configurationService
            = DSpaceServicesFactory.getInstance().getConfigurationService();

        // Minimum image size (in total pixels) to activate subsampling; 0 = disabled
        long subsampleMinPixels = configurationService
            .getLongProperty("thumbnail.subsample.minpixels", 0);
        // Fixed subsampling factor; 0 = auto-calculate based on dimensions
        int configuredFactor = configurationService
            .getIntProperty("thumbnail.subsample.factor", 0);

        try (ImageInputStream iis = ImageIO.createImageInputStream(input)) {
            if (iis == null) {
                throw new IOException("Could not create ImageInputStream from source");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IOException("No ImageReader found for this image format");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int srcWidth = reader.getWidth(0);
                int srcHeight = reader.getHeight(0);
                long totalPixels = (long) srcWidth * srcHeight;

                int subsample = 1;
                if (subsampleMinPixels > 0 && totalPixels >= subsampleMinPixels) {
                    if (configuredFactor > 1) {
                        subsample = configuredFactor;
                    } else {
                        // Auto-calculate: read only enough pixels to produce ~2x the target size
                        int subsampleX = Math.max(1, srcWidth / (maxWidth * 2));
                        int subsampleY = Math.max(1, srcHeight / (maxHeight * 2));
                        subsample = Math.max(subsampleX, subsampleY);
                    }
                    log.info("Subsampling large image ({}x{}, {} pixels) with factor {}",
                             srcWidth, srcHeight, totalPixels, subsample);
                }

                ImageReadParam param = reader.getDefaultReadParam();
                if (subsample > 1) {
                    param.setSourceSubsampling(subsample, subsample, 0, 0);
                }

                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Creates a thumbnail with the specified parameters.
     * Preserves the original temp-file approach so the stream can be read twice:
     * once for EXIF rotation metadata, once for actual image decoding.
     *
     * @param currentItem    the current item
     * @param source         the source image input stream
     * @param verbose        verbose mode
     * @param xmax           max width
     * @param ymax           max height
     * @param blurring       whether to apply blur
     * @param hqscaling      whether to use high-quality scaling
     * @param brandHeight    brand bar height (0 for none)
     * @param brandFontPoint brand font size
     * @param brandFont      brand font name
     * @return InputStream the thumbnail
     * @throws Exception if error
     */
    protected InputStream getThumb(
        Item currentItem,
        InputStream source,
        boolean verbose,
        int xmax,
        int ymax,
        boolean blurring,
        boolean hqscaling,
        int brandHeight,
        int brandFontPoint,
        String brandFont
    ) throws Exception {

        // Write source to a temp file so we can read it twice
        // (once for rotation metadata, once for image data)
        File tempFile = File.createTempFile("temp", ".tmp");

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = source.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        }

        // First pass: read EXIF rotation metadata
        int rotation = 0;
        try (FileInputStream fis = new FileInputStream(tempFile)) {
            rotation = getImageRotationUsingImageReader(fis);
        }

        // Second pass: read image data (with optional subsampling for large images)
        BufferedImage buf;
        try (FileInputStream fis = new FileInputStream(tempFile)) {
            buf = readWithOptionalSubsampling(fis, xmax, ymax);
        } finally {
            if (!tempFile.delete()) {
                log.warn("Could not delete temp file: {}", tempFile.getAbsolutePath());
            }
        }

        if (buf == null) {
            throw new IOException("Could not read image from source");
        }

        return getThumbDim(
            currentItem, buf, verbose, xmax, ymax, blurring, hqscaling, brandHeight, brandFontPoint, rotation,
            brandFont
        );
    }

    /**
     * Creates a thumbnail from an already-loaded BufferedImage.
     *
     * @param currentItem the current item
     * @param buf         the source image
     * @param verbose     verbose mode
     * @return InputStream the thumbnail
     * @throws Exception if error
     */
    public InputStream getThumb(Item currentItem, BufferedImage buf, boolean verbose)
        throws Exception {
        // get config params
        final ConfigurationService configurationService
                = DSpaceServicesFactory.getInstance().getConfigurationService();
        int xmax = configurationService
            .getIntProperty("thumbnail.maxwidth");
        int ymax = configurationService
            .getIntProperty("thumbnail.maxheight");
        boolean blurring = (boolean) configurationService
            .getBooleanProperty("thumbnail.blurring");
        boolean hqscaling = (boolean) configurationService
            .getBooleanProperty("thumbnail.hqscaling");

        return getThumbDim(currentItem, buf, verbose, xmax, ymax, blurring, hqscaling, 0, 0, 0, null);
    }

    /**
     * Core thumbnail generation from a BufferedImage with all options.
     *
     * @param currentItem    the current item
     * @param buf            the source image
     * @param verbose        verbose mode
     * @param xmax           max width
     * @param ymax           max height
     * @param blurring       whether to apply blur
     * @param hqscaling      whether to use high-quality scaling
     * @param brandHeight    brand bar height
     * @param brandFontPoint brand font size
     * @param rotation       rotation angle in degrees
     * @param brandFont      brand font name
     * @return InputStream the thumbnail
     * @throws Exception if error
     */
    public InputStream getThumbDim(Item currentItem, BufferedImage buf, boolean verbose, int xmax, int ymax,
                                   boolean blurring, boolean hqscaling, int brandHeight, int brandFontPoint,
                                   int rotation, String brandFont)
        throws Exception {

        // Rotate the image if needed
        BufferedImage correctedImage = rotateImage(buf, rotation);

        int xsize = correctedImage.getWidth();
        int ysize = correctedImage.getHeight();

        // if verbose flag is set, print out dimensions
        // to STDOUT
        if (verbose) {
            System.out.println("original size: " + xsize + "," + ysize);
        }

        // Calculate new dimensions while maintaining aspect ratio
        Dimension newDimension = getScaledDimension(
            new Dimension(xsize, ysize),
            new Dimension(xmax, ymax)
        );


        // if verbose flag is set, print details to STDOUT
        if (verbose) {
            System.out.println("size after fitting to maximum height: " + newDimension.width + ", "
                                   + newDimension.height);
        }

        xsize = newDimension.width;
        ysize = newDimension.height;

        // create an image buffer for the thumbnail with the new xsize, ysize
        BufferedImage thumbnail = new BufferedImage(xsize, ysize, BufferedImage.TYPE_INT_RGB);

        // Use blurring if selected in config.
        // a little blur before scaling does wonders for keeping moire in check.
        if (blurring) {
            // send the buffered image off to get blurred.
            correctedImage = getBlurredInstance(correctedImage);
        }

        // Use high quality scaling method if selected in config.
        // this has a definite performance penalty.
        if (hqscaling) {
            // send the buffered image off to get an HQ downscale.
            correctedImage = getScaledInstance(correctedImage, xsize, ysize,
                                               RenderingHints.VALUE_INTERPOLATION_BICUBIC, true);
        }

        // now render the image into the thumbnail buffer
        Graphics2D g2d = thumbnail.createGraphics();
        try {
            g2d.drawImage(correctedImage, 0, 0, xsize, ysize, null);

            if (brandHeight != 0) {
                ConfigurationService configurationService
                        = DSpaceServicesFactory.getInstance().getConfigurationService();
                Brand brand = new Brand(xsize, brandHeight, new Font(brandFont, Font.PLAIN, brandFontPoint), 5);
                BufferedImage brandImage = brand.create(configurationService.getProperty("webui.preview.brand"),
                                                        configurationService.getProperty("webui.preview.brand.abbrev"),
                                                        currentItem == null ? "" : "hdl:" + currentItem.getHandle());

                g2d.drawImage(brandImage, 0, ysize, xsize, 20, null);
            }

            ByteArrayInputStream bais;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(thumbnail, "jpeg", baos);
                bais = new ByteArrayInputStream(baos.toByteArray());
            }

            return bais;
        } finally {
            g2d.dispose();
        }
    }


    @Override
    public String[] getInputMIMETypes() {
        return ImageIO.getReaderMIMETypes();
    }

    @Override
    public String[] getInputDescriptions() {
        return null;
    }

    @Override
    public String[] getInputExtensions() {
        // Temporarily disabled as JDK 1.6 only
        // return ImageIO.getReaderFileSuffixes();
        return null;
    }

    /**
     * Creates a normalized instance of the given image.
     *
     * @param buf the source image
     * @return normalized BufferedImage
     */
    public BufferedImage getNormalizedInstance(BufferedImage buf) {
        int type = (buf.getTransparency() == Transparency.OPAQUE) ?
            BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB_PRE;
        int w = buf.getWidth();
        int h = buf.getHeight();
        BufferedImage normal = new BufferedImage(w, h, type);
        Graphics2D g2d = normal.createGraphics();
        g2d.drawImage(buf, 0, 0, w, h, Color.WHITE, null);
        g2d.dispose();
        return normal;
    }

    /**
     * Convenience method that returns a blurred instance of the
     * provided {@code BufferedImage}.
     *
     * @param buf buffered image
     * @return updated BufferedImage
     */
    public BufferedImage getBlurredInstance(BufferedImage buf) {
        buf = getNormalizedInstance(buf);

        // kernel for blur op
        float[] matrix = {
            0.111f, 0.111f, 0.111f,
            0.111f, 0.111f, 0.111f,
            0.111f, 0.111f, 0.111f,
        };

        // perform the blur and return the blurred version.
        BufferedImageOp blur = new ConvolveOp(new Kernel(3, 3, matrix));
        BufferedImage blurbuf = blur.filter(buf, null);
        return blurbuf;
    }

    /**
     * Convenience method that returns a scaled instance of the
     * provided {@code BufferedImage}.
     *
     * @param buf           the original image to be scaled
     * @param targetWidth   the desired width of the scaled instance,
     *                      in pixels
     * @param targetHeight  the desired height of the scaled instance,
     *                      in pixels
     * @param hint          one of the rendering hints that corresponds to
     *                      {@code RenderingHints.KEY_INTERPOLATION} (e.g.
     *                      {@code RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR},
     *                      {@code RenderingHints.VALUE_INTERPOLATION_BILINEAR},
     *                      {@code RenderingHints.VALUE_INTERPOLATION_BICUBIC})
     * @param higherQuality if true, this method will use a multi-step
     *                      scaling technique that provides higher quality than the usual
     *                      one-step technique (only useful in downscaling cases, where
     *                      {@code targetWidth} or {@code targetHeight} is
     *                      smaller than the original dimensions, and generally only when
     *                      the {@code BILINEAR} hint is specified)
     * @return a scaled version of the original {@code BufferedImage}
     */
    public BufferedImage getScaledInstance(BufferedImage buf,
                                           int targetWidth,
                                           int targetHeight,
                                           Object hint,
                                           boolean higherQuality) {
        int type = (buf.getTransparency() == Transparency.OPAQUE) ?
            BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage scalebuf = (BufferedImage) buf;
        int w;
        int h;
        if (higherQuality) {
            // Use multi-step technique: start with original size, then
            // scale down in multiple passes with drawImage()
            // until the target size is reached
            w = buf.getWidth();
            h = buf.getHeight();
        } else {
            // Use one-step technique: scale directly from original
            // size to target size with a single drawImage() call
            w = targetWidth;
            h = targetHeight;
        }

        do {
            if (higherQuality && w > targetWidth) {
                w /= 2;
                if (w < targetWidth) {
                    w = targetWidth;
                }
            }

            if (higherQuality && h > targetHeight) {
                h /= 2;
                if (h < targetHeight) {
                    h = targetHeight;
                }
            }

            BufferedImage tmp = new BufferedImage(w, h, type);
            Graphics2D g2d = tmp.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
            g2d.drawImage(scalebuf, 0, 0, w, h, Color.WHITE, null);
            g2d.dispose();

            scalebuf = tmp;
        } while (w != targetWidth || h != targetHeight);

        return scalebuf;
    }
}
