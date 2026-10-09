package com.tripcast.tourweather.tourspot;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.tripcast.tourweather.climate.ClimateIndexService;
import com.tripcast.tourweather.climate.dto.ClimateIndexResponse;
import com.tripcast.tourweather.climate.util.ClimateRegionIdNormalizer;
import com.tripcast.tourweather.tourspot.course.TourCourseService;
import com.tripcast.tourweather.tourspot.course.TourCourseStopRepository;
import com.tripcast.tourweather.tourspot.dto.TourSpotCourseResponse;
import com.tripcast.tourweather.tourspot.dto.TourSpotResponse;
import com.tripcast.tourweather.tourspot.dto.TourSpotWeatherResponse;
import com.tripcast.tourweather.weather.WeatherService;
import com.tripcast.tourweather.weather.dto.WeatherResponse;
import com.tripcast.tourweather.weather.util.GridCoordinate;
import com.tripcast.tourweather.weather.util.LatLonToGridConverter;

@Service
public class TourSpotService {

    private final TourSpotRepository tourSpotRepository;
    private final WeatherService weatherService;
    private final TourCourseStopRepository courseStopRepository;
    private final ClimateIndexService climateIndexService;
    private final TourCourseService tourCourseService;

    public TourSpotService(
            TourSpotRepository tourSpotRepository,
            WeatherService weatherService,
            TourCourseStopRepository courseStopRepository,
            ClimateIndexService climateIndexService,
            TourCourseService tourCourseService) {
        this.tourSpotRepository = tourSpotRepository;
        this.weatherService = weatherService;
        this.courseStopRepository = courseStopRepository;
        this.climateIndexService = climateIndexService;
        this.tourCourseService = tourCourseService;
    }

    public Page<TourSpotResponse> search(
            String keyword,
            int page,
            int size) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by("name").ascending());

        return tourSpotRepository
                .findByNameContaining(
                        keyword,
                        pageRequest)
                .map(TourSpotResponse::from);
    }

    public TourSpotWeatherResponse getWeather(
            Long tourSpotId) {
        TourSpot tourSpot = tourSpotRepository
                .findById(tourSpotId)
                .orElseThrow(() -> new TourSpotNotFoundException(
                        tourSpotId));

        GridCoordinate grid = LatLonToGridConverter.convert(
                tourSpot.getLatitude(),
                tourSpot.getLongitude());

        WeatherResponse weather = weatherService.getWeather(
                grid.nx(),
                grid.ny());

        ClimateIndexResponse climateIndex = courseStopRepository
                .findFirstByTourSpotIdOrderByIdAsc(tourSpotId)
                .map(stop -> climateIndexService.findLatestOrNull(
                        ClimateRegionIdNormalizer.normalize(
                                stop.getRegionId()
                        )
                ))
                .orElse(null);

        return new TourSpotWeatherResponse(
                tourSpot.getId(),
                tourSpot.getName(),
                tourSpot.getLatitude(),
                tourSpot.getLongitude(),
                weather,
                climateIndex);
    }

    public List<TourSpotCourseResponse> getCourses(Long tourSpotId) {
        if (!tourSpotRepository.existsById(tourSpotId)) {
            throw new TourSpotNotFoundException(tourSpotId);
        }

        return tourCourseService.findCoursesByTourSpotId(tourSpotId);
    }
}
