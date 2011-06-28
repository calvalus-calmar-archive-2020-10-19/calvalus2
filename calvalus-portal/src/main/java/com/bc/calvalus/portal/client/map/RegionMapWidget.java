package com.bc.calvalus.portal.client.map;

import com.bc.calvalus.portal.client.map.actions.*;
import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.Cell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.maps.client.MapWidget;
import com.google.gwt.maps.client.control.MapTypeControl;
import com.google.gwt.maps.client.event.PolygonLineUpdatedHandler;
import com.google.gwt.maps.client.overlay.PolyStyleOptions;
import com.google.gwt.maps.client.overlay.Polygon;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.CellList;
import com.google.gwt.user.cellview.client.HasKeyboardPagingPolicy;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.ResizeComposite;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SplitLayoutPanel;
import com.google.gwt.view.client.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An implementation of a Google map that has regions.
 *
 * @author Norman Fomferra
 */
public class RegionMapWidget extends ResizeComposite implements RegionMap {

    private final RegionMapModel regionMapModel;
    private final RegionMapSelectionModel regionMapSelectionModel;
    private MapWidget mapWidget;
    private boolean adjustingRegionSelection;
    private MapInteraction currentInteraction;

    private boolean editable;
    private Map<Region, Polygon> polygonMap;
    private Map<Polygon, Region> regionMap;

    private PolyStyleOptions normalPolyStrokeStyle;
    private PolyStyleOptions normalPolyFillStyle;
    private PolyStyleOptions selectedPolyStrokeStyle;
    private PolyStyleOptions selectedPolyFillStyle;
    private RegionMapToolbar regionMapToolbar;
    private MyPolygonLineUpdatedHandler polygonLineUpdatedHandler;

    public static RegionMapWidget create(ListDataProvider<Region> regionList, boolean editable) {
        final RegionMapModelImpl model;
        if (editable) {
            model = new RegionMapModelImpl(regionList, createDefaultEditingActions());
        } else {
            model = new RegionMapModelImpl(regionList);
        }
        return new RegionMapWidget(model, editable);
    }

    public RegionMapWidget(RegionMapModel regionMapModel, boolean editable) {
        this(regionMapModel, new RegionMapSelectionModelImpl(), editable);
    }

    public RegionMapWidget(RegionMapModel regionMapModel, RegionMapSelectionModel regionMapSelectionModel, boolean editable) {
        this.regionMapModel = regionMapModel;
        this.regionMapSelectionModel = regionMapSelectionModel;
        this.editable = editable;
        this.normalPolyStrokeStyle = PolyStyleOptions.newInstance("#0000FF", 3, 0.8);
        this.normalPolyFillStyle = PolyStyleOptions.newInstance("#0000FF", 3, 0.2);
        this.selectedPolyStrokeStyle = PolyStyleOptions.newInstance("#FFFF00", 3, 0.8);
        this.selectedPolyFillStyle = normalPolyFillStyle;
        polygonLineUpdatedHandler = new MyPolygonLineUpdatedHandler();
        polygonMap = new HashMap<Region, Polygon>();
        regionMap = new HashMap<Polygon, Region>();
        initUi();
    }

    @Override
    public RegionMapModel getRegionModel() {
        return regionMapModel;
    }

    @Override
    public RegionMapSelectionModel getRegionSelectionModel() {
        return regionMapSelectionModel;
    }

    @Override
    public MapWidget getMapWidget() {
        return mapWidget;
    }

    @Override
    public Polygon getPolygon(Region region) {
        return polygonMap.get(region);
    }

    @Override
    public Region getRegion(String qualifiedName) {
        List<Region> regionList = getRegionModel().getRegionProvider().getList();
        for (Region region : regionList) {
            if (region.getQualifiedName().equalsIgnoreCase(qualifiedName)) {
                return region;
            }
        }
        return null;
    }

    @Override
    public Region getRegion(Polygon polygon) {
        return regionMap.get(polygon);
    }

    @Override
    public void addRegion(Region region) {
        if (!getRegionModel().getRegionProvider().getList().contains(region)) {
            getRegionModel().getRegionProvider().getList().add(0, region);
            getRegionSelectionModel().clearSelection();
            getRegionSelectionModel().setSelected(region, true);
            getRegionModel().fireRegionAdded(this, region);
        }
    }

