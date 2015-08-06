package com.bc.calvalus.processing.ma;

import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.FlagCoding;
import org.esa.snap.framework.datamodel.GeoPos;
import org.esa.snap.framework.datamodel.Mask;
import org.esa.snap.framework.datamodel.PixelPos;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.ProductData;
import org.esa.snap.framework.datamodel.TiePointGrid;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

/**
 * Generates an output record from a {@link Product} using an input reference record.
 *
 * @author MarcoZ
 * @author Norman
 */
public class PixelExtractor {

    public static final String GOOD_PIXEL_MASK_NAME = "_good_pixel";
    public static final String ATTRIB_NAME_AGGREG_PREFIX = "*";
    public static final String EXCLUSION_REASON_ALL_MASKED = "PIXEL_EXPRESSION";

    private final Header header;
    private final Product product;
    private final AffineTransform i2oTransform;
    private final AffineTransform o2iTransform;
    private final Mask pixelMask;
    private final int macroPixelSize;
    private final boolean onlyExtractComplete;
    private final boolean copyInput;

    public PixelExtractor(Header inputHeader,
                          Product product,
                          int macroPixelSize,
                          boolean onlyExtractComplete,
                          String goodPixelMaskExpression,
                          boolean copyInput,
                          AffineTransform i2oTransform) {
        this.product = product;
        this.i2oTransform = i2oTransform;
        try {
            this.o2iTransform = i2oTransform.createInverse();
        } catch (NoninvertibleTransformException e) {
            throw new IllegalArgumentException("failed to invert i2oTransform: " + i2oTransform, e);
        }
        if (goodPixelMaskExpression != null && !goodPixelMaskExpression.trim().isEmpty()) {
            this.pixelMask = createGoodPixelMask(product, goodPixelMaskExpression);
        } else {
            this.pixelMask = null;
        }
        this.macroPixelSize = macroPixelSize;
        this.onlyExtractComplete = onlyExtractComplete;
        this.copyInput = copyInput;

        // Important note: createHeader() is dependent on a number of field values,
        // so we call it at last
        this.header = createHeader(inputHeader);
    }

    /**
     * @return The header corresponding to the records that are generated by this extractor.
     */
    public Header getHeader() {
        return header;
    }

    /**
     * Extracts an output record.
     *
     * @param inputRecord      The input record.
     * @param originalPixelPos The validated original (Level 1) pixel pos.
     * @return The output record or {@code null}, if a certain inclusion criterion is not met.
     * @throws java.io.IOException If an I/O error occurs
     */
    public Record extract(Record inputRecord, PixelPos originalPixelPos, Date originalPixelTime) throws IOException {
        Point2D extractionPos = i2oTransform.transform(originalPixelPos, null);
        PixelPos extractionPixelPos = new PixelPos((float) extractionPos.getX(), (float) extractionPos.getY());
        System.out.println("PixelExtractor.extract: originalPixelPos = " + originalPixelPos + "extractionPixelPos = " + extractionPixelPos);

        Rectangle productRect = new Rectangle(product.getSceneRasterWidth(), product.getSceneRasterHeight());

        if (!product.containsPixel(extractionPixelPos)) {
            System.out.println("pixel pos not in product");
            return null;
        }

        final Rectangle macroPixelRect = productRect.intersection(
                new Rectangle((int) extractionPixelPos.x - macroPixelSize / 2,
                              (int) extractionPixelPos.y - macroPixelSize / 2,
                              macroPixelSize, macroPixelSize));

        if (macroPixelRect.isEmpty()) {
            System.out.println("macro pixel rect is empty");
            return null;
        }
        if (onlyExtractComplete && (macroPixelRect.width < macroPixelSize || macroPixelRect.height < macroPixelSize)) {
            System.out.println("macro pixel is not complete");
            return null;
        }
        int x0 = macroPixelRect.x;
        int y0 = macroPixelRect.y;
        int width = macroPixelRect.width;
        int height = macroPixelRect.height;

        final int[] maskSamples;
        String exclusionReason = "";
        if (pixelMask != null) {
            maskSamples = new int[width * height];
            pixelMask.readPixels(x0, y0, width, height, maskSamples);
            boolean allBad = true;
            for (int i = 0; i < maskSamples.length; i++) {
                int sample = maskSamples[i];
                if (sample != 0) {
                    maskSamples[i] = 1;
                    allBad = false;
                }
            }
            if (allBad) {
                exclusionReason = EXCLUSION_REASON_ALL_MASKED;
            }
        } else {
            maskSamples = null;
        }

        final Object[] values = new Object[header.getAttributeNames().length];

        int index = 0;
        if (copyInput) {
            Object[] inputValues = inputRecord.getAttributeValues();
            System.arraycopy(inputValues, 0, values, 0, inputValues.length);
            index = inputValues.length;
        }

        final int[] pixelXPositions = new int[width * height];
        final int[] pixelYPositions = new int[width * height];
        final float[] pixelLatitudes = new float[width * height];
        final float[] pixelLongitudes = new float[width * height];

        for (int i = 0, y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++, i++) {
                PixelPos pp = new PixelPos(x + 0.5F, y + 0.5F);
                GeoPos gp = product.getGeoCoding().getGeoPos(pp, null);
                Point2D originalCoordinates = o2iTransform.transform(pp, null);
                pixelXPositions[i] = (int) originalCoordinates.getX();
                pixelYPositions[i] = (int) originalCoordinates.getY();
                pixelLatitudes[i] = (float) gp.lat;
                pixelLongitudes[i] = (float) gp.lon;
            }
        }

