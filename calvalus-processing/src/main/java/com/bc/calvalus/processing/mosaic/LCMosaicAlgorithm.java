/*
 * Copyright (C) 2011 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package com.bc.calvalus.processing.mosaic;

import com.bc.calvalus.binning.VariableContext;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Arrays;

/**
 * The algorithm, for lc_cci..
 *
 * @author MarcoZ
 */
public class LCMosaicAlgorithm implements MosaicAlgorithm, Configurable {

    private static final int STATUS = 0;

    private static final int STATUS_LAND = 1;
    private static final int STATUS_WATER = 2;
    private static final int STATUS_SNOW = 3;
    private static final int STATUS_CLOUD = 4;
    private static final int STATUS_CLOUD_SHADOW = 5;
    private static final int STATUS_INVALID = 6;
    private static final int STATUS_CLOUD_TEMPORAL = 7;
    private static final String[] COUNTER_NAMES = {"land", "water", "snow", "cloud", "cloud_shadow", "cloud_temporal"};

    private static final int SDR_OFFSET = COUNTER_NAMES.length + 1;
    private static final int NUM_SDR_BANDS = 15;
    public static final String CALVALUS_LC_SDR8_MEAN = "calvalus.lc.sdr8mean";

    private int[] varIndexes;

    private float[][] aggregatedSamples = null;
    private String[] outputFeatures;
    private int tileSize;
    private int variableCount;

    private Configuration jobConf;
    private SequenceFile.Reader reader;
    private TileIndexWritable sdr8Key;
    private TileDataWritable sdr8Data;
    private float[][] sdr8DataSamples;


    @Override
    public void init(TileIndexWritable tileIndex) throws IOException {
        int numElems = tileSize * tileSize;
        aggregatedSamples = new float[variableCount][numElems];
        for (int band = 0; band < variableCount; band++) {
            Arrays.fill(aggregatedSamples[band], 0.0f);
        }
        if (reader == null && jobConf.get(CALVALUS_LC_SDR8_MEAN) != null) {
            openSdr8MeanReader(getPartition(tileIndex));
        }
        if (reader != null) {
            while (reader.next(sdr8Key)) {
                if (sdr8Key.equals(tileIndex)) {
                    reader.getCurrentValue(sdr8Data);
                    sdr8DataSamples = sdr8Data.getSamples();
                    break;
                }
            }
        }
    }

    private int getPartition(TileIndexWritable tileIndex) {
        MosaicPartitioner mosaicPartitioner = new MosaicPartitioner();
        mosaicPartitioner.setConf(jobConf);
        int numPartitions = jobConf.getInt("mapred.reduce.tasks", 1);
        return mosaicPartitioner.getPartition(tileIndex, null, numPartitions);
    }

    @Override
    public void process(float[][] samples) {
        int numElems = tileSize * tileSize;
        for (int i = 0; i < numElems; i++) {
            int status = (int) samples[varIndexes[0]][i];

            if (status == STATUS_LAND && sdr8DataSamples != null) {
                status = temporalCloudCheck(samples[varIndexes[8]][i], sdr8DataSamples[0][i]);
            }
            if (status == STATUS_LAND) {
                int landCount = (int) aggregatedSamples[STATUS_LAND][i];
                // If we haven't seen LAND so far, but we had SNOW or CLOUD clear SDRs
                if (landCount == 0) {
                    int otherCount = (int) aggregatedSamples[STATUS_SNOW][i] +
                            (int) aggregatedSamples[STATUS_CLOUD][i] +
                            (int) aggregatedSamples[STATUS_CLOUD_SHADOW][i] +
                            (int) aggregatedSamples[STATUS_CLOUD_TEMPORAL][i];
                    if (otherCount > 0) {
                        clearSDR(i, 0.0f);
                    }
                }
                // Since we have seen LAND now, accumulate LAND SDRs
                addSdrs(samples, i);
                // Count LAND
                aggregatedSamples[STATUS_LAND][i] = landCount + 1;
            } else if (status == STATUS_WATER) {
                // Count WATER, do not accumulate
                aggregatedSamples[STATUS_WATER][i]++;
            } else if (status == STATUS_SNOW) {
                int landCount = (int) aggregatedSamples[STATUS_LAND][i];
                // If we haven't seen LAND so far, accumulate SNOW SDRs
                if (landCount == 0) {
                    int otherCount = (int) aggregatedSamples[STATUS_CLOUD][i] +
                            (int) aggregatedSamples[STATUS_CLOUD_SHADOW][i] +
                            (int) aggregatedSamples[STATUS_CLOUD_TEMPORAL][i];
                    if (otherCount > 0) {
                        clearSDR(i, 0.0f);
                    }
                    addSdrs(samples, i);
                }
                // Count SNOW
                aggregatedSamples[STATUS_SNOW][i]++;
            } else if (status == STATUS_CLOUD || status == STATUS_CLOUD_SHADOW || status == STATUS_CLOUD_TEMPORAL) {
                // if we have nothing else count cloud spectra
                int landCount = (int) aggregatedSamples[STATUS_LAND][i];
                int snowCount = (int) aggregatedSamples[STATUS_SNOW][i];
                if (landCount == 0 && snowCount == 0) {
                    addSdrs(samples, i);
                }
                // Count CLOUD orCLOUD_SHADOW
                aggregatedSamples[status][i]++;
            }
        }
    }

