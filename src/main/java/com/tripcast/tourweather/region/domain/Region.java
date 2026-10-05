package com.tripcast.tourweather.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "region")
public class Region {

    @Id
    @Column(name = "region_id", nullable = false, length = 10)
    private String regionId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "sido_name", nullable = false, length = 50)
    private String sidoName;

    protected Region() {
    }

    public Region(String regionId, String name, String sidoName) {
        this.regionId = regionId;
        this.name = name;
        this.sidoName = sidoName;
    }

    public void update(String name, String sidoName) {
        this.name = name;
        this.sidoName = sidoName;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getName() {
        return name;
    }

    public String getSidoName() {
        return sidoName;
    }
}
