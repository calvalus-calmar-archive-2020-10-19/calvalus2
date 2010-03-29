package com.bc.calvados.hadoop.n1;

import com.bc.calvados.hadoop.eodata.N1ProductFileConverter;
import com.bc.calvados.hadoop.eodata.ProductFileConverter;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.MapReduceTestCluster;
import org.apache.hadoop.mapred.OutputLogFilter;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StxMapReduceTest {

    private static final int BUFFER_SIZE = 4096;
    private static final short REPLICATION = (short) 2;
    private static final int BLOCK_SIZE = 1024 * 1024;

    private static MapReduceTestCluster mrTester;
    private static Path inputProduct;

    @BeforeClass
    public static void beforeClass() throws Exception {
        mrTester = new MapReduceTestCluster();
        mrTester.setUpCluster();

        inputProduct = createConvertedN1Product();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (mrTester != null) {
            mrTester.tearDownCluster();
        }
    }

    private static Path createConvertedN1Product() throws IOException {
        Path path = new Path(mrTester.getInputDir(), "converted");
        FSDataOutputStream out = mrTester.getFileSystem().create(path, true, BUFFER_SIZE, REPLICATION, BLOCK_SIZE, null);
        File inputFile = new File("src/test/data/MER_RR__1P.N1");
        assertTrue(inputFile.length() > 0);

        ProductFileConverter n1Converter = new N1ProductFileConverter();
        n1Converter.convertToMRFriendlyFormat(inputFile, out);
        out.close();
        return path;
    }

    @Test
    public void testN1Split() throws Exception {
        final FileSystem fs = mrTester.getFileSystem();
        final Job job = new Job(mrTester.createJobConf(), "stx test");

        // split it using a N1 product input format
        FileInputFormat.addInputPath(job, mrTester.getInputDir());
        N1ProductFormat format = new N1ProductFormat();
        final List<InputSplit> splits = format.getSplits(job);
        assertEquals(11, splits.size());

        FileStatus fileStatus = fs.getFileStatus(inputProduct);
        BlockLocation[] locations = fs.getFileBlockLocations(fileStatus, 0, fileStatus.getLen());

        // make sure that each split is a block and the locations match
        int totalHeight = 0;
        for (int i = 0; i < splits.size(); i++) {
            N1LineInputSplit n1InputSplit = (N1LineInputSplit) splits.get(i);

            assertEquals(totalHeight, n1InputSplit.getYStart());
            totalHeight += n1InputSplit.getHeight();

            //System.out.println("n1InputSplit.getYStart() = " + n1InputSplit.getYStart());
            //System.out.println("n1InputSplit.getHeight() = " + n1InputSplit.getHeight());

            // general split tests
            assertEquals(locations[i].getOffset(), n1InputSplit.getStart());
            assertEquals(locations[i].getLength(), n1InputSplit.getLength());
            String[] blockLocs = locations[i].getHosts();
            String[] splitLocs = n1InputSplit.getLocations();
            assertEquals(2, blockLocs.length);
            assertEquals(2, splitLocs.length);
            assertTrue((blockLocs[0].equals(splitLocs[0]) &&
                    blockLocs[1].equals(splitLocs[1])) ||
                    (blockLocs[1].equals(splitLocs[0]) &&
                            blockLocs[0].equals(splitLocs[1])));
        }

        assertEquals(306, totalHeight);
    }

    @Test
    public void testMapper() throws Exception {
        runStxMapper();

        // check the output
        final FileSystem fs = mrTester.getFileSystem();
        Path[] outputFiles = FileUtil.stat2Paths(fs.listStatus(
        mrTester.getOutputDir(), new OutputLogFilter()));
        assertEquals(1, outputFiles.length);
        InputStream is = fs.open(outputFiles[0]);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        final String line1 = reader.readLine();
        final String line2 = reader.readLine();
        int numLines = 2;
        while(reader.readLine() != null) {
            numLines++;
        }
        
        assertEquals("0\tmin=6374.0  max=15441.0", line1);
        assertEquals("16\tmin=6209.0  max=10677.0", line2);
        assertEquals(22, numLines);
        reader.close();
    }

    private void runStxMapper() throws Exception {
        final Job job = new Job(mrTester.createJobConf(), "stx test");

        job.setMapperClass(StxMapper.class);
        job.setInputFormatClass(N1ProductFormat.class);
        job.setMapOutputKeyClass(IntWritable.class);
        job.setMapOutputValueClass(StxWritable.class);
        FileInputFormat.setInputPaths(job, mrTester.getInputDir());
        FileOutputFormat.setOutputPath(job, mrTester.getOutputDir());
        job.waitForCompletion(true);
    }
}