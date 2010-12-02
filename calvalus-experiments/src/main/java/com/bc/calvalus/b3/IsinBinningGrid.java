package com.bc.calvalus.b3;

import static java.lang.Math.cos;
import static java.lang.Math.toRadians;

/**
 * Implementation of the ISIN (Integerized Sinusoidal) binnning grid as used for NASA
 * SeaDAS and MODIS L3 products.
 *
 * @author Norman Fomferra
 * @see <a href="http://oceancolor.gsfc.nasa.gov/SeaWiFS/TECH_REPORTS/PreLPDF/PreLVol32.pdf">SeaWiFS Technical Report Series Volume 32, Level-3 SeaWiFS Data</a>
 * @see <a href="http://oceancolor.gsfc.nasa.gov/DOCS/Ocean_Level-3_Binned_Data_Products.pdf">Ocean Level-3 Binned Data Products</a>
 */
public final class IsinBinningGrid implements BinningGrid {
    public static final int DEFAULT_NUM_ROWS = 2160;

    private final int numRows;
    private final double[] latBin;
    private final int[] baseBin;
    private final int[] numBin;
    private final int numBins;

    public IsinBinningGrid() {
        this(DEFAULT_NUM_ROWS);
    }

    public IsinBinningGrid(int numRows) {
        if (numRows < 2) {
            throw new IllegalArgumentException("numRows < 2");
        }
        if (numRows % 2 != 0) {
            throw new IllegalArgumentException("numRows % 2 != 0");
        }

        this.numRows = numRows;
        latBin = new double[numRows];
        baseBin = new int[numRows];
        numBin = new int[numRows];
        baseBin[0] = 0;
        for (int row = 0; row < numRows; row++) {
            latBin[row] = ((row + 0.5) * 180.0 / numRows) - 90.0;
            numBin[row] = (int) (0.5 + (2 * numRows * cos(toRadians(latBin[row]))));
            if (row > 0) {
                baseBin[row] = baseBin[row - 1] + numBin[row - 1];
            }
        }
        numBins = baseBin[numRows - 1] + numBin[numRows - 1];
    }

    public int getNumRows() {
        return numRows;
    }

    @Override
    public int getNumCols(int row) {
        return numBin[row];
    }

    public int getNumBins() {
        return numBins;
    }

    @Override
    public int getBinIndex(double lat, double lon) {
        final int row = getRowIndex(lat);
        final int col = getColIndex(lon, row);
        return baseBin[row] + col;
    }

    public int getColIndex(double lon, int row) {
        if (lon <= -180.0) {
            return 0;
        }
        if (lon >= 180.0) {
            return numBin[row] - 1;
        }
        return (int) ((180.0 + lon) * numBin[row] / 360.0);
    }

    public int getRowIndex(double lat) {
        if (lat <= -90.0) {
            return 0;
        }
        if (lat >= 90.0) {
            return numRows - 1;
        }
        return (int) ((90.0 + lat) * (numRows / 180.0));
    }

    /**
     * Pseudo-code:
     * <pre>
     *       int row = numRows - 1;
     *       while (idx < baseBin[row]) {
     *            row--;
     *       }
     *       return row;
     * </pre>
     *
     * @param binIndex The bin ID.
     * @return The row index.
     */
    public int getRowIndex(int binIndex) {
        // compute max constant
        final int max = baseBin.length - 1;
        // avoid field access from the while loop
        final int[] rowBinIds = this.baseBin;
        int low = 0;
        int high = max;
        while (true) {
            int mid = (low + high) >>> 1;
            if (binIndex < rowBinIds[mid]) {
                high = mid - 1;
            } else if (mid == max) {
                return mid;
            } else if (binIndex < rowBinIds[mid + 1]) {
                return mid;
            } else {
                low = mid + 1;
            }
        }
    }

    public double[] getCenterLatLon(int binIndex) {
        final int row = getRowIndex(binIndex);
        return new double[]{
                latBin[row],
                getCenterLon(row, binIndex - baseBin[row])
        };
    }

    public double[] getCenterLatLon(int row, int col) {
        return new double[]{
                latBin[row],
                getCenterLon(row, col)
        };
    }


    public double getCenterLon(int row, int col) {
        return 360.0 * (col + 0.5) / numBin[row] - 180.0;
    }


}
