package com.tripcast.tourweather.tourspot;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripcast.tourweather.tourspot.dto.TourSpotCourseResponse;
import com.tripcast.tourweather.tourspot.dto.TourSpotResponse;
import com.tripcast.tourweather.tourspot.dto.TourSpotWeatherResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(
        name = "관광지",
        description = "관광지 검색, 날씨 및 포함 코스 조회 API"
)
@Validated
@RestController
@RequestMapping("/api/tour-spots")
public class TourSpotController {

    private final TourSpotService tourSpotService;

    public TourSpotController(
            TourSpotService tourSpotService
    ) {
        this.tourSpotService = tourSpotService;
    }

    @Operation(
            summary = "관광지 검색",
            description = "이름에 검색어가 포함된 관광지를 페이지 단위로 조회합니다."
    )
    @GetMapping
    public Page<TourSpotResponse> search(
            @RequestParam(defaultValue = "")
            String keyword,

            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "20")
            @Min(1)
            @Max(100)
            int size
    ) {
        return tourSpotService.search(
                keyword,
                page,
                size
        );
    }

    @Operation(
            summary = "관광지 날씨 조회",
            description = "관광지 ID를 이용해 위치와 기상청 단기예보를 조회합니다."
    )
    @GetMapping("/{id}/weather")
    public TourSpotWeatherResponse getWeather(
            @PathVariable Long id
    ) {
        return tourSpotService.getWeather(id);
    }

    @Operation(
            summary = "관광지가 포함된 코스 목록 조회",
            description = "관광지 ID를 이용해 해당 관광지를 포함하는 코스를 courseId 오름차순으로 조회합니다. 페이지네이션은 없습니다. 같은 관광지가 한 코스에 여러 번 나와도 코스는 한 번만 반환하며, spotCourseOrder는 가장 앞선 방문 순서입니다. 포함된 코스가 없으면 빈 배열을 반환하고, 관광지가 없으면 404입니다."
    )
    @GetMapping("/{id}/courses")
    public List<TourSpotCourseResponse> getCourses(
            @PathVariable
            @Min(1)
            Long id
    ) {
        return tourSpotService.getCourses(id);
    }
}
