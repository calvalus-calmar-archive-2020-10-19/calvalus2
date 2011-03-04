package com.bc.calvalus.production.hadoop;

import com.bc.calvalus.production.ProductionException;
import com.bc.calvalus.production.ProductionRequest;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Generates the WPS XML.
 */
public class WpsXmlGeneratorTest {

    @Test
    public void testL3WpsXml() throws ProductionException {
        L3ProcessingRequestFactory l3ProcessingRequestFactory = new L3ProcessingRequestFactory() {
            @Override
            public String[] getInputFiles(ProductionRequest request) throws ProductionException {
                return new String[]{"fileA", "fileB", "fileC"};
            }
        };
        ProductionRequest productionRequest = L3ProcessingRequestTest.createValidL3ProductionRequest();
        L3ProcessingRequest processingRequest = l3ProcessingRequestFactory.createProcessingRequest(productionRequest);

        String xml = new WpsXmlGenerator().createL3WpsXml("ID_pi-pa-po", "Wonderful L3", processingRequest);
        assertNotNull(xml);

        // System.out.println(xml);

        assertTrue(xml.contains("<ows:Identifier>ID_pi-pa-po</ows:Identifier>"));
        assertTrue(xml.contains("<ows:Title>Wonderful L3</ows:Title>"));

        assertTrue(xml.contains("<ows:Identifier>calvalus.processor.package</ows:Identifier>"));
        assertTrue(xml.contains("<wps:LiteralData>beam</wps:LiteralData>"));

        assertTrue(xml.contains("<ows:Identifier>calvalus.processor.version</ows:Identifier>"));
        assertTrue(xml.contains("<wps:LiteralData>4.9-SNAPSHOT</wps:LiteralData>"));

        assertTrue(xml.contains("<ows:Identifier>calvalus.output.dir</ows:Identifier>"));
        assertTrue(xml.contains("<wps:Reference xlink:href=\"calvalus-level3-output\"/>"));

        assertTrue(xml.contains("<ows:Identifier>calvalus.input</ows:Identifier>"));
        assertTrue(xml.contains("<wps:Reference xlink:href=\"fileA\"/>"));
        assertTrue(xml.contains("<wps:Reference xlink:href=\"fileB\"/>"));
        assertTrue(xml.contains("<wps:Reference xlink:href=\"fileC\"/>"));

        assertTrue(xml.contains("<ows:Identifier>calvalus.l2.operator</ows:Identifier>"));
        assertTrue(xml.contains("<wps:LiteralData>BandMaths</wps:LiteralData>"));

        assertTrue(xml.contains("<ows:Identifier>calvalus.l2.parameters</ows:Identifier>"));
        assertTrue(xml.contains("<wps:ComplexData><!-- no params --></wps:ComplexData>"));

        assertTrue(xml.contains("<ows:Identifier>calvalus.l3.parameters</ows:Identifier>"));
        assertTrue(xml.contains("<numRows>4320</numRows>"));
        assertTrue(xml.contains("<maskExpr>NOT INVALID</maskExpr>"));
        assertTrue(xml.contains("<fillValue>NaN</fillValue>"));
        assertTrue(xml.contains("<bbox>5,50,25,60</bbox>"));
        assertTrue(xml.contains("<numRows>4320</numRows>"));
        assertTrue(xml.contains("<superSampling>1</superSampling>"));

    }
}
