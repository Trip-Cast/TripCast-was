package com.tripcast.tourweather;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.tripcast.tourweather.climate.domain.RegionClimateIndex;
import com.tripcast.tourweather.climate.repository.RegionClimateIndexRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Transactional
class MySqlFlywayMigrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RegionClimateIndexRepository regionClimateIndexRepository;

    @Test
    void 빈_MySQL에서_V1부터_V4까지_모두_성공한다() {
        assertEquals("4", flyway.info().current().getVersion().getVersion());
        assertEquals(4, flyway.info().applied().length);
    }

    @Test
    void spot_weather_index는_V3에서_삭제되어_존재하지_않는다() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name = 'spot_weather_index'",
                Integer.class
        );

        assertEquals(0, count);
    }

    @Test
    void score_컬럼이_DECIMAL_5_2로_생성된다() {
        var row = jdbcTemplate.queryForMap(
                "SELECT DATA_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name = 'region_climate_index' "
                        + "AND column_name = 'score'"
        );

        assertEquals("decimal", row.get("DATA_TYPE"));
        assertEquals(5L, ((Number) row.get("NUMERIC_PRECISION")).longValue());
        assertEquals(2L, ((Number) row.get("NUMERIC_SCALE")).longValue());
    }

    @Test
    void 코스_정류장_FK와_유니크_제약이_실제_MySQL에_생성된다() {
        Integer fkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name = 'tour_course_stop' "
                        + "AND constraint_type = 'FOREIGN KEY'",
                Integer.class
        );
        Integer uniqueCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name = 'tour_course_stop' "
                        + "AND constraint_type = 'UNIQUE'",
                Integer.class
        );

        assertEquals(2, fkCount);
        assertEquals(2, uniqueCount);
    }

    @Test
    void score는_실제_MySQL에서도_소수점을_손실없이_저장한다() {
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
