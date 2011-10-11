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

package com.bc.calvalus.production.hadoop;

import com.bc.calvalus.commons.WorkflowItem;
import com.bc.calvalus.inventory.InventoryService;
import com.bc.calvalus.processing.JobConfigNames;
import com.bc.calvalus.processing.beam.ProductValidatorWorkflowItem;
import com.bc.calvalus.processing.hadoop.HadoopProcessingService;
import com.bc.calvalus.production.Production;
import com.bc.calvalus.production.ProductionException;
import com.bc.calvalus.production.ProductionRequest;
import com.bc.calvalus.staging.Staging;
import com.bc.calvalus.staging.StagingService;
import org.apache.commons.lang.NotImplementedException;
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
public class PVProductionType extends HadoopProductionType {

    static final String NAME = "PV";

    public PVProductionType(InventoryService inventoryService, HadoopProcessingService processingService, StagingService stagingService) {
        super(NAME, inventoryService, processingService, stagingService);
    }

    @Override
    public Production createProduction(ProductionRequest productionRequest) throws ProductionException {
        final String productionId = Production.createId(productionRequest.getProductionType());
        final String productionName = createProductionName(productionRequest);
        final String userName = productionRequest.getUserName();

        WorkflowItem workflowItem = createWorkflowItem(productionId, productionRequest);

        // todo - if autoStaging=true, create sequential workflow and add staging job
        String stagingDir = userName + "/" + productionId;
        boolean autoStaging = productionRequest.isAutoStaging(); //TODO
        autoStaging = false;
        return new Production(productionId,
                              productionName,
                              "",
                              stagingDir,
                              autoStaging,
                              productionRequest,
                              workflowItem);
    }

    // TODO, at the moment no staging implemented
    @Override
    protected Staging createUnsubmittedStaging(Production production) {
        throw new NotImplementedException("Staging currently not implemented for product validation.");
    }

    static String createProductionName(ProductionRequest productionRequest) throws ProductionException {
        return String.format("Product validation using input path '%s'", productionRequest.getString("inputPath"));
    }

    WorkflowItem createWorkflowItem(String productionId,
                                      ProductionRequest productionRequest) throws ProductionException {

        String inputPath = productionRequest.getString("inputPath");
        String regionName = productionRequest.getRegionName();

        Date[] dateList = productionRequest.getDates("dateList", null);
        String[] inputFiles;
        if (dateList != null) {
            List<String> inputFileAccumulator = new ArrayList<String>();
            for (Date date : dateList) {
                inputFileAccumulator.addAll(Arrays.asList(getInputPaths(inputPath, date, date, regionName)));
            }
            inputFiles = inputFileAccumulator.toArray(new String[inputFileAccumulator.size()]);
        } else {
            Date minDate = productionRequest.getDate("minDate", null);
            Date maxDate = productionRequest.getDate("maxDate", null);
            inputFiles = getInputPaths(inputPath, minDate, maxDate, regionName);
        }
        if (inputFiles.length == 0) {
            throw new ProductionException("No input files found for given time range.");
        }

        String outputDir = getOutputPath(productionRequest, productionId, "");

        Configuration jobConfig = createJobConfig(productionRequest);
        jobConfig.set(JobConfigNames.CALVALUS_INPUT, StringUtils.join(inputFiles, ","));
        jobConfig.set(JobConfigNames.CALVALUS_OUTPUT_DIR, outputDir);

        return new ProductValidatorWorkflowItem(getProcessingService(), productionId, jobConfig);
    }

}