    @Override
    public void removeRegion(Region region) {
        if (getRegionModel().getRegionProvider().getList().remove(region)) {
            getRegionSelectionModel().setSelected(region, false);
            getRegionModel().fireRegionRemoved(this, region);
        }
    }

    @Override
    public MapInteraction getCurrentInteraction() {
        return currentInteraction;
    }

    @Override
    public void setCurrentInteraction(MapInteraction interaction) {
        if (currentInteraction != interaction) {
            if (currentInteraction != null) {
                currentInteraction.detachFrom(this);
                if (regionMapToolbar != null) {
                    regionMapToolbar.deselect(currentInteraction);
                }
            }
            currentInteraction = interaction;
            if (currentInteraction != null) {
                currentInteraction.attachTo(this);
                if (regionMapToolbar != null) {
                    regionMapToolbar.select(currentInteraction);
                }
            }
        }
    }

    private void initUi() {

        mapWidget = new MapWidget();
        mapWidget.setDoubleClickZoom(true);
        mapWidget.setScrollWheelZoomEnabled(true);
        mapWidget.addControl(new MapTypeControl());
        // Other possible MapWidget Controls are:
        // mapWidget.addControl(new SmallMapControl());
        // mapWidget.addControl(new OverviewMapControl());

        Cell<Region> regionCell = new AbstractCell<Region>() {
            @Override
            public void render(Context context, Region value, SafeHtmlBuilder sb) {
                sb.appendHtmlConstant(value.getName());
            }
        };

        ProvidesKey<Region> regionKey = new ProvidesKey<Region>() {
            @Override
            public Object getKey(Region item) {
                return item.getQualifiedName();
            }
        };

        final CellList<Region> regionCellList = new CellList<Region>(regionCell, regionKey);
        regionCellList.setVisibleRange(0, 256);
        regionCellList.setKeyboardPagingPolicy(HasKeyboardPagingPolicy.KeyboardPagingPolicy.INCREASE_RANGE);
        regionCellList.setKeyboardSelectionPolicy(HasKeyboardSelectionPolicy.KeyboardSelectionPolicy.ENABLED);
        regionMapModel.getRegionProvider().addDataDisplay(regionCellList);

        // Add a selection model so we can select cells.
        final SingleSelectionModel<Region> regionListSelectionModel = new SingleSelectionModel<Region>(regionKey);
        regionCellList.setSelectionModel(regionListSelectionModel);
        regionCellList.getSelectionModel().addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(SelectionChangeEvent event) {
                if (!adjustingRegionSelection) {
                    try {
                        adjustingRegionSelection = true;
                        updateRegionSelection(regionListSelectionModel, regionMapSelectionModel);
                        if (!editable) {
                            new LocateRegionsAction().run(RegionMapWidget.this);
                        }
                    } finally {
                        adjustingRegionSelection = false;
                    }
                }
            }
        });

        regionMapSelectionModel.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(SelectionChangeEvent event) {
                if (!adjustingRegionSelection) {
                    try {
                        adjustingRegionSelection = true;
                        updateRegionSelection(regionMapSelectionModel, regionListSelectionModel);
                        updatePolygonStyles();
                    } finally {
                        adjustingRegionSelection = false;
                    }
                }
            }
        });

        ScrollPanel regionScrollPanel = new ScrollPanel(regionCellList);

        DockLayoutPanel regionPanel = new DockLayoutPanel(Style.Unit.EM);
        regionPanel.ensureDebugId("regionPanel");

        if (getRegionModel().getActions().length > 0) {
            regionMapToolbar = new RegionMapToolbar(this);
            regionPanel.addSouth(regionMapToolbar, 3.5);
        }
        regionPanel.add(regionScrollPanel);

        SplitLayoutPanel regionSplitLayoutPanel = new SplitLayoutPanel();
        regionSplitLayoutPanel.ensureDebugId("regionSplitLayoutPanel");
        regionSplitLayoutPanel.addWest(regionPanel, 180);
        regionSplitLayoutPanel.add(mapWidget);

        if (getCurrentInteraction() == null) {
            setCurrentInteraction(createSelectInteraction());
        }

        updatePolygonStyles();
        initWidget(regionSplitLayoutPanel);

        getRegionModel().addChangeListener(new RegionMapModel.ChangeListener() {

            @Override
            public void onRegionAdded(RegionMapModel.ChangeEvent event) {
                ensurePolygonPresent(event.getRegion());
            }

            @Override
            public void onRegionRemoved(RegionMapModel.ChangeEvent event) {
                ensurePolygonAbsent(event.getRegion());
            }

            @Override
            public void onRegionChanged(RegionMapModel.ChangeEvent event) {
                if (event.getRegionMap() != RegionMapWidget.this) {
                    ensurePolygonAbsent(event.getRegion());
                    ensurePolygonPresent(event.getRegion());
                }
            }
        });
    }

    private Polygon ensurePolygonPresent(Region region) {
        Polygon polygon = polygonMap.get(region);
        if (polygon == null) {
            polygon = region.createPolygon();
            polygon.setVisible(true);
            regionMap.put(polygon, region);
            polygonMap.put(region, polygon);
            mapWidget.addOverlay(polygon);
            updatePolygonStyle(region, polygon);
        }
        return polygon;
    }

    private Polygon ensurePolygonAbsent(Region region) {
        Polygon polygon = polygonMap.get(region);
        if (polygon != null) {
            mapWidget.removeOverlay(polygon);
            regionMap.remove(polygon);
            polygonMap.remove(region);
        }
        return polygon;
    }

    private void updatePolygonStyles() {
        List<Region> regionList = regionMapModel.getRegionProvider().getList();
        for (Region region : regionList) {
            updatePolygonStyle(region, ensurePolygonPresent(region));
        }
    }

    private void updatePolygonStyle(Region region, Polygon polygon) {
        boolean selected = regionMapSelectionModel.isSelected(region);
        polygon.setStrokeStyle(selected ? selectedPolyStrokeStyle : normalPolyStrokeStyle);
        polygon.setFillStyle(selected ? selectedPolyFillStyle : normalPolyFillStyle);
        if (editable && region.isUserRegion()) {
            polygon.setEditingEnabled(selected);
            if (selected) {
                polygon.addPolygonLineUpdatedHandler(polygonLineUpdatedHandler);
            } else {
                polygon.removePolygonLineUpdatedHandler(polygonLineUpdatedHandler);
            }
        }
    }

    public void applyVertexChanges() {
        GWT.log("Applying vertex changes in user regions...");
        List<Region> regionList = regionMapModel.getRegionProvider().getList();
        for (Region region : regionList) {
            if (region.isUserRegion()) {
                Polygon polygon = polygonMap.get(region);
                if (polygon != null) {
                    region.setVertices(Region.getVertices(polygon));
                }
            }
        }
    }

    private void updateRegionSelection(SelectionModel<Region> source, SelectionModel<Region> target) {
        for (Region region : regionMapModel.getRegionProvider().getList()) {
            target.setSelected(region, source.isSelected(region));
        }
    }

    private static MapAction[] createDefaultEditingActions() {
        // todo: use the action constructor that takes an icon image (nf)
        final SelectInteraction selectInteraction = createSelectInteraction();
        return new MapAction[]{
                selectInteraction,
                new InsertPolygonInteraction(new AbstractMapAction("P", "New polygon region") {
                    @Override
                    public void run(RegionMap regionMap) {
                        regionMap.setCurrentInteraction(selectInteraction);
                    }
                }),
                new InsertBoxInteraction(new AbstractMapAction("B", "New box region") {
                    @Override
                    public void run(RegionMap regionMap) {
                        regionMap.setCurrentInteraction(selectInteraction);
                    }
                }),
                MapAction.SEPARATOR,
                new RenameRegionAction(),
                new DeleteRegionsAction(),
                new LocateRegionsAction(),
                new ShowRegionInfoAction()
        };
    }

    private static SelectInteraction createSelectInteraction() {
        return new SelectInteraction(new AbstractMapAction("S", "Select region") {
            @Override
            public void run(RegionMap regionMap) {
            }
        });
    }

    private class MyPolygonLineUpdatedHandler implements PolygonLineUpdatedHandler {
        @Override
        public void onUpdate(PolygonLineUpdatedEvent event) {
            System.out.println("PolygonLineUpdatedEvent: onUpdate: event = " + event);
            Polygon polygon = event.getSender();
            Region region = regionMap.get(polygon);
            if (region != null) {
                getRegionModel().fireRegionChanged(RegionMapWidget.this, region);
            }
        }
    }
}
