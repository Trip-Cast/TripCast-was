package com.tripcast.tourweather.tourspot.course;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourCourseStopRepository
        extends JpaRepository<TourCourseStop, Long> {

    boolean existsBySourceSpotId(String sourceSpotId);

    List<TourCourseStop> findByCourseIdOrderByCourseOrderAsc(Long courseId);

    /**
     * 여러 코스의 정류지를 관광지와 함께 한 번에 조회한다(목록 API의 N+1 방지).
     * 코스 ID, 방문 순서 오름차순이라 코스별로 묶으면 첫/마지막 정류지가 바로 나온다.
     */
    @Query("""
            select stop
            from TourCourseStop stop
            join fetch stop.tourSpot
            where stop.course.id in :courseIds
            order by stop.course.id asc, stop.courseOrder asc
            """)
    List<TourCourseStop> findAllWithTourSpotByCourseIds(
            @Param("courseIds") Collection<Long> courseIds
    );

    Optional<TourCourseStop> findFirstByTourSpotIdOrderByIdAsc(Long tourSpotId);

    /**
     * 관광지가 속한 코스를 코스 ID 오름차순으로, 코스당 한 건씩 조회한다.
     * 같은 관광지가 한 코스에 여러 번 나오면 가장 앞선 방문 순서만 남는다.
     */
    @Query("""
            select new com.tripcast.tourweather.tourspot.course.SpotCourseOrder(
                stop.course.id,
                min(stop.courseOrder)
            )
            from TourCourseStop stop
            where stop.tourSpot.id = :tourSpotId
            group by stop.course.id
            order by stop.course.id asc
            """)
    List<SpotCourseOrder> findCourseOrdersByTourSpotId(
            @Param("tourSpotId") Long tourSpotId
    );

    @Query("""
            select distinct stop.regionId
            from TourCourseStop stop
            where stop.regionId is not null
              and stop.regionId <> ''
            order by stop.regionId
            """)
    List<String> findDistinctRegionIds();
}
