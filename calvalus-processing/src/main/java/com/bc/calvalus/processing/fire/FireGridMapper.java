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

package com.bc.calvalus.processing.fire;

import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.processing.beam.CalvalusProductIO;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.CombineFileSplit;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Product;

import javax.script.ScriptException;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static com.bc.calvalus.processing.fire.FireGridLcRemapping.*;

/**
 * Runs the fire formatting grid mapper.
 *
 * @author thomas
 * @author marcop
 */
public class FireGridMapper extends Mapper<Text, FileSplit, Text, GridCell> {

    static final int TARGET_RASTER_WIDTH = 40;
    static final int TARGET_RASTER_HEIGHT = 40;
    static final int LC_CLASSES_COUNT = 18;
    static final int NO_DATA = -1;
    static final int NO_AREA = 0;

    private static final Logger LOG = CalvalusLogger.getLogger();

    @Override
    public void run(Context context) throws IOException, InterruptedException {

        int year = Integer.parseInt(context.getConfiguration().get("calvalus.year"));
        int month = Integer.parseInt(context.getConfiguration().get("calvalus.month"));

        int doyFirstOfMonth = Year.of(year).atMonth(month).atDay(1).getDayOfYear();
        int doyLastOfMonth = Year.of(year).atMonth(month).atDay(Year.of(year).atMonth(month).lengthOfMonth()).getDayOfYear();

        int doyFirstHalf = Year.of(year).atMonth(month).atDay(7).getDayOfYear();
        int doySecondHalf = Year.of(year).atMonth(month).atDay(22).getDayOfYear();

        CombineFileSplit inputSplit = (CombineFileSplit) context.getInputSplit();
        Path[] paths = inputSplit.getPaths();
        LOG.info("paths=" + Arrays.toString(paths));

        String tile = getTile(paths[0]);
        File centerSourceProductFile = CalvalusProductIO.copyFileToLocal(paths[0], context.getConfiguration());
        Product centerSourceProduct = ProductIO.readProduct(centerSourceProductFile);

        File lcTile = CalvalusProductIO.copyFileToLocal(paths[1], context.getConfiguration());
        Product lcProduct = ProductIO.readProduct(lcTile);

        FireGridDataSource dataSource = new FireGridDataSourceImpl(centerSourceProduct, lcProduct);
        ErrorPredictor errorPredictor = new ErrorPredictor();
        dataSource.setDoyFirstOfMonth(doyFirstOfMonth);
        dataSource.setDoyLastOfMonth(doyLastOfMonth);
        dataSource.setDoyFirstHalf(doyFirstHalf);
        dataSource.setDoySecondHalf(doySecondHalf);

        SourceData data = new SourceData();
        double[] areas = new double[TARGET_RASTER_WIDTH * TARGET_RASTER_HEIGHT];

        float[] baFirstHalf = new float[TARGET_RASTER_WIDTH * TARGET_RASTER_HEIGHT];
        float[] baSecondHalf = new float[TARGET_RASTER_WIDTH * TARGET_RASTER_HEIGHT];
        int[] patchNumberFirstHalf = new int[TARGET_RASTER_WIDTH * TARGET_RASTER_HEIGHT];
        int[] patchNumberSecondHalf = new int[TARGET_RASTER_WIDTH * TARGET_RASTER_HEIGHT];

        List<float[]> baInLcFirstHalf = new ArrayList<>();
        List<float[]> baInLcSecondHalf = new ArrayList<>();
        for (int c = 0; c < LC_CLASSES_COUNT; c++) {
            baInLcFirstHalf.add(new float[TARGET_RASTER_WIDTH * TARGET_RASTER_HEIGHT]);
            baInLcSecondHalf.add(new float[TARGET_RASTER_WIDTH * TARGET_RASTER_HEIGHT]);
        }

        context.progress();

        int targetPixelIndex = 0;
        for (int y = 0; y < TARGET_RASTER_HEIGHT; y++) {
            LOG.info(String.format("Processing line %d of target raster.", y));
            for (int x = 0; x < TARGET_RASTER_WIDTH; x++) {
                data.reset();
                Rectangle sourceRect = new Rectangle(x * TARGET_RASTER_WIDTH, y * TARGET_RASTER_HEIGHT, 90, 90);
                dataSource.readPixels(sourceRect, data);

                float baValueFirstHalf = 0.0F;
                float baValueSecondHalf = 0.0F;

                for (int i = 0; i < data.pixels.length; i++) {
                    int doy = data.pixels[i];
                    if (isValidFirstHalfPixel(doyFirstOfMonth, doySecondHalf, doy)) {
                        baValueFirstHalf += data.areas[i];
                        for (int lcClass = 0; lcClass < LC_CLASSES_COUNT; lcClass++) {
                            if (isSingleTarget(lcClass + 1)) {
                                // for each single target class, check if the pixel is of the class, and add the area to the class
                                baInLcFirstHalf.get(lcClass)[targetPixelIndex] += isInSingleLcClass(lcClass + 1, data.lcClasses[i]) ? data.areas[i] : 0.0;
                            }
                        }
                    } else if (isValidSecondHalfPixel(doyLastOfMonth, doyFirstHalf, doy)) {
                        baValueSecondHalf += data.areas[i];
                        for (int lcClass = 0; lcClass < LC_CLASSES_COUNT; lcClass++) {
                            if (isSingleTarget(lcClass + 1)) {
                                // for each single target class, check if the pixel is of the class, and add the area to the class
                                baInLcSecondHalf.get(lcClass)[targetPixelIndex] += isInSingleLcClass(lcClass + 1, data.lcClasses[i]) ? data.areas[i] : 0.0;
                            }
                        }
                    }
                    areas[targetPixelIndex] += data.areas[i];
                }

                for (int lcClass = 0; lcClass < LC_CLASSES_COUNT; lcClass++) {
                    double firstHalfValue = 0;
                    double secondHalfValue = 0;
                    if (!isSingleTarget(lcClass + 1) && isInMultiLcClass(lcClass + 1, data.pixels)) {
                        for (int i = 0; i < data.pixels.length; i++) {
                            int pixel = data.pixels[i];
                            if (isValidFirstHalfPixel(doyFirstOfMonth, doySecondHalf, pixel) && isPartOfMultiClass(lcClass + 1, pixel)) {
                                firstHalfValue += areas[i];
                            } else if (isValidSecondHalfPixel(doyLastOfMonth, doyFirstHalf, pixel) && isPartOfMultiClass(lcClass + 1, pixel)) {
                                secondHalfValue += areas[i];
                            }
                        }
                        baInLcFirstHalf.get(lcClass)[targetPixelIndex] += firstHalfValue;
                        baInLcSecondHalf.get(lcClass)[targetPixelIndex] += secondHalfValue;
                    }
                }

                baFirstHalf[targetPixelIndex] = baValueFirstHalf;
                baSecondHalf[targetPixelIndex] = baValueSecondHalf;
                patchNumberFirstHalf[targetPixelIndex] = data.patchCountFirstHalf;
                patchNumberSecondHalf[targetPixelIndex] = data.patchCountSecondHalf;

                targetPixelIndex++;
            }
        }

        float[] errorsFirstHalf = predict(errorPredictor, baFirstHalf, areas);
        float[] errorsSecondHalf = predict(errorPredictor, baSecondHalf, areas);

        cleanErrors(baFirstHalf, errorsFirstHalf);
        cleanErrors(baSecondHalf, errorsSecondHalf);

        context.progress();

        GridCell gridCell = new GridCell();
        gridCell.setBaFirstHalf(baFirstHalf);
        gridCell.setBaSecondHalf(baSecondHalf);
        gridCell.setPatchNumberFirstHalf(patchNumberFirstHalf);
        gridCell.setPatchNumberSecondHalf(patchNumberSecondHalf);
        gridCell.setErrorsFirstHalf(errorsFirstHalf);
        gridCell.setErrorsSecondHalf(errorsSecondHalf);
        gridCell.setBaInLcFirstHalf(baInLcFirstHalf);
        gridCell.setBaInLcSecondHalf(baInLcSecondHalf);

        context.write(new Text(String.format("%d-%02d-%s", year, month, tile)), gridCell);
        errorPredictor.dispose();
    }

