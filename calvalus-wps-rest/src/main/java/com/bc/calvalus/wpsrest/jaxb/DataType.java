//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.08.11 at 03:37:15 PM CEST 
//


package com.bc.calvalus.wpsrest.jaxb;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * Identifies the form of this input or output value, and provides supporting information. 
 * 
 * <p>Java class for DataType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DataType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;choice>
 *         &lt;element name="ComplexData" type="{http://www.opengis.net/wps/1.0.0}ComplexDataType"/>
 *         &lt;element name="LiteralData" type="{http://www.opengis.net/wps/1.0.0}LiteralDataType"/>
 *         &lt;element name="BoundingBoxData" type="{http://www.opengis.net/ows/1.1}BoundingBoxType"/>
 *       &lt;/choice>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DataType", propOrder = {
    "complexData",
    "literalData",
    "boundingBoxData"
})
public class DataType {

    @XmlElement(name = "ComplexData")
    protected ComplexDataType complexData;
    @XmlElement(name = "LiteralData")
    protected LiteralDataType literalData;
    @XmlElement(name = "BoundingBoxData")
    protected BoundingBoxType boundingBoxData;

    /**
     * Gets the value of the complexData property.
     * 
     * @return
     *     possible object is
     *     {@link ComplexDataType }
     *     
     */
    public ComplexDataType getComplexData() {
        return complexData;
    }

    /**
     * Sets the value of the complexData property.
     * 
     * @param value
     *     allowed object is
     *     {@link ComplexDataType }
     *     
     */
    public void setComplexData(ComplexDataType value) {
        this.complexData = value;
    }

    /**
     * Gets the value of the literalData property.
     * 
     * @return
     *     possible object is
     *     {@link LiteralDataType }
     *     
     */
    public LiteralDataType getLiteralData() {
        return literalData;
    }

    /**
     * Sets the value of the literalData property.
     * 
     * @param value
     *     allowed object is
     *     {@link LiteralDataType }
     *     
     */
    public void setLiteralData(LiteralDataType value) {
        this.literalData = value;
    }

    /**
     * Gets the value of the boundingBoxData property.
     * 
     * @return
     *     possible object is
     *     {@link BoundingBoxType }
     *     
     */
    public BoundingBoxType getBoundingBoxData() {
        return boundingBoxData;
    }

    /**
     * Sets the value of the boundingBoxData property.
     * 
     * @param value
     *     allowed object is
     *     {@link BoundingBoxType }
     *     
     */
    public void setBoundingBoxData(BoundingBoxType value) {
        this.boundingBoxData = value;
    }

}