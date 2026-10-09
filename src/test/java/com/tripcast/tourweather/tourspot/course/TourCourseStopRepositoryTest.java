package com.tripcast.tourweather.tourspot.course;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.tripcast.tourweather.tourspot.TourSpot;
import com.tripcast.tourweather.tourspot.TourSpotRepository;

@SpringBootTest
@Transactional
class TourCourseStopRepositoryTest {

    @Autowired
    private TourCourseRepository courseRepository;

    @Autowired
    private TourCourseStopRepository courseStopRepository;

    @Autowired
    private TourSpotRepository tourSpotRepository;

    @Test
    void 여러_코스에_속한_관광지는_코스ID_오름차순으로_코스당_한_건씩_반환한다() {
        TourSpot spot = tourSpotRepository.save(new TourSpot("서울시청", 37.5665, 126.9780));
        TourSpot other = tourSpotRepository.save(new TourSpot("덕수궁", 37.5658, 126.9751));
        TourCourse first = courseRepository.save(new TourCourse("TH01"));
        TourCourse second = courseRepository.save(new TourCourse("TH02"));
        TourCourse third = courseRepository.save(new TourCourse("TH03"));

        // 삽입 순서를 코스 ID 순과 다르게 해서 정렬이 삽입 순서에 기대지 않음을 확인한다.
        saveStop("S-3-1", third, spot, 1);
        saveStop("S-1-1", first, other, 1);
        saveStop("S-1-2", first, spot, 2);
        saveStop("S-2-1", second, other, 1);

        List<SpotCourseOrder> result =
                courseStopRepository.findCourseOrdersByTourSpotId(spot.getId());

        assertEquals(
                List.of(
                        new SpotCourseOrder(first.getId(), 2),
                        new SpotCourseOrder(third.getId(), 1)
                ),
                result
        );
    }

    @Test
    void 코스가_없는_관광지는_빈_목록을_반환한다() {
        TourSpot spot = tourSpotRepository.save(new TourSpot("서울시청", 37.5665, 126.9780));
        TourSpot other = tourSpotRepository.save(new TourSpot("덕수궁", 37.5658, 126.9751));
        TourCourse course = courseRepository.save(new TourCourse("TH01"));
        saveStop("S-1-1", course, other, 1);

        assertEquals(
                List.of(),
                courseStopRepository.findCourseOrdersByTourSpotId(spot.getId())
        );
    }

    @Test
    void 같은_코스에_여러_번_나오는_관광지는_가장_앞선_순서로_한_번만_반환한다() {
        TourSpot spot = tourSpotRepository.save(new TourSpot("서울시청", 37.5665, 126.9780));
        TourSpot other = tourSpotRepository.save(new TourSpot("덕수궁", 37.5658, 126.9751));
        TourCourse course = courseRepository.save(new TourCourse("TH01"));

        saveStop("S-1-1", course, other, 1);
        saveStop("S-1-4", course, spot, 4);
        saveStop("S-1-2", course, spot, 2);
        saveStop("S-1-3", course, other, 3);

        assertEquals(
                List.of(new SpotCourseOrder(course.getId(), 2)),
                courseStopRepository.findCourseOrdersByTourSpotId(spot.getId())
        );
    }

    private void saveStop(
            String sourceSpotId,
            TourCourse course,
            TourSpot spot,
            int courseOrder
    ) {
        courseStopRepository.save(new TourCourseStop(
                sourceSpotId,
                course,
                spot,
                "1111051000",
                courseOrder,
                30,
                "실외",
                "01",
                "역사"
        ));
    }
}
