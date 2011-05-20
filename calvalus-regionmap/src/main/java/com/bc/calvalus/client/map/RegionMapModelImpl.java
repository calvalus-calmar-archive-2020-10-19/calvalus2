package com.bc.calvalus.client.map;

import com.google.gwt.view.client.ListDataProvider;

import java.util.List;

/**
 * The default impl. of {@link RegionMapModel}.
 *
 * @author Norman Fomferra
 */
public class RegionMapModelImpl implements RegionMapModel {

    private final ListDataProvider<Region> regionProvider;
    private final MapAction[] mapActions;

    public RegionMapModelImpl(List<Region> regionList, MapAction... mapActions) {
        this.mapActions = mapActions;
        this.regionProvider = new ListDataProvider<Region>(regionList);

    }

    @Override
    public MapAction[] getActions() {
        return mapActions;
    }

    @Override
    public ListDataProvider<Region> getRegionProvider() {
        return regionProvider;
    }
}
