package org.esa.beam.binning;

import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.util.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.esa.beam.binning.BinningOpTest.*;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Norman Fomferra
 */
public class BinningOpRobustnessTest {

    @Before
    public void setUp() throws Exception {
        TESTDATA_DIR.mkdir();
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteTree(TESTDATA_DIR);
    }


    @Test
    public void testNoSourceProductSet() throws Exception {
        final BinningOp binningOp = new BinningOp();
        testThatOperatorExceptionIsThrown(binningOp, ".*single source product.*");
    }

    @Test
    public void testBinningConfigNotSet() throws Exception {
        final BinningOp binningOp = new BinningOp();
        binningOp.setSourceProduct(createSourceProduct());
        testThatOperatorExceptionIsThrown(binningOp, ".*parameter 'binningConfig'.*");
    }

    @Test
    public void testInvalidConfigsSet() throws Exception {
        final BinningOp binningOp = new BinningOp();
        binningOp.setSourceProduct(createSourceProduct());
        binningOp.setBinningConfig(new BinningConfig());        // not ok, numRows == 0
        testThatOperatorExceptionIsThrown(binningOp, ".*parameter 'binningConfig.maskExpr'.*");
    }

    @Test
    public void testNoStartDateSet() throws Exception {
        final BinningOp binningOp = new BinningOp();
        binningOp.setSourceProduct(createSourceProduct());
        binningOp.setBinningConfig(createBinningConfig());
        binningOp.setFormatterConfig(createFormatterConfig());
        testThatOperatorExceptionIsThrown(binningOp, ".*determine 'startDate'.*");
    }

    @Test
    public void testNoEndDateSet() throws Exception {
        final BinningOp binningOp = new BinningOp();
        binningOp.setSourceProduct(createSourceProduct());
        binningOp.setStartDate("2007-06-21");
        binningOp.setBinningConfig(createBinningConfig());
        binningOp.setFormatterConfig(createFormatterConfig());
        testThatOperatorExceptionIsThrown(binningOp, ".*determine 'endDate'.*");
    }

    private void testThatOperatorExceptionIsThrown(BinningOp binningOp, String regex) {
        String message = "OperatorException expected with message regex: " + regex;
        try {
            binningOp.getTargetProduct();
            fail(message);
        } catch (OperatorException e) {
            assertTrue(message + ", got [" + e.getMessage() + "]", Pattern.matches(regex, e.getMessage()));
        }
    }

}
