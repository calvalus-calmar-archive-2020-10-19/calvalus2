package com.bc.calvados.hadoop.n1;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;


public class StxReducer extends Reducer<IntWritable, StxWritable, IntWritable, StxWritable> {

    private StxWritable result = new StxWritable();

    /**
     * This method is called once for each key. Most applications will define
     * their reduce class by overriding this method. The default implementation
     * is an identity function.
     */
    @Override
    protected void reduce(IntWritable key, Iterable<StxWritable> values, Context context) throws IOException, InterruptedException {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (StxWritable stx : values) {
            min = Math.min(stx.getMin(), min);
            max = Math.max(stx.getMax(), max);
        }
        result.setMin(min);
        result.setMax(max);
        context.write(key, result);
    }
}