        // check for X/Y flip
        boolean flipX = false;
        boolean flipY = false;
        if (macroPixelSize > 1) {
            // for flipY, compare two pixels in a row
            if (pixelXPositions[0] > pixelXPositions[1]) {
                flipY = true;
            }
            // for flipX, compare two pixels in a col
            if (pixelYPositions[0] > pixelYPositions[width]) {
                flipX = true;
            }
        }
        ////////////////////////////////////
        // 1. derived information
        //
        // field "source_name"
        values[index++] = product.getName();
        // field "pixel_time"
        values[index++] = originalPixelTime;
        // field "pixel_x"
        values[index++] = flipIntArray(pixelXPositions, width, height, flipX, flipY);
        // field "pixel_y"
        values[index++] = flipIntArray(pixelYPositions, width, height, flipX, flipY);
        // field "pixel_lat"
        values[index++] = flipFloatArray(pixelLatitudes, width, height, flipX, flipY);
        // field "pixel_lon"
        values[index++] = flipFloatArray(pixelLongitudes, width, height, flipX, flipY);
        if (maskSamples != null) {
            // field "pixel_mask"
            values[index++] = flipIntArray(maskSamples, width, height, flipX, flipY);
        }

        ////////////////////////////////////
        // 2. + 3. bands and flags
        //
        final Band[] productBands = product.getBands();
        for (Band band : productBands) {
            if (!band.isFlagBand()) {
                if (band.isFloatingPointType()) {
                    final float[] floatSamples = new float[macroPixelRect.width * macroPixelRect.height];
                    band.readPixels(x0, y0, width, height, floatSamples);
                    maskNaN(band, x0, y0, width, height, floatSamples);
                    values[index++] = flipFloatArray(floatSamples, width, height, flipX, flipY);
                } else {
                    final int[] intSamples = new int[macroPixelRect.width * macroPixelRect.height];
                    band.readPixels(x0, y0, width, height, intSamples);
                    values[index++] = flipIntArray(intSamples, width, height, flipX, flipY);
                }
            }
        }

        ////////////////////////////////////
        // 4. tie-points
        //
        for (TiePointGrid tiePointGrid : product.getTiePointGrids()) {
            final float[] floatSamples = new float[macroPixelRect.width * macroPixelRect.height];
            tiePointGrid.readPixels(x0, y0, width, height, floatSamples);
            values[index++] = flipFloatArray(floatSamples, width, height, flipX, flipY);
        }

