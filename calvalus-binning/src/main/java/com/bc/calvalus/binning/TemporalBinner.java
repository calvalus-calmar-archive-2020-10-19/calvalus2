package com.bc.calvalus.binning;

import java.util.List;

/**
 * Processes a spatial slice of bin cells.
 * Called by {@link SpatialBinner}.
 * @param <BIN> The bin type.
 */
public class TemporalBinner<BIN extends Bin> implements BinConsumer<BIN> {
    private final BinStore<BIN> store;

    public TemporalBinner(BinStore<BIN> store) {
        this.store = store;
    }

    @Override
    public void consumeSlice(int sliceIndex, List<BIN> sliceBins) {
        for (BIN spatialBin : sliceBins) {
            BIN temporalBin = store.getBin(spatialBin.getIndex());
            temporalBin.addBin(spatialBin);
            store.putBin(temporalBin);
        }
    }
}
