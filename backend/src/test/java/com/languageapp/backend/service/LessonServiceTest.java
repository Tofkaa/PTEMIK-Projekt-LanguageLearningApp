package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.ExerciseResponse;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.exception.ForbiddenException;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    @Mock private LessonRepository lessonRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChallengeRepository challengeRepository;
    @Mock private CourseRepository courseRepository;

    @InjectMocks
    private LessonService lessonService;

    private User student;
    private Lesson lesson;
    private Exercise exercise;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(lessonService, "exerciseSalt", "titkos_teszt_so");

        Course course = new Course();
        course.setCourseId(UUID.randomUUID());
        course.setLanguageCode("en");

        student = new User();
        student.setUserId(UUID.randomUUID());
        student.setEmail("diak@test.com");
        student.setActiveCourse(course);

        LessonTopic topic = new LessonTopic();
        topic.setActive(true);

        lesson = new Lesson();
        lesson.setLessonId(UUID.randomUUID());
        lesson.setActive(true);
        lesson.setTopic(topic);

        exercise = new Exercise();
        exercise.setExerciseId(UUID.randomUUID());
        exercise.setType("TYPING");
        exercise.setActive(true);
        exercise.setLesson(lesson);
        exercise.setCorrectAnswer(Map.of("answer", "alma"));

        lesson.setExercises(List.of(exercise));
    }

    @Test
    void getExercisesByLessonId_ShouldHashCorrectAnswers() {
        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(lessonRepository.findById(lesson.getLessonId())).thenReturn(Optional.of(lesson));

        List<ExerciseResponse> responses = lessonService.getExercisesByLessonId(lesson.getLessonId(), student.getEmail(), null);

        assertEquals(1, responses.size());
        ExerciseResponse response = responses.get(0);

        assertNotNull(response.getAnswerHash());
        assertNotEquals("alma", response.getAnswerHash());
    }

    @Test
    void getExercisesByLessonId_WithChallengeBypass_ShouldAllowAccess() {
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = new Challenge();
        challenge.setChallenger(student);
        challenge.setOpponent(new User());
        challenge.setLesson(lesson);

        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(lessonRepository.findById(lesson.getLessonId())).thenReturn(Optional.of(lesson));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        List<ExerciseResponse> responses = lessonService.getExercisesByLessonId(lesson.getLessonId(), student.getEmail(), challengeId);

        assertNotNull(responses);
        assertEquals(1, responses.size());
    }

    @Test
    void getExercisesByLessonId_WithFakeChallenge_ShouldThrowForbidden() {
        UUID challengeId = UUID.randomUUID();
        Challenge challenge = new Challenge();

        User otherChallenger = new User(); otherChallenger.setUserId(UUID.randomUUID());
        User otherOpponent = new User(); otherOpponent.setUserId(UUID.randomUUID());

        challenge.setChallenger(otherChallenger);
        challenge.setOpponent(otherOpponent);
        challenge.setLesson(lesson);

        when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        when(lessonRepository.findById(lesson.getLessonId())).thenReturn(Optional.of(lesson));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThrows(ForbiddenException.class, () ->
                lessonService.getExercisesByLessonId(lesson.getLessonId(), student.getEmail(), challengeId)
        );
    }
}