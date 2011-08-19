package com.bc.calvalus.processing.beam;

import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.processing.JobConfNames;
import com.bc.calvalus.processing.shellexec.ProcessorException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.io.FileUtils;

import java.awt.*;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processor adapter for BEAM L2 operators.
 * <ul>
 * <li>transforms request to parameter objects</li>
 * <li>instantiates and calls operator</li>
 * </ul>
 *
 * @author Boe
 */
public class BeamOperatorMapper extends Mapper<NullWritable, NullWritable, Text /*N1 input name*/, Text /*split output name*/> {

    private static final String COUNTER_GROUP_NAME_PRODUCTS = "Product Counts";
    private static final int DEFAULT_TILE_HEIGHT = 64;
    private static final Logger LOG = CalvalusLogger.getLogger();

    /**
     * Mapper implementation method. See class comment.
     *
     * @param context task "configuration"
     * @throws java.io.IOException  if installation or process initiation raises it
     * @throws InterruptedException if processing is interrupted externally
     * @throws com.bc.calvalus.processing.shellexec.ProcessorException
     *                              if processing fails
     */
    @Override
    public void run(Context context) throws IOException, InterruptedException, ProcessorException {
        Configuration jobConfig = context.getConfiguration();
        ProductFactory productFactory = new ProductFactory(jobConfig);

        final FileSplit split = (FileSplit) context.getInputSplit();

        // write initial log entry for runtime measurements
        LOG.info(context.getTaskAttemptID() + " starts processing of split " + split);
        final long startTime = System.nanoTime();

        try {
            // parse request

            Path inputPath = split.getPath();
            String inputFormat = jobConfig.get(JobConfNames.CALVALUS_INPUT_FORMAT, "ENVISAT");
            String regionGeometryWkt = jobConfig.get(JobConfNames.CALVALUS_REGION_GEOMETRY);
            String level2OperatorName = jobConfig.get(JobConfNames.CALVALUS_L2_OPERATOR);
            String level2Parameters = jobConfig.get(JobConfNames.CALVALUS_L2_PARAMETERS);
            Product targetProduct = productFactory.getProduct(inputPath,
                                                              inputFormat,
                                                              regionGeometryWkt,
                                                              level2OperatorName,
                                                              level2Parameters);
            if (targetProduct == null) {
                LOG.info("Product not used");
                return;
            }
            LOG.info(context.getTaskAttemptID() + " target product created");
            if (targetProduct.getSceneRasterWidth() == 0 || targetProduct.getSceneRasterHeight() == 0) {
                LOG.warning("target product is empty, skip writing.");
            } else {
                // process input and write target product
                String inputFilename = inputPath.getName();
                String outputFilename = "L2_of_" + FileUtils.exchangeExtension(inputFilename, ".seq");
                Path workOutputPath = FileOutputFormat.getWorkOutputPath(context);
                final Path outputProductPath = new Path(workOutputPath, outputFilename);
                int tileHeight = DEFAULT_TILE_HEIGHT;
                Dimension preferredTileSize = targetProduct.getPreferredTileSize();
                if (preferredTileSize != null) {
                    tileHeight = preferredTileSize.height;
                }
                StreamingProductWriter streamingProductWriter = new StreamingProductWriter(jobConfig, context);
                streamingProductWriter.writeProduct(targetProduct, outputProductPath, tileHeight);
                context.getCounter(COUNTER_GROUP_NAME_PRODUCTS, inputPath.getName()).increment(1);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "BeamOperatorMapper exception: " + e.toString(), e);
            throw new ProcessorException("BeamOperatorMapper exception: " + e.toString(), e);
        } finally {
            // write final log entry for runtime measurements
            final long stopTime = System.nanoTime();
            LOG.info(context.getTaskAttemptID() + " stops processing of split " + split + " after " + ((stopTime - startTime) / 1E9) + " sec");
        }
    }


}