    static boolean isValidFirstHalfPixel(int doyFirstOfMonth, int doySecondHalf, int pixel) {
        return pixel >= doyFirstOfMonth && pixel < doySecondHalf - 6 && pixel != 999 && pixel != NO_DATA;
    }

    static boolean isValidSecondHalfPixel(int doyLastOfMonth, int doyFirstHalf, int pixel) {
        return pixel > doyFirstHalf + 8 && pixel <= doyLastOfMonth && pixel != 999 && pixel != NO_DATA;
    }

    private static void cleanErrors(float[] ba, float[] errors) {
        for (int i = 0; i < errors.length; i++) {
            if (ba[i] == 0.0) {
                errors[i] = 0.0F;
            }
        }
    }

    private float[] predict(ErrorPredictor errorPredictor, float[] ba, double[] areas) {
        try {
            return errorPredictor.predictError(ba, areas);
        } catch (ScriptException e) {
            throw new RuntimeException(String.format("Unable to predict error from BA %s, areas %s", Arrays.toString(ba), Arrays.toString(areas)), e);
        }
    }

    private static String getTile(Path path) {
        int startIndex = path.toString().indexOf("BA_PIX_MER_") + "BA_PIX_MER_".length();
        return path.toString().substring(startIndex, startIndex + 6);
    }

    interface FireGridDataSource {

        void readPixels(Rectangle sourceRect, SourceData data) throws IOException;

        void setDoyFirstOfMonth(int doyFirstOfMonth);

        void setDoyLastOfMonth(int doyLastOfMonth);

        void setDoyFirstHalf(int doyFirstHalf);

        void setDoySecondHalf(int doySecondHalf);
    }

}
