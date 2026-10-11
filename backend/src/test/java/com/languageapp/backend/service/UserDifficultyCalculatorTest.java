package com.languageapp.backend.service;

import com.languageapp.backend.entity.Course;
import com.languageapp.backend.entity.Progress;
import com.languageapp.backend.entity.Result;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.DifficultyLevel;
import com.languageapp.backend.repository.ProgressRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDifficultyCalculatorTest {

    @Mock private ResultRepository resultRepository;
    @Mock private ProgressRepository progressRepository;

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
        user.setActiveCourse(course);
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
}