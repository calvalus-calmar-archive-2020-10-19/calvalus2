package com.bc.calvalus.generator.extractor.counter;


import com.bc.calvalus.generator.GenerateLogException;
import com.bc.calvalus.generator.extractor.Extractor;
import com.bc.calvalus.generator.extractor.jobs.JobType;
import com.bc.wps.utilities.PropertiesWrapper;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;

/**
 * @author muhammad.bc.
 */
public class CounterExtractor extends Extractor {


    private static final String COUNTER_XSL = "counter.xsl";
    private final String countersUrl;
    private final String xsltAsString;

    public CounterExtractor() {
        countersUrl = PropertiesWrapper.get("calvalus.history.counters.url");
        xsltAsString = loadXSLTFile(COUNTER_XSL);
    }

    @Override
    public <T> HashMap<String, T> extractInfo(int from, int to, List<JobType> jobTypes) throws GenerateLogException {
        HashMap<String, CountersType> confTypesHashMap = new HashMap<>();
        int size = jobTypes.size();
        if (!(size >= from && from >= 0) && !(size >= to && to >= 0) && (to >= from)) {
            throw new GenerateLogException("The range is out of bound");
        }
        try {
            for (int i = from; i < to; i++) {
                JobType jobType = jobTypes.get(i);
                String jobTypeId = jobType.getId();
                CountersType confType = getType(jobTypeId);
                confTypesHashMap.put(jobTypeId, confType);
            }
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return (HashMap<String, T>) confTypesHashMap;
    }


    public CountersType getType(String jobId) throws JAXBException {
        StreamSource xsltSource = new StreamSource(new StringReader(xsltAsString));
        String sourceURL = String.format(countersUrl, jobId);
        return extractInfo(sourceURL, xsltSource, CountersType.class);
    }

    public String getXsltAsString() {
        return xsltAsString;
    }
}