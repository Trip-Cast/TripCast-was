package com.tripcast.tourweather.climate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tripcast.tourweather.climate.client.TourClimateApiException;
import com.tripcast.tourweather.climate.client.TourWeatherIndexClient;
import com.tripcast.tourweather.climate.client.dto.TourClimateApiResponse.Item;
import com.tripcast.tourweather.climate.domain.RegionClimateIndex;
import com.tripcast.tourweather.climate.repository.RegionClimateIndexRepository;
import com.tripcast.tourweather.climate.util.ClimateRegionIdNormalizer;
import com.tripcast.tourweather.tourspot.course.TourCourseStopRepository;

@Service
@ConditionalOnClimateIndexSync
public class ClimateIndexSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(ClimateIndexSyncService.class);

    private final TourCourseStopRepository courseStopRepository;
    private final RegionClimateIndexRepository climateIndexRepository;
    private final TourWeatherIndexClient tourWeatherIndexClient;

    public ClimateIndexSyncService(
            TourCourseStopRepository courseStopRepository,
            RegionClimateIndexRepository climateIndexRepository,
            TourWeatherIndexClient tourWeatherIndexClient
    ) {
        this.courseStopRepository = courseStopRepository;
        this.climateIndexRepository = climateIndexRepository;
        this.tourWeatherIndexClient = tourWeatherIndexClient;
    }

    public ClimateIndexSyncResult sync(
            LocalDateTime currentDate,
            int forecastDays
    ) {
        List<String> regionIds = courseStopRepository
                .findDistinctRegionIds()
                .stream()
                .map(ClimateRegionIdNormalizer::normalize)
                .distinct()
                .toList();
        int successfulRegions = 0;
        int savedRecords = 0;

        for (String regionId : regionIds) {
            try {
                List<Item> items = tourWeatherIndexClient
                        .getCityTourClimateIndex(
                                regionId,
                                currentDate,
                                forecastDays
                        );
                for (Item item : items) {
                    saveOrUpdate(regionId, item);
                    savedRecords++;
                }
                successfulRegions++;
            } catch (TourClimateApiException exception) {
                if (exception.isNoData()) {
                    log.info(
                            "관광기후지수 데이터 없음. regionId={}",
                            regionId
                    );
                } else {
                    log.warn(
                            "관광기후지수 동기화 실패. regionId={}",
                            regionId,
                            exception
                    );
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "관광기후지수 동기화 실패. regionId={}",
                        regionId,
                        exception
                );
            }
        }

        return new ClimateIndexSyncResult(
                regionIds.size(),
                successfulRegions,
                savedRecords,
                regionIds.size() - successfulRegions
        );
    }

    private void saveOrUpdate(String requestedRegionId, Item item) {
        String regionId = hasText(item.cityAreaId())
                ? item.cityAreaId()
                : requestedRegionId;
        LocalDate baseDate = parseBaseDate(item.tm());
        BigDecimal score = parseScore(item.kmaTci());
        String grade = requireText(item.tciGrade(), "tciGrade");

        RegionClimateIndex climateIndex = climateIndexRepository
                .findByRegionIdAndBaseDate(regionId, baseDate)
                .map(existing -> {
                    existing.update(score, grade);
                    return existing;
                })
                .orElseGet(() -> new RegionClimateIndex(
                        regionId,
                        baseDate,
                        score,
                        grade
                ));

        climateIndexRepository.save(climateIndex);
    }

    private LocalDate parseBaseDate(String value) {
        String tm = requireText(value, "tm");
        try {
            if (tm.length() >= 10 && tm.charAt(4) == '-') {
                return LocalDate.parse(tm.substring(0, 10));
            }
            if (tm.length() >= 8) {
                return LocalDate.parse(
                        tm.substring(0, 8),
                        DateTimeFormatter.BASIC_ISO_DATE
                );
            }
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "관광기후지수 예보시각 형식이 올바르지 않습니다: " + tm,
                    exception
            );
        }
        throw new IllegalArgumentException(
                "관광기후지수 예보시각 형식이 올바르지 않습니다: " + tm
        );
    }

    private BigDecimal parseScore(String value) {
        String score = requireText(value, "kmaTci");
        try {
            return new BigDecimal(score);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "관광기후지수 점수 형식이 올바르지 않습니다: " + score,
                    exception
            );
        }
    }

    private String requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(
                    "관광기후지수 응답 필드가 비어 있습니다: " + fieldName
            );
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
