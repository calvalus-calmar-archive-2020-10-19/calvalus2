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

package com.bc.calvalus.portal.client;

import com.bc.calvalus.portal.client.map.Region;
import com.bc.calvalus.portal.client.map.RegionMap;
import com.bc.calvalus.portal.client.map.RegionMapWidget;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.maps.client.geom.LatLngBounds;
import com.google.gwt.maps.client.overlay.Polygon;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiFactory;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.datepicker.client.DateBox;
import com.google.gwt.view.client.SelectionChangeEvent;

import java.util.*;

/**
 * Demo view that lets users submit a new L2 production.
 *
 * @author Norman
 */
public class ProductSetFilterForm extends Composite {

    private final PortalContext portal;

    interface TheUiBinder extends UiBinder<Widget, ProductSetFilterForm> {
    }

    private static TheUiBinder uiBinder = GWT.create(TheUiBinder.class);

    private static final DateTimeFormat DATE_FORMAT = DateTimeFormat.getFormat("yyyy-MM-dd");

    @UiField
    RadioButton temporalFilterOff;
    @UiField
    RadioButton temporalFilterByDateList;
    @UiField
    RadioButton temporalFilterByDateRange;

    @UiField
    DateBox minDate;
    @UiField
    DateBox maxDate;
    @UiField
    TextBox numDays;

    @UiField
    TextArea dateList;

    @UiField
    RadioButton spatialFilterOff;
    @UiField
    RadioButton spatialFilterByRegion;
    @UiField
    Anchor manageRegionsAnchor;
    @UiField
    RegionMapWidget regionMap;

    static int radioGroupId;

