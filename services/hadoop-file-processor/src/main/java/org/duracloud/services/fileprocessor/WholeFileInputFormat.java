/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.services.fileprocessor;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;

import java.io.IOException;

/**
 * Input format which defines that files are not split and uses the 
 * SimpleFileRecordReader to produce key/value pairs based only on file path.
 *
 * @author: Bill Branan
 * Date: Aug 5, 2010
 */
public class WholeFileInputFormat extends FileInputFormat {

    @Override
    protected boolean isSplitable(FileSystem fs, Path filename) {
        return false;
    }

    @Override
    public RecordReader getRecordReader(InputSplit inputSplit,
                                        JobConf jobConf,
                                        Reporter reporter) throws IOException {
        String locations = "";
        for(String location : inputSplit.getLocations()) {
            locations += (location + ", ");
        }
        System.out.println("Creating record reader for split with locations: " +
                           locations);

        SimpleFileRecordReader recordReader =
            new SimpleFileRecordReader((FileSplit)inputSplit);
        return recordReader;
    }

    @Override
    public InputSplit[] getSplits(JobConf job, int numSplits)
        throws IOException {
        InputSplit[] splits = super.getSplits(job, numSplits);

        System.out.println("File splits for processing:");
        for(InputSplit split : splits) {
            for(String location : split.getLocations()) {
                System.out.println("Split location: " + location);
            }
        }

        return splits;
    }
}

