package com.bc.calvalus.binning;

import java.util.Arrays;

import static com.bc.calvalus.binning.AggregatorAverage.getWeightFn;
import static java.lang.Math.exp;
import static java.lang.Math.sqrt;

/**
 * An aggregator that computes a maximum-likelihood average.
 */
public class AggregatorAverageML implements Aggregator {

    private final int varIndex;
    private final String[] spatialPropertyNames;
    private final String[] temporalPropertyNames;
    private final String[] outputPropertyNames;
    private final AggregatorAverage.WeightFn weightFn;
    private final double fillValue;

    public AggregatorAverageML(VariableContext ctx, String varName, Double weightCoeff, Double fillValue) {
        this.varIndex = ctx.getVariableIndex(varName);
        this.spatialPropertyNames = new String[]{varName + "_sum_x", varName + "_sum_xx"};
        this.temporalPropertyNames = new String[]{varName + "_sum_x", varName + "_sum_xx", varName + "_sum_w"};
        this.outputPropertyNames = new String[]{varName + "_mean", varName + "_sigma", varName + "_median", varName + "_mode"};
        this.weightFn = getWeightFn(weightCoeff != null ? weightCoeff : 0.5);
        this.fillValue = fillValue != null ? fillValue : Double.NaN;
    }

    @Override
    public String getName() {
        return "AVG_ML";
    }

    @Override
    public int getSpatialPropertyCount() {
        return 2;
    }

    @Override
    public String getSpatialPropertyName(int i) {
        return spatialPropertyNames[i];
    }

    @Override
    public int getTemporalPropertyCount() {
        return 3;
    }

    @Override
    public String getTemporalPropertyName(int i) {
        return temporalPropertyNames[i];
    }

    @Override
    public int getOutputPropertyCount() {
        return 4;
    }

    @Override
    public String getOutputPropertyName(int i) {
        return outputPropertyNames[i];
    }

    @Override
    public double getOutputPropertyFillValue(int i) {
        return fillValue;
    }

    @Override
    public void initSpatial(BinContext ctx, WritableVector vector) {
        vector.set(0, 0.0f);
        vector.set(1, 0.0f);
    }

    @Override
    public void initTemporal(BinContext ctx, WritableVector vector) {
        vector.set(0, 0.0f);
        vector.set(1, 0.0f);
        vector.set(2, 0.0f);
    }

    @Override
    public void aggregateSpatial(BinContext ctx, Vector observationVector, WritableVector spatialVector) {
        final float value = (float) Math.log(observationVector.get(varIndex));
        spatialVector.set(0, spatialVector.get(0) + value);
        spatialVector.set(1, spatialVector.get(1) + value * value);
    }

    @Override
    public void completeSpatial(BinContext ctx, int numObs, WritableVector numSpatialObs) {
        final float w = weightFn.eval(numObs);
        numSpatialObs.set(0, numSpatialObs.get(0) / w);
        numSpatialObs.set(1, numSpatialObs.get(1) / w);
    }

    @Override
    public void aggregateTemporal(BinContext ctx, Vector spatialVector, int numSpatialObs, WritableVector temporalVector) {
        temporalVector.set(0, temporalVector.get(0) + spatialVector.get(0));  // sumX
        temporalVector.set(1, temporalVector.get(1) + spatialVector.get(1));  // sumXX
        temporalVector.set(2, temporalVector.get(2) + weightFn.eval(numSpatialObs)); // sumW
    }

    @Override
    public void completeTemporal(BinContext ctx, int numTemporalObs, WritableVector temporalVector) {
    }

    @Override
    public void computeOutput(Vector temporalVector, WritableVector outputVector) {
        final float sumX = temporalVector.get(0);
        final float sumXX = temporalVector.get(1);
        final float sumW = temporalVector.get(2);
        final float avLogs = sumX / sumW;
        final float vrLogs = sumXX / sumW - avLogs * avLogs;
        final float mean = (float) exp(avLogs + 0.5 * vrLogs);
        final float sigma = (float) (mean * sqrt(exp(vrLogs) - 1.0));
        final float median = (float) exp(avLogs);
        final float mode = (float) exp(avLogs - vrLogs);
        outputVector.set(0, mean);
        outputVector.set(1, sigma);
        outputVector.set(2, median);
        outputVector.set(3, mode);
    }

    @Override
    public String toString() {
        return "AggregatorAverageML{" +
                "varIndex=" + varIndex +
                ", outputPropertyNames=" + (outputPropertyNames == null ? null : Arrays.toString(outputPropertyNames)) +
                ", weightFn=" + weightFn +
                '}';
    }
}
