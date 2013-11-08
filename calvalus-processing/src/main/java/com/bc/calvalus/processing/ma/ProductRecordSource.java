package com.bc.calvalus.processing.ma;

import com.bc.ceres.core.Assert;
import com.bc.jexp.ParseException;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * A special record source that generates its output records from a {@link Product} and an input record source.
 *
 * @author MarcoZ
 * @author Norman
 */
public class ProductRecordSource implements RecordSource {

    public static final String SOURCE_NAME_ATT_NAME = "source_name";

    public static final String PIXEL_X_ATT_NAME = "pixel_x";
    public static final String PIXEL_Y_ATT_NAME = "pixel_y";
    public static final String PIXEL_LAT_ATT_NAME = "pixel_lat";
    public static final String PIXEL_LON_ATT_NAME = "pixel_lon";
    public static final String PIXEL_TIME_ATT_NAME = "pixel_time";
    public static final String PIXEL_MASK_ATT_NAME = "pixel_mask";

    private final RecordSource input;
    private final boolean empty;
    private final PixelExtractor pixelExtractor;

    public ProductRecordSource(Product product, RecordSource input, MAConfig config) {

        Assert.notNull(product, "product");
        Assert.notNull(input, "input");
        Assert.notNull(config, "config");
        if (!input.getHeader().hasLocation()) {
            throw new IllegalArgumentException("Input records don't have locations.");
        }

        this.input = input;
        this.empty = shallApplyTimeCriterion(config) && !canApplyTimeCriterion(input);

        pixelExtractor = new PixelExtractor(input.getHeader(),
                                            product,
                                            config.getMacroPixelSize(),
                                            config.getGoodPixelExpression(),
                                            config.getMaxTimeDifference(),
                                            config.getCopyInput());
    }

    @Override
    public Header getHeader() {
        return pixelExtractor.getHeader();
    }

    /**
     * Extracts output records from input records.
     *
     * @return Extracted records.
     */
    @Override
    public Iterable<Record> getRecords() throws Exception {
        if (empty) {
            return Collections.emptyList();
        }

        final Iterable<Record> inputRecords = input.getRecords();
        return new Iterable<Record>() {
            @Override
            public Iterator<Record> iterator() {
                return new PixelPosRecordGenerator(getInputRecordsSortedByPixelYX(inputRecords, pixelExtractor).iterator());
            }
        };
    }

    @Override
    public String getTimeAndLocationColumnDescription() {
        return "BEAM product format";
    }

    public static RecordTransformer createShiftTransformer(Header header, Rectangle inputRect) {
        List<String> attributeNames = Arrays.asList(header.getAttributeNames());
        final int xAttributeIndex = attributeNames.indexOf(PixelExtractor.ATTRIB_NAME_AGGREG_PREFIX + PIXEL_X_ATT_NAME);
        final int yAttributeIndex = attributeNames.indexOf(PixelExtractor.ATTRIB_NAME_AGGREG_PREFIX + PIXEL_Y_ATT_NAME);
        int xOffset = 0;
        int yOffset = 0;
        if (inputRect != null) {
            xOffset = inputRect.x;
            yOffset = inputRect.y;
        }
        return new ProductOffsetTransformer(xAttributeIndex, yAttributeIndex, xOffset, yOffset);
    }

    public static RecordTransformer createAggregator(Header header, MAConfig config) {
        String pixelMaskAttributeName = PixelExtractor.ATTRIB_NAME_AGGREG_PREFIX + PIXEL_MASK_ATT_NAME;
        final int pixelMaskAttributeIndex = Arrays.asList(header.getAttributeNames()).indexOf(pixelMaskAttributeName);
        return new RecordAggregator(pixelMaskAttributeIndex, config.getFilteredMeanCoeff());
    }

    public static RecordFilter createRecordFilter(Header header, MAConfig config) {
        if (shallApplyGoodRecordExpression(config)) {
            final String goodRecordExpression = config.getGoodRecordExpression();
            final RecordFilter recordFilter;
            try {
                recordFilter = ExpressionRecordFilter.create(header, goodRecordExpression);
            } catch (ParseException e) {
                throw new IllegalStateException("Illegal configuration: goodRecordExpression is invalid: " + e.getMessage(), e);
            }
            return recordFilter;
        } else {
            return new RecordFilter() {
                @Override
                public boolean accept(Record record) {
                    return true;
                }
            };
        }
    }

    private static boolean shallApplyGoodRecordExpression(MAConfig config) {
        String goodRecordExpression = config.getGoodRecordExpression();
        return goodRecordExpression != null && !goodRecordExpression.isEmpty();
    }

    private static boolean shallApplyTimeCriterion(MAConfig config) {
        Double maxTimeDifference = config.getMaxTimeDifference();
        return maxTimeDifference != null && maxTimeDifference > 0;
    }

    private static boolean canApplyTimeCriterion(RecordSource input) {
        return input.getHeader().hasTime();
    }

    private Iterable<PixelPosRecord> getInputRecordsSortedByPixelYX(Iterable<Record> inputRecords, PixelExtractor pixelExtractor) {
        ArrayList<PixelPosRecord> pixelPosList = new ArrayList<PixelPosRecord>(128);
        for (Record inputRecord : inputRecords) {
            final PixelPos pixelPos = pixelExtractor.getPixelPos(inputRecord);
            if (pixelPos != null) {
                pixelPosList.add(new PixelPosRecord(pixelPos, inputRecord));
            }
        }
        PixelPosRecord[] records = pixelPosList.toArray(new PixelPosRecord[pixelPosList.size()]);
        Arrays.sort(records, new YXComparator());
        return Arrays.asList(records);
    }

    private static class YXComparator implements Comparator<PixelPosRecord> {

        @Override
        public int compare(PixelPosRecord o1, PixelPosRecord o2) {
            float y1 = o1.pixelPos.y;
            float y2 = o2.pixelPos.y;
            if (y1 < y2) {
                return -2;
            } else if (y1 > y2) {
                return 2;
            }
            float x1 = o1.pixelPos.x;
            float x2 = o2.pixelPos.x;
            if (x1 < x2) {
                return -1;
            } else if (x1 > x2) {
                return 1;
            }
            return 0;
        }
    }

    private abstract class OutputRecordGenerator<T> extends RecordIterator {

        private final Iterator<T> inputIterator;

        public OutputRecordGenerator(Iterator<T> inputIterator) {
            this.inputIterator = inputIterator;
        }

        @Override
        public Record getNextRecord() {
            while (inputIterator.hasNext()) {
                T input = inputIterator.next();
                try {
                    Record next = getRecord(input);
                    if (next != null) {
                        return next;
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to get output record for input " + input, e);
                }
            }
            return null;
        }

        protected abstract Record getRecord(T input) throws IOException;
    }

    private class PixelPosRecordGenerator extends OutputRecordGenerator<PixelPosRecord> {

        public PixelPosRecordGenerator(Iterator<PixelPosRecord> inputIterator) {
            super(inputIterator);
        }

        @Override
        protected Record getRecord(PixelPosRecord input) throws IOException {
            return pixelExtractor.extract(input.record, input.pixelPos);
        }
    }

    private static class PixelPosRecord {

        private final PixelPos pixelPos;
        private final Record record;

        private PixelPosRecord(PixelPos pixelPos, Record record) {
            this.pixelPos = pixelPos;
            this.record = record;
        }

        @Override
        public String toString() {
            return "PixelPosRecord{" +
                   "pixelPos=" + pixelPos +
                   ", record=" + record +
                   '}';
        }
    }

}
