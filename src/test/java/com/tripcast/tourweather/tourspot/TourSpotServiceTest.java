package com.tripcast.tourweather.tourspot;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.tripcast.tourweather.climate.ClimateIndexService;
import com.tripcast.tourweather.tourspot.course.TourCourseService;
import com.tripcast.tourweather.tourspot.course.TourCourseStopRepository;
import com.tripcast.tourweather.tourspot.dto.TourSpotCourseResponse;
import com.tripcast.tourweather.tourspot.dto.TourSpotResponse;
import com.tripcast.tourweather.tourspot.dto.TourSpotWeatherResponse;
import com.tripcast.tourweather.weather.WeatherService;
import com.tripcast.tourweather.weather.dto.WeatherResponse;

@ExtendWith(MockitoExtension.class)
class TourSpotServiceTest {

    @Mock
    private TourSpotRepository tourSpotRepository;

    @Mock
    private WeatherService weatherService;

    @Mock
    private TourCourseStopRepository courseStopRepository;

    @Mock
    private ClimateIndexService climateIndexService;

    @Mock
    private TourCourseService tourCourseService;

    @InjectMocks
    private TourSpotService tourSpotService;

    @Test
    void 관광지를_이름으로_페이지_검색한다() {
        TourSpot tourSpot = new TourSpot(
                "서울시청",
                37.5665,
                126.9780);

        PageRequest repositoryPageRequest = PageRequest.of(
                2,
                10,
                Sort.by("name").ascending());
        Page<TourSpot> repositoryResult = new PageImpl<>(
                List.of(tourSpot),
                repositoryPageRequest,
                21);

        when(tourSpotRepository.findByNameContaining(
                eq("서울"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(repositoryResult);

        Page<TourSpotResponse> result = tourSpotService.search(
                "서울",
                2,
                10);

        assertAll(
                () -> assertEquals(2, result.getNumber()),
                () -> assertEquals(10, result.getSize()),
                () -> assertEquals(21, result.getTotalElements()),
                () -> assertEquals(1, result.getContent().size()),
                () -> assertEquals(
                        "서울시청",
                        result.getContent().get(0).name()));

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(tourSpotRepository).findByNameContaining(
                eq("서울"),
                pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertAll(
                () -> assertEquals(2, pageable.getPageNumber()),
                () -> assertEquals(10, pageable.getPageSize()),
                () -> assertEquals(
                        Sort.Direction.ASC,
                        pageable.getSort()
                                .getOrderFor("name")
                                .getDirection()));
    }

    @Test
    void 관광지_좌표를_변환해서_날씨를_조회한다() {
        TourSpot tourSpot = new TourSpot(
                "서울시청",
                37.5665,
                126.9780);

        WeatherResponse weather = new WeatherResponse(
                "20260920",
                "1200",
                22.0,
                20,
                "맑음");

        when(tourSpotRepository.findById(1L))
                .thenReturn(Optional.of(tourSpot));
        when(weatherService.getWeather(60, 127))
                .thenReturn(weather);
        when(courseStopRepository.findFirstByTourSpotIdOrderByIdAsc(1L))
                .thenReturn(Optional.empty());

        TourSpotWeatherResponse result =
                tourSpotService.getWeather(1L);

        assertAll(
                () -> assertEquals("서울시청", result.tourSpotName()),
                () -> assertEquals(37.5665, result.latitude()),
                () -> assertEquals(126.9780, result.longitude()),
                () -> assertEquals(weather, result.weather()),
                () -> assertNull(result.climateIndex()));

        verify(tourSpotRepository).findById(1L);
        verify(weatherService).getWeather(60, 127);
    }

    @Test
    void 존재하지_않는_관광지는_예외가_발생한다() {
        when(tourSpotRepository.findById(999L))
                .thenReturn(Optional.empty());

        TourSpotNotFoundException exception = assertThrows(
                TourSpotNotFoundException.class,
                () -> tourSpotService.getWeather(999L));

        assertEquals(
                "관광지를 찾을 수 없습니다: 999",
                exception.getMessage());
        verifyNoInteractions(weatherService);
    }

    @Test
    void 관광지가_포함된_코스를_코스_서비스에서_조회한다() {
        TourSpotCourseResponse course = new TourSpotCourseResponse(
                1L, "TH01", "서울시청", "역사", 3L,
                37.5665, 126.9780, 37.5512, 126.9882, 2);

        when(tourSpotRepository.existsById(1L)).thenReturn(true);
        when(tourCourseService.findCoursesByTourSpotId(1L))
                .thenReturn(List.of(course));

        assertEquals(List.of(course), tourSpotService.getCourses(1L));
    }

    @Test
    void 존재하지_않는_관광지의_코스를_조회하면_예외가_발생한다() {
        when(tourSpotRepository.existsById(999L)).thenReturn(false);

        assertThrows(
                TourSpotNotFoundException.class,
                () -> tourSpotService.getCourses(999L));

        verifyNoInteractions(tourCourseService);
    }
}
