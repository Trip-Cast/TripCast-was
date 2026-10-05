package com.tripcast.tourweather.region;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.tripcast.tourweather.region.domain.Region;
import com.tripcast.tourweather.region.importer.RegionDataImporter;
import com.tripcast.tourweather.region.repository.RegionRepository;

@SpringBootTest(properties = "app.region-import.enabled=true")
@Transactional
class RegionImportIntegrationTest {

    @Autowired
    private RegionDataImporter importer;

    @Autowired
    private RegionRepository regionRepository;

    @Test
    void 지역_csv를_적재하고_다시_실행해도_중복이_생기지_않는다() {
        importer.run(new DefaultApplicationArguments());
        assertEquals(198, regionRepository.count());

        importer.run(new DefaultApplicationArguments());
        assertEquals(198, regionRepository.count());
    }

    @Test
    void 이름이_바뀐_지역은_CSV_기준으로_갱신한다() {
        regionRepository.save(new Region("5013000000", "옛이름", "옛시도"));

        importer.run(new DefaultApplicationArguments());

        Region region = regionRepository.findById("5013000000").orElseThrow();
        assertEquals("서귀포시", region.getName());
        assertEquals("제주특별자치도", region.getSidoName());
    }

    @Test
    void 적재한_지역을_코드_순서로_조회한다() {
        importer.run(new DefaultApplicationArguments());

        List<Region> regions = regionRepository
                .findAllByRegionIdInOrderByRegionIdAsc(
                        List.of("5013000000", "1111000000", "5011000000")
                );

        assertEquals(
                List.of("1111000000", "5011000000", "5013000000"),
                regions.stream().map(Region::getRegionId).toList()
        );
    }
}
