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

package com.bc.calvalus.processing.fire.format.grid.meris;

import com.bc.calvalus.processing.beam.CalvalusProductIO;
import com.bc.calvalus.processing.fire.format.grid.AbstractGridMapper;
import com.bc.calvalus.processing.fire.format.grid.ErrorPredictor;
import com.bc.calvalus.processing.fire.format.grid.GridCell;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.lib.input.CombineFileSplit;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Product;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs the fire formatting grid mapper.
 *
 * @author thomas
 * @author marcop
 */
public class MerisGridMapper extends AbstractGridMapper {

    private boolean maskUnmappablePixels;

    protected MerisGridMapper(int targetRasterWidth, int targetRasterHeight) {
        super(40, 40);
    }

    @Override
    public void run(Context context) throws IOException, InterruptedException {

        int year = Integer.parseInt(context.getConfiguration().get("calvalus.year"));
        int month = Integer.parseInt(context.getConfiguration().get("calvalus.month"));

        CombineFileSplit inputSplit = (CombineFileSplit) context.getInputSplit();
        Path[] paths = inputSplit.getPaths();
        LOG.info("paths=" + Arrays.toString(paths));

        boolean computeBA = !paths[0].getName().equals("dummy");
        LOG.info(computeBA ? "Computing BA" : "Only computing coverage");
        maskUnmappablePixels = paths[0].getName().contains("v4.0.tif");
        if (maskUnmappablePixels) {
            LOG.info("v4.0 file; masking pixels which accidentally fall into unmappable LC class");
        }

        Product sourceProduct;
        Product lcProduct;
        if (computeBA) {
            File sourceProductFile = CalvalusProductIO.copyFileToLocal(paths[0], context.getConfiguration());
            sourceProduct = ProductIO.readProduct(sourceProductFile);

            File lcTile = CalvalusProductIO.copyFileToLocal(paths[1], context.getConfiguration());
            lcProduct = ProductIO.readProduct(lcTile);
        } else {
            // because coverage is computed in reducer
            return;
        }
        List<File> srProducts = new ArrayList<>();

        setDataSource(new MerisDataSource(sourceProduct, lcProduct, srProducts));

        ErrorPredictor errorPredictor = new ErrorPredictor();
        GridCell gridCell = computeGridCell(year, month, errorPredictor);

        context.progress();

        context.write(new Text(String.format("%d-%02d-%s", year, month, getTile(paths[1].toString()))), gridCell); // use LC input for determining tile
        errorPredictor.dispose();
    }

    private static String getTile(String path) {
        // path.toString() = hdfs://calvalus/calvalus/projects/fire/sr-fr-default-nc-classic/2008/v04h07/2008/2008-06-01-fire-nc/CCI-Fire-MERIS-SDR-L3-300m-v1.0-2008-06-01-v04h07.nc
        int startIndex = path.length() - 9;
        return path.substring(startIndex, startIndex + 6);
    }

    @Override
    protected boolean maskUnmappablePixels() {
        return maskUnmappablePixels;
    }

}