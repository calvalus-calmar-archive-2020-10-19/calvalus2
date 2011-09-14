package com.bc.calvalus.processing.beam;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.junit.Test;

import static com.bc.calvalus.processing.JobUtils.createGeometry;
import static com.bc.calvalus.processing.beam.ProductFactory.isGlobalCoverageGeometry;
import static org.junit.Assert.*;

/**
 * @author Norman
 */
public class ProductFactoryTest {
    @Test
    public void testPassThrough() throws Exception {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();

        Product sourceProduct = new Product("N", "T", 100, 100);
        sourceProduct.addBand("b1", "1.0");
        sourceProduct.addBand("b2", "2.0");

        Product targetProduct = ProductFactory.getProcessedProduct(sourceProduct, null, false, "PassThrough", "<parameters/>");
        assertSame(sourceProduct, targetProduct);
    }

    @Test
    public void testIsGlobeCoverageGeometry() throws Exception {
        assertTrue(isGlobalCoverageGeometry(createGeometry("POLYGON((-180 -90, 180 -90, 180 90, -180 90, -180 -90))")));
        assertTrue(isGlobalCoverageGeometry(createGeometry("POLYGON((-180 -90, -180 90, 180 90,  180 -90,  -180 -90))")));
        assertFalse(isGlobalCoverageGeometry(createGeometry("POLYGON((-180 -90, 180 -90, 180 89, -180 89, -180 -90))")));
        assertFalse(isGlobalCoverageGeometry(createGeometry("POLYGON((-180 -90, 80 -90, 80 89, -180 89, -180 -90))")));
    }


}
