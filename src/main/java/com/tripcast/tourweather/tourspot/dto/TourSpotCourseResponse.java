package com.tripcast.tourweather.tourspot.dto;

import com.tripcast.tourweather.tourspot.course.dto.TourCourseSummaryResponse;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관광지가 포함된 코스 요약. 코스 목록 항목에 해당 관광지의 코스 내 방문 순서(spotCourseOrder)를 더한 형태")
public record TourSpotCourseResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long courseId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceCourseId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "코스 첫 관광지명. 정류지가 없는 코스는 null")
        String representativeSpotName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "코스 첫 관광지의 테마명. 정류지가 없는 코스는 null")
        String representativeThemeName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "코스의 정류지 수")
        long stopCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "첫 정류지(courseOrder 최솟값)의 위도. 정류지가 없는 코스는 null")
        Double startLatitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "첫 정류지의 경도. 정류지가 없는 코스는 null")
        Double startLongitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "마지막 정류지(courseOrder 최댓값)의 위도. 정류지가 1개면 시작과 같고, 없는 코스는 null")
        Double endLatitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "마지막 정류지의 경도. 정류지가 1개면 시작과 같고, 없는 코스는 null")
        Double endLongitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "요청한 관광지의 코스 내 방문 순서(1부터 시작, TourCourseStopResponse.courseOrder와 같은 의미). 한 코스에 여러 번 등장하면 가장 앞선 순서")
        int spotCourseOrder
) {
    public static TourSpotCourseResponse of(
            TourCourseSummaryResponse summary,
            int spotCourseOrder
    ) {
        return new TourSpotCourseResponse(
                summary.courseId(),
                summary.sourceCourseId(),
                summary.representativeSpotName(),
                summary.representativeThemeName(),
                summary.stopCount(),
                summary.startLatitude(),
                summary.startLongitude(),
                summary.endLatitude(),
                summary.endLongitude(),
                spotCourseOrder
        );
    }
}
