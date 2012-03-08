/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.beam.binning.operator;

/*
* Copyright (C) 2012 Brockmann Consult GmbH (info@brockmann-consult.de)
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


import com.bc.ceres.binding.BindingException;
import com.bc.ceres.binding.PropertyContainer;
import org.esa.beam.binning.*;
import org.esa.beam.binning.support.BinningContextImpl;
import org.esa.beam.binning.support.IsinBinningGrid;
import org.esa.beam.binning.support.VariableContextImpl;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.ParameterBlockConverter;

/**
 * Configuration for the binning.
 *
 * @author Norman Fomferra
 * @author Marco Zühlke
 */
@SuppressWarnings({"UnusedDeclaration"})
public class BinningConfig {

    /**
     * Number of rows in the binning grid.
     */
    @Parameter
    int numRows;

    /**
     * The number of pixels used for super-sampling an input pixel into sub-pixel.
     */
    @Parameter
    Integer superSampling;

    /**
     * The band maths expression used to filter input pixels.
     */
    @Parameter
    String maskExpr;

    /**
     * List of variables. A variable will generate a {@link org.esa.beam.framework.datamodel.VirtualBand VirtualBand}
     * in the input data product to be binned, so that it can be used for binning.
     */
    @Parameter(alias = "variables", itemAlias = "variable")
    VariableConfig[] variableConfigs;

    /**
     * List of aggregators. Aggregators generate the bands in the binned output products.
     */
    @Parameter(alias = "aggregators", itemAlias = "aggregator")
    AggregatorConfig[] aggregatorConfigs;

    public int getNumRows() {
        return numRows;
    }

    public void setNumRows(int numRows) {
        this.numRows = numRows;
    }

    public String getMaskExpr() {
        return maskExpr;
    }

    public void setMaskExpr(String maskExpr) {
        this.maskExpr = maskExpr;
    }

    public Integer getSuperSampling() {
        return superSampling;
    }

    public void setSuperSampling(Integer superSampling) {
        this.superSampling = superSampling;
    }

    public VariableConfig[] getVariableConfigs() {
        return variableConfigs;
    }

    public void setVariableConfigs(VariableConfig... variableConfigs) {
        this.variableConfigs = variableConfigs;
    }

    public AggregatorConfig[] getAggregatorConfigs() {
        return aggregatorConfigs;
    }

    public void setAggregatorConfigs(AggregatorConfig... aggregatorConfigs) {
        this.aggregatorConfigs = aggregatorConfigs;
    }

    public static BinningConfig fromXml(String xml) throws BindingException {
        return new ParameterBlockConverter().convertXmlToObject(xml, new BinningConfig());
    }

    public String toXml() {
        try {
            return new ParameterBlockConverter().convertObjectToXml(this);
        } catch (BindingException e) {
            throw new RuntimeException(e);
        }
    }

    public BinningContext createBinningContext() {
        VariableContext variableContext = createVariableContext();
        return new BinningContextImpl(createBinningGrid(),
                                      createBinManager(variableContext),
                                      getSuperSampling() != null ? getSuperSampling() : 1);
    }

    public BinningGrid createBinningGrid() {
        if (numRows == 0) {
            numRows = IsinBinningGrid.DEFAULT_NUM_ROWS;
        }
        return new IsinBinningGrid(numRows);
    }

    private BinManager createBinManager(VariableContext variableContext) {
        Aggregator[] aggregators = createAggregators(variableContext);
        return createBinManager(variableContext, aggregators);
    }

    public Aggregator[] createAggregators(VariableContext variableContext) {
        Aggregator[] aggregators = new Aggregator[aggregatorConfigs.length];
        for (int i = 0; i < aggregators.length; i++) {
            AggregatorConfig aggregatorConfig = aggregatorConfigs[i];
            AggregatorDescriptor descriptor = AggregatorDescriptorRegistry.getInstance().getAggregatorDescriptor(aggregatorConfig.getAggregatorName());
            if (descriptor != null) {
                aggregators[i] = descriptor.createAggregator(variableContext, PropertyContainer.createObjectBacked(aggregatorConfig));
            } else {
                throw new IllegalArgumentException("Unknown aggregator type: " + aggregatorConfig.getAggregatorName());
            }
        }
        return aggregators;
    }

    protected BinManager createBinManager(VariableContext variableContext, Aggregator[] aggregators) {
        return new BinManager(variableContext, aggregators);
    }

    public VariableContext createVariableContext() {
        VariableContextImpl variableContext = new VariableContextImpl();
        if (maskExpr == null) {
            maskExpr = "";
        }
        variableContext.setMaskExpr(maskExpr);

        // define declared variables
        //
        if (variableConfigs != null) {
            for (VariableConfig variableConfig : variableConfigs) {
                variableContext.defineVariable(variableConfig.getName(), variableConfig.getExpr());
            }
        }

        // define variables of all aggregators
        //
        if (aggregatorConfigs != null) {
            for (AggregatorConfig aggregatorConfig : aggregatorConfigs) {
                String varName = aggregatorConfig.getVarName();
                if (varName != null) {
                    variableContext.defineVariable(varName);
                } else {
                    String[] varNames = aggregatorConfig.getVarNames();
                    if (varNames != null) {
                        for (String varName1 : varNames) {
                            variableContext.defineVariable(varName1);
                        }
                    }
                }
            }
        }
        return variableContext;
    }

}