        return new DefaultRecord(inputRecord.getId(),
                                 inputRecord.getLocation(),
                                 inputRecord.getTime(),
                                 values,
                                 new Object[]{exclusionReason});
    }

    static int[] flipIntArray(int[] data, int w, int h, boolean flipX, boolean flipY) {
        if (flipY) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w / 2; x++) {
                    int frontIndex = x + y * w;
                    int backIndex = (y + 1) * w - (x + 1);
                    int temp = data[frontIndex]; //save from front
                    data[frontIndex] = data[backIndex]; //back to front
                    data[backIndex] = temp;//write to back
                }
            }
        }
        if (flipX) {
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h / 2; y++) {
                    int topIndex = x + y * w;
                    int bottomIndex = x + (h - y - 1) * w;
                    int temp = data[topIndex];
                    data[topIndex] = data[bottomIndex];
                    data[bottomIndex] = temp;
                }
            }
        }
        return data;
    }

    static float[] flipFloatArray(float[] data, int w, int h, boolean flipX, boolean flipY) {
        if (flipY) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w / 2; x++) {
                    int frontIndex = x + y * w;
                    int backIndex = (y + 1) * w - (x + 1);
                    float temp = data[frontIndex]; //save from front
                    data[frontIndex] = data[backIndex]; //back to front
                    data[backIndex] = temp;//write to back
                }
            }
        }
        if (flipX) {
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h / 2; y++) {
                    int topIndex = x + y * w;
                    int bottomIndex = x + (h - y - 1) * w;
                    float temp = data[topIndex];
                    data[topIndex] = data[bottomIndex];
                    data[bottomIndex] = temp;
                }
            }
        }
        return data;
    }

    private Header createHeader(Header inputHeader) {
        final java.util.List<String> attributeNames = new ArrayList<String>();

        if (copyInput) {
            Collections.addAll(attributeNames, inputHeader.getAttributeNames());
        }

        ////////////////////////////////////
        // 1. derived information
        //
        attributeNames.add(ProductRecordSource.SOURCE_NAME_ATT_NAME);
        attributeNames.add(ProductRecordSource.PIXEL_TIME_ATT_NAME);
        attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + ProductRecordSource.PIXEL_X_ATT_NAME);
        attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + ProductRecordSource.PIXEL_Y_ATT_NAME);
        attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + ProductRecordSource.PIXEL_LAT_ATT_NAME);
        attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + ProductRecordSource.PIXEL_LON_ATT_NAME);
        if (pixelMask != null) {
            attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + ProductRecordSource.PIXEL_MASK_ATT_NAME);
        }

        ////////////////////////////////////
        // 2. bands
        //
        Band[] productBands = product.getBands();
        for (Band band : productBands) {
            if (!band.isFlagBand()) {
                attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + band.getName());
            }
        }

        ////////////////////////////////////
        // 3. flags (virtual bands)
        //
        for (Band band : productBands) {
            if (band.isFlagBand()) {
                FlagCoding flagCoding = band.getFlagCoding();
                String[] flagNames = flagCoding.getFlagNames();
                for (String flagName : flagNames) {
                    attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + band.getName() + "." + flagName);
                    // Note: side-effect here, adding new band to product
                    product.addBand("flag_" + band.getName() + "_" + flagName, band.getName() + "." + flagName, ProductData.TYPE_INT8);
                }
            }
        }

        ////////////////////////////////////
        // 4. tie-points
        //
        String[] tiePointGridNames = product.getTiePointGridNames();
        for (String tiePointGridName : tiePointGridNames) {
            attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + tiePointGridName);
        }

        return new DefaultHeader(inputHeader.hasLocation(),
                                 inputHeader.hasTime(),
                                 attributeNames.toArray(new String[attributeNames.size()]));
    }

    private void maskNaN(Band band, int x0, int y0, int width, int height, float[] samples) {
        for (int i = 0, y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++, i++) {
                if (!band.isPixelValid(x, y)) {
                    samples[i] = Float.NaN;
                }
            }
        }
    }

    private static Mask createGoodPixelMask(Product product, String goodPixelExpression) {
        Mask mask = product.getMaskGroup().get(GOOD_PIXEL_MASK_NAME);
        if (mask != null) {
            product.getMaskGroup().remove(mask);
            mask.dispose();
        }

        int width = product.getSceneRasterWidth();
        int height = product.getSceneRasterHeight();

        mask = Mask.BandMathsType.create(GOOD_PIXEL_MASK_NAME,
                                         null,
                                         width,
                                         height,
                                         goodPixelExpression,
                                         Color.RED,
                                         0.5);

        // Note: side-effect here, adding new mask to product
        product.getMaskGroup().add(mask);

        return mask;
    }
}
