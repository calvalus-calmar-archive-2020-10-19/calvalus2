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

import com.bc.calvalus.processing.l3.L3Config;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ReflectionUtils;

/**
 * Utility methods for mosaic processing.
 *
 * @author MarcoZ
 */
public class MosaicUtils {

    public static MosaicAlgorithm createAlgorithm(Configuration jobConf) {
        L3Config l3Config = L3Config.get(jobConf);
        L3Config.AggregatorConfiguration[] aggregators = l3Config.getAggregators();
        MosaicAlgorithm mosaicAlgorithm = null;
        if (aggregators != null) {
            L3Config.AggregatorConfiguration first = aggregators[0];
            String type = first.getType();
            try {
                Class<?> algorithClass = Class.forName(type);
                mosaicAlgorithm = (MosaicAlgorithm) ReflectionUtils.newInstance(algorithClass, jobConf);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        if (mosaicAlgorithm == null) {
            mosaicAlgorithm = new MeanMosaicAlgorithm();
        }
        mosaicAlgorithm.setVariableContext(l3Config.getVariableContext());
        return mosaicAlgorithm;
    }
}