    public ProductSetFilterForm(final PortalContext portal) {
        this.portal = portal;

        initWidget(uiBinder.createAndBindUi(this));

        radioGroupId++;

        minDate.setFormat(new DateBox.DefaultFormat(DATE_FORMAT));
        minDate.setValue(DATE_FORMAT.parse("2008-06-01"));

        maxDate.setFormat(new DateBox.DefaultFormat(DATE_FORMAT));
        maxDate.setValue(DATE_FORMAT.parse("2008-06-10"));

        dateList.setEnabled(false);
        dateList.setValue("2008-06-01\n" +
                                  "2008-06-02\n" +
                                  "2008-06-03");

        temporalFilterOff.setName("temporalFilter" + radioGroupId);
        temporalFilterByDateRange.setName("temporalFilter" + radioGroupId);
        temporalFilterByDateList.setName("temporalFilter" + radioGroupId);
        temporalFilterByDateRange.setValue(true);
        temporalFilterByDateRange.addValueChangeHandler(new TimeSelValueChangeHandler());
        temporalFilterByDateList.addValueChangeHandler(new TimeSelValueChangeHandler());

        spatialFilterOff.setName("spatialFilter" + radioGroupId);
        spatialFilterByRegion.setName("spatialFilter" + radioGroupId);
        spatialFilterByRegion.setValue(true);

        manageRegionsAnchor.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                portal.showView(ManageRegionsView.ID);
            }
        });

        addChangeHandler(new ChangeHandler() {
            @Override
            public void temporalFilterChanged(Map<String, String> data) {
                updateNumDays();
            }

            @Override
            public void spatialFilterChanged(Map<String, String> data) {
            }
        });

        updateNumDays();
    }

    private void updateNumDays() {
        if (temporalFilterByDateRange.getValue()) {
            long millisPerDay = 24L * 60L * 60L * 1000L;
            Date min = minDate.getValue();
            Date max = maxDate.getValue();
            numDays.setValue("" +((millisPerDay + max.getTime()) - min.getTime()) / millisPerDay);
        } else if (temporalFilterByDateList.getValue()) {
            String[] splits = dateList.getValue().split("\\s");
            HashSet<String> set = new HashSet<String>(Arrays.asList(splits));
            numDays.setValue("" + set.size());
        } else {
            numDays.setValue("?");
        }
    }

    public void addChangeHandler(final ChangeHandler changeHandler) {
        ValueChangeHandler<Date> dateValueChangeHandler = new ValueChangeHandler<Date>() {
            @Override
            public void onValueChange(ValueChangeEvent<Date> event) {
                changeHandler.temporalFilterChanged(getValueMap());
            }
        };
        minDate.addValueChangeHandler(dateValueChangeHandler);
        maxDate.addValueChangeHandler(dateValueChangeHandler);
        ValueChangeHandler<Boolean> booleanValueChangeHandler = new ValueChangeHandler<Boolean>() {
            @Override
            public void onValueChange(ValueChangeEvent<Boolean> booleanValueChangeEvent) {
                changeHandler.temporalFilterChanged(getValueMap());
            }
        };
        temporalFilterOff.addValueChangeHandler(booleanValueChangeHandler);
        temporalFilterByDateRange.addValueChangeHandler(booleanValueChangeHandler);
        temporalFilterByDateList.addValueChangeHandler(booleanValueChangeHandler);
        dateList.addValueChangeHandler(new ValueChangeHandler<String>() {
            @Override
            public void onValueChange(ValueChangeEvent<String> stringValueChangeEvent) {
                changeHandler.temporalFilterChanged(getValueMap());
            }
        });

        ValueChangeHandler<Boolean> spatialFilterChangeHandler = new ValueChangeHandler<Boolean>() {
            @Override
            public void onValueChange(ValueChangeEvent<Boolean> booleanValueChangeEvent) {
                changeHandler.spatialFilterChanged(getValueMap());
            }
        };
        spatialFilterOff.addValueChangeHandler(spatialFilterChangeHandler);
        spatialFilterByRegion.addValueChangeHandler(spatialFilterChangeHandler);
        regionMap.getRegionMapSelectionModel().addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(SelectionChangeEvent selectionChangeEvent) {
                changeHandler.spatialFilterChanged(getValueMap());
            }
        });
    }

    @UiFactory
    public RegionMapWidget createRegionMap() { // method name is insignificant
        return new RegionMapWidget(portal.getRegionMapModel(), false);
    }

    public HasValue<Date> getMinDate() {
        return minDate;
    }

    public HasValue<Date> getMaxDate() {
        return maxDate;
    }

    public Region getSelectedRegion() {
        return spatialFilterByRegion.getValue() ? regionMap.getRegionMapSelectionModel().getSelectedRegion() : null;
    }

    public RegionMap getRegionMap() {
        return regionMap;
    }

    public void validateForm() throws ValidationException {

        if (temporalFilterByDateRange.getValue()) {
            Date min = minDate.getValue();
            Date max = maxDate.getValue();
            if (min.after(max)) {
                throw new ValidationException(dateList, "Start date must be before end date.");
            }
        } else if (temporalFilterByDateList.getValue()) {
            String value = dateList.getValue().trim();
            if (value.isEmpty()) {
                throw new ValidationException(dateList, "Date list must not be empty.");
            }
        }

        if (spatialFilterByRegion.getValue()) {
            Region region = getSelectedRegion();
            if (region == null) {
                throw new ValidationException(regionMap, "Please select a region.");
            }
        }
    }

    public Map<String, String> getValueMap() {

        Map<String, String> parameters = new HashMap<String, String>();

        if (temporalFilterOff.getValue()) {
            // ok
        } else if (temporalFilterByDateRange.getValue()) {
            parameters.put("minDate", minDate.getFormat().format(minDate, minDate.getValue()));
            parameters.put("maxDate", maxDate.getFormat().format(maxDate, maxDate.getValue()));
        } else if (temporalFilterByDateList.getValue()) {
            parameters.put("dateList", dateList.getValue());
        }

        if (spatialFilterOff.getValue()) {
            parameters.put("regionName", "global.World");
            parameters.put("regionWKT", "POLYGON((-180 -90, 180 -90, 180 90, -180 90, -180 -90))");
            parameters.put("minLon", "-180");
            parameters.put("minLat", "-90");
            parameters.put("maxLon", "180");
            parameters.put("maxLat", "90");
        } else if (spatialFilterByRegion.getValue()) {
            Region region = getSelectedRegion();
            if (region != null) {
                Polygon polygon = region.createPolygon();
                LatLngBounds bounds = polygon.getBounds();
                parameters.put("regionName", region.getQualifiedName());
                parameters.put("regionWKT", region.getGeometryWkt());
                parameters.put("minLon", bounds.getNorthEast().getLongitude() + "");
                parameters.put("minLat", bounds.getNorthEast().getLatitude() + "");
                parameters.put("maxLon", bounds.getSouthWest().getLongitude() + "");
                parameters.put("maxLat", bounds.getSouthWest().getLatitude() + "");
            }
        }

        return parameters;
    }

    public interface ChangeHandler {
        void temporalFilterChanged(Map<String, String> data);

        void spatialFilterChanged(Map<String, String> data);
    }

    private class TimeSelValueChangeHandler implements ValueChangeHandler<Boolean> {
        @Override
        public void onValueChange(ValueChangeEvent<Boolean> booleanValueChangeEvent) {
            minDate.setEnabled(temporalFilterByDateRange.getValue());
            maxDate.setEnabled(temporalFilterByDateRange.getValue());
            dateList.setEnabled(temporalFilterByDateList.getValue());

            regionMap.setEnabled(spatialFilterByRegion.getValue());
        }
    }
}