    private int temporalCloudCheck(float sdr8, float sdr8CloudThreshold) {
        if (sdr8 > sdr8CloudThreshold) {
            // treat this as cloud
            return STATUS_CLOUD_TEMPORAL;
        } else {
            return STATUS_LAND;
        }
    }

    @Override
    public float[][] getResult() {
        int numElems = tileSize * tileSize;
        for (int i = 0; i < numElems; i++) {
            int status = calculateStatus(aggregatedSamples[STATUS_LAND][i],
                                         aggregatedSamples[STATUS_WATER][i],
                                         aggregatedSamples[STATUS_SNOW][i],
                                         aggregatedSamples[STATUS_CLOUD][i],
                                         aggregatedSamples[STATUS_CLOUD_SHADOW][i],
                                         aggregatedSamples[STATUS_CLOUD_TEMPORAL][i]);
            aggregatedSamples[STATUS][i] = status;
            float wSum = 0f;
            if (status == STATUS_LAND) {
                wSum = aggregatedSamples[STATUS_LAND][i];
            } else if (status == STATUS_WATER) {
                // do nothing
            } else if (status == STATUS_SNOW) {
                wSum = aggregatedSamples[STATUS_SNOW][i];
            } else if (status == STATUS_CLOUD || status == STATUS_CLOUD_SHADOW || status == STATUS_CLOUD_TEMPORAL) {
                wSum = aggregatedSamples[STATUS_CLOUD][i] +
                        aggregatedSamples[STATUS_CLOUD_SHADOW][i] +
                        aggregatedSamples[STATUS_CLOUD_TEMPORAL][i];
            }
            if (wSum != 0f) {
                for (int j = 0; j < NUM_SDR_BANDS + NUM_SDR_BANDS + 1; j++) {  // sdr + ndvi + sdr_error
                    aggregatedSamples[SDR_OFFSET + j][i] /= wSum;
                }
            } else {
                clearSDR(i, Float.NaN);
            }
        }
        return aggregatedSamples;
    }

    @Override
    public void setVariableContext(VariableContext variableContext) {
        varIndexes = createVariableIndexes(variableContext, NUM_SDR_BANDS);
        outputFeatures = createOutputFeatureNames(NUM_SDR_BANDS);
        variableCount = outputFeatures.length;
        tileSize = new MosaicGrid().getTileSize();
    }

    @Override
    public String[] getOutputFeatures() {
        return outputFeatures;
    }

    @Override
    public void setConf(Configuration jobConf) {
        this.jobConf = jobConf;
    }

