package com.bc.calvalus.binning;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An implementation of the {@link com.bc.calvalus.binning.SpatialBinProcessor} interface that performs a temporal binning.
 */
class MySpatialBinProcessor implements SpatialBinProcessor {
    private final BinManager binManager;
    final Map<Long, TemporalBin> binMap ;

    MySpatialBinProcessor(BinManager binManager) {
        this.binManager = binManager;
        this.binMap = new HashMap<Long, TemporalBin>();
    }

    @Override
    public void processSpatialBinSlice(BinningContext ctx, List<SpatialBin> sliceBins) {
        for (SpatialBin spatialBin : sliceBins) {
            TemporalBin temporalBin = binMap.get(spatialBin.getIndex());
            if (temporalBin == null) {
                temporalBin = binManager.createTemporalBin(spatialBin.getIndex());
            }
            ctx.getBinManager().aggregateTemporalBin(spatialBin, temporalBin);
            binMap.put(temporalBin.getIndex(), temporalBin);
        }
    }
}