package com.tripcast.tourweather.region.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tripcast.tourweather.region.domain.Region;
import com.tripcast.tourweather.region.repository.RegionRepository;

@Component
@ConditionalOnProperty(
        name = "app.region-import.enabled",
        havingValue = "true"
)
public class RegionDataImporter implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(RegionDataImporter.class);

    private final RegionCsvReader csvReader;
    private final RegionRepository regionRepository;

    public RegionDataImporter(
            RegionCsvReader csvReader,
            RegionRepository regionRepository
    ) {
        this.csvReader = csvReader;
        this.regionRepository = regionRepository;
    }

    /**
     * CSV를 기준으로 region 테이블을 맞춘다. 여러 번 실행해도 결과가 같다(upsert).
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int savedCount = 0;
        int updatedCount = 0;

        for (RegionCsvRow row : csvReader.read()) {
            var existing = regionRepository.findById(row.regionId());

            if (existing.isPresent()) {
                existing.get().update(row.name(), row.sidoName());
                updatedCount++;
            } else {
                regionRepository.save(new Region(
                        row.regionId(),
                        row.name(),
                        row.sidoName()
                ));
                savedCount++;
            }
        }

        log.info(
                "지역 CSV 적재 완료: 저장={}, 갱신={}",
                savedCount,
                updatedCount
        );
    }
}
