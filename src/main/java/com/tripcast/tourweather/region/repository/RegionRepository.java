package com.tripcast.tourweather.region.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tripcast.tourweather.region.domain.Region;

public interface RegionRepository extends JpaRepository<Region, String> {

    List<Region> findAllByRegionIdInOrderByRegionIdAsc(
            Collection<String> regionIds
    );
}
