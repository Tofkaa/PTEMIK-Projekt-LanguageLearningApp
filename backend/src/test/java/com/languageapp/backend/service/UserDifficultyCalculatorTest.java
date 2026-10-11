package com.languageapp.backend.service;

import com.languageapp.backend.entity.Course;
import com.languageapp.backend.entity.Lesson;
import com.languageapp.backend.entity.Result;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.DifficultyLevel;
import com.languageapp.backend.repository.ResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class UserDifficultyCalculatorTest {

    @Mock private ResultRepository resultRepository;

    @InjectMocks
    private UserDifficultyCalculator calculator;

    private User user;
    private Course course;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setCourseId(UUID.randomUUID());

        user = new User();
        user.setUserId(UUID.randomUUID());
        user.setPreferredDifficulty(DifficultyLevel.DYNAMIC);
        user.setActiveCourse(course); // Hozzáadjuk a kurzust a tesztfelhasználóhoz
    }

    @Test
    void determineTargetDifficulty_ManualOverride_ReturnsPreferred() {
        user.setPreferredDifficulty(DifficultyLevel.HARD);
        String diff = calculator.determineTargetDifficulty(user);
        assertEquals("HARD", diff, "Ha nem DYNAMIC, a beállított fix szintet kell visszaadnia.");
    }

    @Test
    void determineTargetDifficulty_NoHistoryInCourse_ReturnsEasy() {

        when(resultRepository.findTop5ByUserUserIdAndLesson_Topic_Course_CourseIdOrderBySubmittedAtDesc(
                eq(user.getUserId()), eq(course.getCourseId()))).thenReturn(List.of());

        String diff = calculator.determineTargetDifficulty(user);

        assertEquals("EASY", diff, "Előzmények nélkül az adott kurzuson az alapértelmezett szint az EASY.");
    }

    @Test
    void determineTargetDifficulty_MediumScoreDropsBelow50_DemotesToEasy() {
        Lesson mediumLesson = new Lesson();
        mediumLesson.setDifficulty("MEDIUM");

        Result r1 = new Result(); r1.setLesson(mediumLesson); r1.setScore(40);
        Result r2 = new Result(); r2.setLesson(mediumLesson); r2.setScore(45);


        when(resultRepository.findTop5ByUserUserIdAndLesson_Topic_Course_CourseIdOrderBySubmittedAtDesc(
                eq(user.getUserId()), eq(course.getCourseId()))).thenReturn(List.of(r1, r2));
        when(resultRepository.countByUserUserIdAndLesson_Topic_Course_CourseId(
                eq(user.getUserId()), eq(course.getCourseId()))).thenReturn(10L);

        String diff = calculator.determineTargetDifficulty(user);

        assertEquals("EASY", diff, "Ha a WMA 50% alá esik MEDIUM-on, azonnal EASY-re kell degradálni.");
    }

    @Test
    void determineTargetDifficulty_EasyWithHighWma_PromotesToMedium() {
        Lesson easyLesson = new Lesson();
        easyLesson.setDifficulty("EASY");

        Result r1 = new Result(); r1.setLesson(easyLesson); r1.setScore(100);
        Result r2 = new Result(); r2.setLesson(easyLesson); r2.setScore(90);


        when(resultRepository.findTop5ByUserUserIdAndLesson_Topic_Course_CourseIdOrderBySubmittedAtDesc(
                eq(user.getUserId()), eq(course.getCourseId()))).thenReturn(List.of(r1, r2));
        when(resultRepository.countByUserUserIdAndLesson_Topic_Course_CourseId(
                eq(user.getUserId()), eq(course.getCourseId()))).thenReturn(3L);

        String diff = calculator.determineTargetDifficulty(user);

        assertEquals("MEDIUM", diff, "75% feletti WMA és >= 3 próbálkozás esetén MEDIUM-ra kell léptetni.");
    }
}