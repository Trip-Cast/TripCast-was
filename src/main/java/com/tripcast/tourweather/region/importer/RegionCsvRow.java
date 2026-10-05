package com.tripcast.tourweather.region.importer;

public record RegionCsvRow(
        String regionId,
        String sidoName,
        String name
) {
}
