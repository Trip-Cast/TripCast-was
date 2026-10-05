package com.tripcast.tourweather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.tripcast.tourweather.climate.domain.RegionClimateIndex;
import com.tripcast.tourweather.climate.repository.RegionClimateIndexRepository;

@SpringBootTest
@Transactional
class FlywayMigrationTest {

    private static final List<String> MANAGED_TABLES = List.of(
            "tour_spot",
            "tour_course",
            "tour_course_stop",
            "region_climate_index",
            "region"
    );

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RegionClimateIndexRepository regionClimateIndexRepository;

    @Test
    void V1부터_V4까지_실패없이_모두_적용된다() {
        MigrationInfo[] all = flyway.info().all();

        assertTrue(Arrays.stream(all)
                .noneMatch(info -> info.getState() == MigrationState.FAILED));

        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion().getVersion())
                .toList();

        assertEquals(List.of("1", "2", "3", "4"), appliedVersions);
        assertEquals("4", flyway.info().current().getVersion().getVersion());
    }

    @Test
    void 다섯_테이블이_모두_생성된다() {
        for (String table : MANAGED_TABLES) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE LOWER(table_name) = LOWER(?)",
                    Integer.class,
                    table
            );
            assertEquals(1, count, table + " 테이블이 없습니다.");
        }
    }

    @Test
    void spot_weather_index는_V3에서_삭제되어_존재하지_않는다() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE LOWER(table_name) = LOWER('spot_weather_index')",
                Integer.class
        );

        assertEquals(0, count);
    }

    @Test
    void flyway_schema_history에_V1부터_V4까지_성공_이력이_있다() {
        List<MigrationInfo> applied = Arrays.asList(flyway.info().applied());

        assertEquals(4, applied.size());
        assertTrue(applied.stream()
                .allMatch(info -> info.getState() == MigrationState.SUCCESS));
    }

    @Test
    void score는_소수점을_손실없이_저장하고_조회한다() {
        RegionClimateIndex saved = regionClimateIndexRepository.save(
                new RegionClimateIndex(
                        "9999000000",
                        LocalDate.of(2026, 9, 21),
                        new BigDecimal("0.44"),
                        "매우좋음"
                )
        );

        RegionClimateIndex found = regionClimateIndexRepository
                .findById(saved.getId())
                .orElseThrow();

        assertEquals(new BigDecimal("0.44"), found.getScore());
    }
}
