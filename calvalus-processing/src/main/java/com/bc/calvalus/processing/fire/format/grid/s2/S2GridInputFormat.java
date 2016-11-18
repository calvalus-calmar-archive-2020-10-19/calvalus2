package com.bc.calvalus.processing.fire.format.grid.s2;

import com.bc.calvalus.JobClientsMap;
import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.commons.InputPathResolver;
import com.bc.calvalus.inventory.hadoop.HdfsInventoryService;
import com.bc.calvalus.processing.hadoop.NoRecordReader;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.CombineFileSplit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author thomas
 */
public class S2GridInputFormat extends InputFormat {

    @Override
    public List<InputSplit> getSplits(JobContext context) throws IOException {
        Configuration conf = context.getConfiguration();
        String year = conf.get("calvalus.year");
        String month = conf.get("calvalus.month");
        Properties tiles = new Properties();
        tiles.load(getClass().getResourceAsStream("areas-tiles-2deg.properties"));
        List<InputSplit> splits = new ArrayList<>(1000);
        for (Map.Entry<Object, Object> entry : tiles.entrySet()) {
            CalvalusLogger.getLogger().info(String.format("Creating split(s) for tile %s...", entry.getKey().toString()));
            String tilePattern = entry.getValue().toString();
            String[] utmTiles = tilePattern.split("\\|");
            List<FileStatus> fileStatuses = new ArrayList<>();
            for (String utmTile : utmTiles) {
                String inputPathPattern = "hdfs://calvalus/calvalus/projects/fire/s2-ba/" + utmTile + "/BA-.*" + year + month + ".*nc";
                Collections.addAll(fileStatuses, getFileStatuses(inputPathPattern, conf));
            }
            addSplit(fileStatuses.toArray(new FileStatus[0]), splits, conf, entry.getKey().toString());
        }
        CalvalusLogger.getLogger().info(String.format("Created %d split(s).", splits.size()));
        return splits;
    }

    private void addSplit(FileStatus[] fileStatuses, List<InputSplit> splits, Configuration conf, String fiveDegreeTile) throws IOException {
        List<Path> filePaths = new ArrayList<>();
        List<Long> fileLengths = new ArrayList<>();
        for (FileStatus fileStatus : fileStatuses) {
            Path path = fileStatus.getPath();
            filePaths.add(path);
            fileLengths.add(fileStatus.getLen());
        }
        String lcTile = String.format("v%02dh%02d", Integer.parseInt(fiveDegreeTile.substring(1, 3)) * 2, Integer.parseInt(fiveDegreeTile.substring(4, 6)) * 2);
        String lcInputPath = "hdfs://calvalus/calvalus/projects/fire/aux/lc/" + String.format("lc-%s-%s.nc", "2005", lcTile);
        FileStatus lcPath = FileSystem.get(conf).getFileStatus(new Path(lcInputPath));
        filePaths.add(lcPath.getPath());
        fileLengths.add(lcPath.getLen());
        filePaths.add(new Path(fiveDegreeTile));
        fileLengths.add(0L);
        CombineFileSplit combineFileSplit = new CombineFileSplit(filePaths.toArray(new Path[filePaths.size()]),
                fileLengths.stream().mapToLong(Long::longValue).toArray());
        splits.add(combineFileSplit);
    }

    private FileStatus[] getFileStatuses(String inputPathPatterns,
                                         Configuration conf) throws IOException {

        JobClientsMap jobClientsMap = new JobClientsMap(new JobConf(conf));
        HdfsInventoryService hdfsInventoryService = new HdfsInventoryService(jobClientsMap, "eodata");
        InputPathResolver inputPathResolver = new InputPathResolver();
        List<String> inputPatterns = inputPathResolver.resolve(inputPathPatterns);
        return hdfsInventoryService.globFileStatuses(inputPatterns, conf);
    }

    public RecordReader createRecordReader(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException {
        return new NoRecordReader();
    }
}