    private void openSdr8MeanReader(int partition) {
        String sdr8MeanDir = jobConf.get(CALVALUS_LC_SDR8_MEAN);
        NumberFormat NUMBER_FORMAT = NumberFormat.getInstance();
        NUMBER_FORMAT.setMinimumIntegerDigits(5);
        NUMBER_FORMAT.setGroupingUsed(false);

        String fileName = "part-r-" + NUMBER_FORMAT.format(partition);
        Path path = new Path(sdr8MeanDir, fileName);
        try {
            FileSystem fs = path.getFileSystem(jobConf);
            reader = new SequenceFile.Reader(fs, path, jobConf);
            sdr8Key = new TileIndexWritable();
            sdr8Data = new TileDataWritable();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Configuration getConf() {
        return jobConf;
    }

    private void clearSDR(int i, float value) {
        for (int j = 0; j < NUM_SDR_BANDS + NUM_SDR_BANDS + 1; j++) {
            aggregatedSamples[SDR_OFFSET + j][i] = value;
        }
    }

    private void addSdrs(float[][] samples, int i) {
        final int sdrObservationOffset = 1; // status
        int sdrOffset = SDR_OFFSET;
        for (int j = 0; j < NUM_SDR_BANDS + 1; j++) { // sdr + ndvi
            aggregatedSamples[sdrOffset + j][i] += samples[varIndexes[sdrObservationOffset + j]][i];
        }
        sdrOffset += NUM_SDR_BANDS + 1;
        for (int j = 0; j < NUM_SDR_BANDS; j++) { // sdr_error
            final int varIndex = varIndexes[sdrObservationOffset + NUM_SDR_BANDS + 1 + j];
            float sdrErrorMeasurement = samples[varIndex][i];
            aggregatedSamples[sdrOffset + j][i] += (sdrErrorMeasurement * sdrErrorMeasurement);
        }
    }

    static int calculateStatus(float land, float water, float snow, float cloud, float cloudShadow, float cloudTemp) {
        if (land > 0) {
            return STATUS_LAND;
        } else if (water > 0 || snow > 0) {
            if (water > snow) {
                return STATUS_WATER;
            } else {
                return STATUS_SNOW;
            }
        } else if (cloud > 0 || cloudShadow > 0 || cloudTemp > 0) {
            if (cloud > cloudShadow || cloudTemp > cloudShadow) {
                if (cloud > cloudTemp) {
                    return STATUS_CLOUD;
                } else {
                    return STATUS_CLOUD_TEMPORAL;
                }
            } else {
                return STATUS_CLOUD_SHADOW;
            }
        } else {
            return STATUS_INVALID;
        }
    }

    private static int[] createVariableIndexes(VariableContext varCtx, int numBands) {
        int[] varIndexes = new int[3 + numBands + 1 + numBands];
        int j = 0;
        varIndexes[j++] = getVariableIndex(varCtx, "status");
        for (int i = 0; i < numBands; i++) {
            varIndexes[j++] = getVariableIndex(varCtx, "sdr_" + (i + 1));
        }
        varIndexes[j++] = getVariableIndex(varCtx, "ndvi");
        for (int i = 0; i < numBands; i++) {
            varIndexes[j++] = getVariableIndex(varCtx, "sdr_error_" + (i + 1));
        }
        return varIndexes;
    }

    private static int getVariableIndex(VariableContext varCtx, String varName) {
        int varIndex = varCtx.getVariableIndex(varName);
        if (varIndex < 0) {
            throw new IllegalArgumentException(String.format("varIndex < 0 for varName='%s'", varName));
        }
        return varIndex;
    }

    private static String[] createOutputFeatureNames(int numBands) {
        String[] featureNames = new String[2 + COUNTER_NAMES.length + (numBands * 2)];
        int j = 0;
        featureNames[j++] = "status";
        for (String counter : COUNTER_NAMES) {
            featureNames[j++] = counter + "_count";
        }
        for (int i = 0; i < numBands; i++) {
            featureNames[j++] = "sr_" + (i + 1) + "_mean";
        }
        featureNames[j++] = "ndvi_mean";
        for (int i = 0; i < numBands; i++) {
            featureNames[j++] = "sr_" + (i + 1) + "_sigma";
        }
        return featureNames;
    }
}
