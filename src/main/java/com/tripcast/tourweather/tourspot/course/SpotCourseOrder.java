package com.tripcast.tourweather.tourspot.course;

/**
 * 관광지가 속한 코스와, 그 코스 안에서 관광지가 처음 등장하는 방문 순서.
 */
public record SpotCourseOrder(
        Long courseId,
        Integer courseOrder
) {
}
