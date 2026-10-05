package com.tripcast.tourweather.region.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.tripcast.tourweather.climate.util.ClimateRegionIdNormalizer;
import com.tripcast.tourweather.tourspot.importer.TourSpotCsvReader;
import com.tripcast.tourweather.tourspot.importer.TourSpotCsvRow;

class RegionCsvReaderTest {

    private final RegionCsvReader csvReader = new RegionCsvReader();

    @Test
    void 지역_csv를_읽는다() {
        List<RegionCsvRow> rows = csvReader.read();

        assertEquals(198, rows.size());
        assertTrue(rows.contains(
                new RegionCsvRow("5013000000", "제주특별자치도", "서귀포시")
        ));
        assertTrue(rows.contains(
                new RegionCsvRow("4511100000", "전북특별자치도", "전주시 완산구")
        ));
    }

    @Test
    void 지역_코드는_중복이_없고_모두_10자리_시군구_코드다() {
        List<RegionCsvRow> rows = csvReader.read();
        Set<String> regionIds = rows.stream()
                .map(RegionCsvRow::regionId)
                .collect(Collectors.toSet());

        assertEquals(rows.size(), regionIds.size());
        assertTrue(regionIds.stream()
                .allMatch(id -> id.matches("\\d{5}00000")));
        assertTrue(rows.stream().allMatch(row ->
                !row.name().isBlank() && !row.sidoName().isBlank()));
    }

    @Test
    void 관광지_csv에_나오는_모든_시군구가_지역_csv에_이름이_있다() {
        Set<String> namedRegionIds = csvReader.read().stream()
                .map(RegionCsvRow::regionId)
                .collect(Collectors.toSet());
        Set<String> usedRegionIds = new TourSpotCsvReader().read().stream()
                .map(TourSpotCsvRow::regionId)
                .map(ClimateRegionIdNormalizer::normalize)
                .collect(Collectors.toSet());

        Set<String> missing = usedRegionIds.stream()
                .filter(id -> !namedRegionIds.contains(id))
                .collect(Collectors.toSet());

        assertTrue(missing.isEmpty(), "이름이 없는 지역 코드: " + missing);
    }
}
