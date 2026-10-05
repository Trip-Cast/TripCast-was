package com.tripcast.tourweather.region;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripcast.tourweather.climate.util.ClimateRegionIdNormalizer;
import com.tripcast.tourweather.region.domain.Region;
import com.tripcast.tourweather.region.dto.RegionResponse;
import com.tripcast.tourweather.region.repository.RegionRepository;
import com.tripcast.tourweather.tourspot.course.TourCourseStopRepository;

@Service
@Transactional(readOnly = true)
public class RegionService {

    private static final Logger log =
            LoggerFactory.getLogger(RegionService.class);

    private final RegionRepository regionRepository;
    private final TourCourseStopRepository courseStopRepository;

    public RegionService(
            RegionRepository regionRepository,
            TourCourseStopRepository courseStopRepository
    ) {
        this.regionRepository = regionRepository;
        this.courseStopRepository = courseStopRepository;
    }

    /**
     * 관광코스에 등록된 시군구 중 이름이 있는 지역을 코드 오름차순으로 반환한다.
     * keyword가 있으면 "시도 시군구" 이름에 부분 일치하는 지역만 남긴다.
     */
    public List<RegionResponse> getRegions(String keyword) {
        Set<String> registeredRegionIds = courseStopRepository
                .findDistinctRegionIds()
                .stream()
                .map(ClimateRegionIdNormalizer::normalize)
                .collect(Collectors.toCollection(TreeSet::new));

        if (registeredRegionIds.isEmpty()) {
            return List.of();
        }

        List<Region> regions = regionRepository
                .findAllByRegionIdInOrderByRegionIdAsc(registeredRegionIds);
        warnIfNamesAreMissing(registeredRegionIds, regions);

        String needle = compact(keyword);

        return regions.stream()
                .map(RegionResponse::from)
                .filter(region -> needle.isEmpty()
                        || compact(region.displayName()).contains(needle))
                .toList();
    }

    private void warnIfNamesAreMissing(
            Set<String> registeredRegionIds,
            List<Region> regions
    ) {
        Set<String> missing = new TreeSet<>(registeredRegionIds);
        regions.forEach(region -> missing.remove(region.getRegionId()));

        if (!missing.isEmpty()) {
            log.warn(
                    "이름이 등록되지 않아 지역 목록에서 제외된 코드: {}",
                    missing
            );
        }
    }

    private String compact(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
