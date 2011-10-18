package com.bc.calvalus.production.hadoop;

import com.bc.calvalus.inventory.InventoryService;
import com.bc.calvalus.processing.JobConfigNames;
import com.bc.calvalus.processing.hadoop.HadoopProcessingService;
import com.bc.calvalus.processing.l2.L2WorkflowItem;
import com.bc.calvalus.production.Production;
import com.bc.calvalus.production.ProductionException;
import com.bc.calvalus.production.ProductionRequest;
import com.bc.calvalus.staging.Staging;
import com.bc.calvalus.staging.StagingService;
import com.vividsolutions.jts.geom.Geometry;
import org.apache.hadoop.conf.Configuration;
import org.esa.beam.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * A production type used for generating one or more Level-2 products.
 *
 * @author MarcoZ
 * @author Norman
 */
public class L2ProductionType extends HadoopProductionType {

    static final String NAME = "L2";

    public L2ProductionType(InventoryService inventoryService, HadoopProcessingService processingService, StagingService stagingService) {
        super(NAME, inventoryService, processingService, stagingService);
    }

    @Override
    public Production createProduction(ProductionRequest productionRequest) throws ProductionException {
        final String productionId = Production.createId(productionRequest.getProductionType());
        final String productionName = createL2ProductionName(productionRequest);
        final String userName = productionRequest.getUserName();

        L2WorkflowItem workflowItem = createWorkflowItem(productionId, productionRequest);

        // todo - if autoStaging=true, create sequential workflow and add staging job
        String stagingDir = userName + "/" + productionId;
        boolean autoStaging = productionRequest.isAutoStaging();
        return new Production(productionId,
                              productionName,
                              workflowItem.getOutputDir(),
                              stagingDir,
                              autoStaging,
                              productionRequest,
                              workflowItem);
    }

    @Override
    protected Staging createUnsubmittedStaging(Production production) {
        return new L2Staging(production,
                             getProcessingService().getJobClient().getConf(),
                             getStagingService().getStagingDir());
    }

    static String createL2ProductionName(ProductionRequest productionRequest) throws ProductionException {
        return String.format("Level 2 production using input path '%s' and L2 processor '%s'",
                             productionRequest.getString("inputPath"),
                             productionRequest.getString("processorName"));
    }

    L2WorkflowItem createWorkflowItem(String productionId,
                                      ProductionRequest productionRequest) throws ProductionException {

        String[] inputFiles = getInputFiles(getInventoryService(), productionRequest);

        String outputDir = getOutputPath(productionRequest, productionId, "");

        String processorName = productionRequest.getString("processorName");
        String processorParameters = productionRequest.getString("processorParameters", "<parameters/>");
        String processorBundle = String.format("%s-%s",
                                               productionRequest.getString("processorBundleName"),
                                               productionRequest.getString("processorBundleVersion"));

        Geometry regionGeometry = productionRequest.getRegionGeometry(null);

        Configuration l2JobConfig = createJobConfig(productionRequest);
        l2JobConfig.set(JobConfigNames.CALVALUS_INPUT, StringUtils.join(inputFiles, ","));
        l2JobConfig.set(JobConfigNames.CALVALUS_OUTPUT_DIR, outputDir);
        l2JobConfig.set(JobConfigNames.CALVALUS_L2_BUNDLE, processorBundle);
        l2JobConfig.set(JobConfigNames.CALVALUS_L2_OPERATOR, processorName);
        l2JobConfig.set(JobConfigNames.CALVALUS_L2_PARAMETERS, processorParameters);
        l2JobConfig.set(JobConfigNames.CALVALUS_REGION_GEOMETRY, regionGeometry != null ? regionGeometry.toString() : "");

        return new L2WorkflowItem(getProcessingService(), productionId, l2JobConfig);
    }

    public static String[] getInputFiles(InventoryService inventoryService, ProductionRequest productionRequest) throws ProductionException {
        String inputPath = productionRequest.getString("inputPath");
        String regionName = productionRequest.getRegionName();
        Date[] dateList = productionRequest.getDates("dateList", null);
        String[] inputFiles;
        if (dateList != null) {
            List<String> inputFileAccumulator = new ArrayList<String>();
            for (Date date : dateList) {
                inputFileAccumulator.addAll(Arrays.asList(getInputPaths(inventoryService, inputPath, date, date, regionName)));
            }
            inputFiles = inputFileAccumulator.toArray(new String[inputFileAccumulator.size()]);
        } else {
            Date minDate = productionRequest.getDate("minDate", null);
            Date maxDate = productionRequest.getDate("maxDate", null);
            inputFiles = getInputPaths(inventoryService, inputPath, minDate, maxDate, regionName);
        }
        if (inputFiles.length == 0) {
            throw new ProductionException("No input files found for given time range.");
        }
        return inputFiles;
    }

}
