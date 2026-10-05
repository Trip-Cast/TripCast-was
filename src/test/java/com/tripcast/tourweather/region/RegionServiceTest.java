package com.tripcast.tourweather.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripcast.tourweather.region.domain.Region;
import com.tripcast.tourweather.region.dto.RegionResponse;
import com.tripcast.tourweather.region.repository.RegionRepository;
import com.tripcast.tourweather.tourspot.course.TourCourseStopRepository;

@ExtendWith(MockitoExtension.class)
class RegionServiceTest {

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private TourCourseStopRepository courseStopRepository;

    @InjectMocks
    private RegionService regionService;

    @Test
    void 읍면동_코드는_시군구로_정규화하고_중복을_제거해_조회한다() {
        when(courseStopRepository.findDistinctRegionIds())
                .thenReturn(List.of("4822051000", "4822052000", "5013000000"));
        when(regionRepository.findAllByRegionIdInOrderByRegionIdAsc(
                Set.of("4822000000", "5013000000")
        )).thenReturn(List.of(
                new Region("4822000000", "통영시", "경상남도"),
                new Region("5013000000", "서귀포시", "제주특별자치도")
        ));

        List<RegionResponse> result = regionService.getRegions(null);

        assertEquals(2, result.size());
        assertEquals("4822000000", result.get(0).regionId());
        assertEquals("경상남도 통영시", result.get(0).displayName());
        assertEquals("5013000000", result.get(1).regionId());
    }

    @Test
    void 이름이_등록되지_않은_코드는_목록에서_제외한다() {
        when(courseStopRepository.findDistinctRegionIds())
                .thenReturn(List.of("1111000000", "9999900000"));
        when(regionRepository.findAllByRegionIdInOrderByRegionIdAsc(
                Set.of("1111000000", "9999900000")
        )).thenReturn(List.of(
                new Region("1111000000", "종로구", "서울특별시")
        ));

        List<RegionResponse> result = regionService.getRegions(null);

        assertEquals(1, result.size());
        assertEquals("1111000000", result.get(0).regionId());
    }

    @Test
    void keyword는_공백과_대소문자를_무시하고_시도명까지_검색한다() {
        stubRegions();

        assertEquals(
                List.of("5013000000"),
                regionService.getRegions(" 자치도 서귀 ").stream()
                        .map(RegionResponse::regionId)
                        .toList()
        );
        assertEquals(
                List.of("5011000000", "5013000000"),
                regionService.getRegions("제주특별자치도").stream()
                        .map(RegionResponse::regionId)
                        .toList()
        );
    }

    @Test
    void keyword가_비어_있으면_전체_목록을_반환한다() {
        stubRegions();

        assertEquals(3, regionService.getRegions("").size());
        assertEquals(3, regionService.getRegions("   ").size());
    }

    @Test
    void keyword와_일치하는_지역이_없으면_빈_목록을_반환한다() {
        stubRegions();

        assertTrue(regionService.getRegions("없는지역").isEmpty());
    }

    @Test
    void 등록된_지역이_없으면_이름_조회_없이_빈_목록을_반환한다() {
        when(courseStopRepository.findDistinctRegionIds())
                .thenReturn(List.of());

        assertTrue(regionService.getRegions(null).isEmpty());
        verify(regionRepository, never())
                .findAllByRegionIdInOrderByRegionIdAsc(anyCollection());
    }

    @Test
    void 시도명과_시군구명이_같으면_표시_이름에_한_번만_쓴다() {
        RegionResponse response = RegionResponse.from(
                new Region("3611000000", "세종특별자치시", "세종특별자치시")
        );

        assertEquals("세종특별자치시", response.displayName());
    }

    private void stubRegions() {
        when(courseStopRepository.findDistinctRegionIds())
                .thenReturn(List.of("1111000000", "5011000000", "5013000000"));
        when(regionRepository.findAllByRegionIdInOrderByRegionIdAsc(
                Set.of("1111000000", "5011000000", "5013000000")
        )).thenReturn(List.of(
                new Region("1111000000", "종로구", "서울특별시"),
                new Region("5011000000", "제주시", "제주특별자치도"),
                new Region("5013000000", "서귀포시", "제주특별자치도")
        ));
    }
}
