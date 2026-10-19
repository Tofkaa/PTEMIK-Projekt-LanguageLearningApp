package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.ExerciseImportRequest;
import com.languageapp.backend.dto.request.LessonImportRequest;
import com.languageapp.backend.dto.request.TopicImportRequest;
import com.languageapp.backend.entity.Exercise;
import com.languageapp.backend.entity.Lesson;
import com.languageapp.backend.entity.LessonTopic;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.ExerciseRepository;
import com.languageapp.backend.repository.LessonRepository;
import com.languageapp.backend.repository.LessonTopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurriculumServiceTest {

    @Mock
    private LessonTopicRepository topicRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @InjectMocks
    private CurriculumService curriculumService;

    private LessonTopic mockTopic;
    private Lesson mockLesson;

    @BeforeEach
    void setUp() {
        mockTopic = new LessonTopic();
        mockTopic.setTopicId(UUID.randomUUID());
        mockTopic.setName("Test Topic");

        mockLesson = new Lesson();
        mockLesson.setLessonId(UUID.randomUUID());
        mockLesson.setTitle("Test Lesson");
        mockLesson.setTopic(mockTopic);
    }

    @Test
    void importLessonsToExistingTopic_Success() {
        // Arrange
        UUID topicId = mockTopic.getTopicId();
        when(topicRepository.findById(topicId)).thenReturn(Optional.of(mockTopic));

        LessonImportRequest lessonReq = new LessonImportRequest();
        lessonReq.setTitle("New Lesson");
        lessonReq.setDifficulty("EASY");

        when(lessonRepository.save(any(Lesson.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        curriculumService.importLessonsToExistingTopic(topicId, List.of(lessonReq));

        // Assert
        ArgumentCaptor<Lesson> lessonCaptor = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonRepository, times(1)).save(lessonCaptor.capture());

        Lesson savedLesson = lessonCaptor.getValue();
        assertEquals("New Lesson", savedLesson.getTitle());
        assertEquals(mockTopic, savedLesson.getTopic(), "A leckének hozzá kell kapcsolódnia a témakörhöz");
    }

    @Test
    void importLessonsToExistingTopic_ThrowsExceptionWhenTopicNotFound() {
        // Arrange
        UUID fakeId = UUID.randomUUID();
        when(topicRepository.findById(fakeId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () ->
                curriculumService.importLessonsToExistingTopic(fakeId, Collections.emptyList())
        );
    }

    @Test
    void importExercisesToExistingLesson_Success() {
        // Arrange
        UUID lessonId = mockLesson.getLessonId();
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(mockLesson));

        ExerciseImportRequest exerciseReq = new ExerciseImportRequest();
        exerciseReq.setType("MULTIPLE_CHOICE");
        exerciseReq.setContent(java.util.Map.of("questionLang", "hu", "answerLang", "en"));

        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        curriculumService.importExercisesToExistingLesson(lessonId, List.of(exerciseReq));

        // Assert
        ArgumentCaptor<Exercise> exerciseCaptor = ArgumentCaptor.forClass(Exercise.class);
        verify(exerciseRepository, times(1)).save(exerciseCaptor.capture());

        Exercise savedExercise = exerciseCaptor.getValue();
        assertEquals("MULTIPLE_CHOICE", savedExercise.getType());
        assertEquals(mockLesson, savedExercise.getLesson(), "A feladatnak hozzá kell kapcsolódnia a leckéhez");
    }

    private ExerciseImportRequest imageRequest() {
        var request = new ExerciseImportRequest(); request.setType("IMAGE_CHOICE"); request.setImageUrl("/images/test.png");
        request.setContent(new java.util.HashMap<>(java.util.Map.of("questionLang", "hu", "answerLang", "de",
                "imagePurpose", "visual-assessment", "imageAlt", "Képfelismerési szókincsfeladat", "imageAltLang", "hu")));
        return request;
    }

    @Test void importsVisualAssessmentWithoutDerivingAltFromAnswer() {
        when(lessonRepository.findById(mockLesson.getLessonId())).thenReturn(Optional.of(mockLesson));
        var request = imageRequest();
        request.setCorrectAnswer(java.util.Map.of("answer", "Apfel"));
        curriculumService.importExercisesToExistingLesson(mockLesson.getLessonId(), List.of(request));
        verify(exerciseRepository).save(argThat(e -> e.getContent().get("imageAlt").equals("Képfelismerési szókincsfeladat")));
    }

    @Test void rejectsMissingImageAlternativeBeforeSavingAnyExercise() {
        when(lessonRepository.findById(mockLesson.getLessonId())).thenReturn(Optional.of(mockLesson));
        var invalid = imageRequest(); invalid.getContent().remove("imageAlt");
        assertThrows(BadRequestException.class, () -> curriculumService.importExercisesToExistingLesson(mockLesson.getLessonId(), List.of(imageRequest(), invalid)));
        verifyNoInteractions(exerciseRepository);
    }

    @Test void rejectsInconsistentMixedLanguageParts() {
        when(lessonRepository.findById(mockLesson.getLessonId())).thenReturn(Optional.of(mockLesson));
        var invalid = imageRequest(); invalid.getContent().put("question", "Question");
        invalid.getContent().put("questionParts", List.of(java.util.Map.of("text", "Different", "lang", "en")));
        assertThrows(BadRequestException.class, () -> curriculumService.importExercisesToExistingLesson(mockLesson.getLessonId(), List.of(invalid)));
        verifyNoInteractions(exerciseRepository);
    }

    @Test void rejectsMissingImagePurposeWithReadableValidationError() {
        when(lessonRepository.findById(mockLesson.getLessonId())).thenReturn(Optional.of(mockLesson));
        var invalid = imageRequest(); invalid.getContent().remove("imagePurpose");
        assertThrows(BadRequestException.class, () -> curriculumService.importExercisesToExistingLesson(mockLesson.getLessonId(), List.of(invalid)));
        verifyNoInteractions(exerciseRepository);
    }

    @Test void rejectsInvalidLanguageTag() {
        when(lessonRepository.findById(mockLesson.getLessonId())).thenReturn(Optional.of(mockLesson));
        var invalid = imageRequest(); invalid.getContent().put("answerLang", "en_US");
        assertThrows(BadRequestException.class, () -> curriculumService.importExercisesToExistingLesson(mockLesson.getLessonId(), List.of(invalid)));
        verifyNoInteractions(exerciseRepository);
    }

    private ExerciseImportRequest hintRequest() {
        var request = imageRequest();
        request.getContent().put("hint", "alma = Apfel");
        request.getContent().put("hintDirection", "hu-to-target");
        request.getContent().put("hintParts", List.of(java.util.Map.of("text", "alma = ", "lang", "hu"),
                java.util.Map.of("text", "Apfel", "lang", "de")));
        return request;
    }

    @Test void importsExplicitHintLanguagesWithoutReversingOrChangingTheText() {
        when(lessonRepository.findById(mockLesson.getLessonId())).thenReturn(Optional.of(mockLesson));
        var request = hintRequest();
        curriculumService.importExercisesToExistingLesson(mockLesson.getLessonId(), List.of(request));
        verify(exerciseRepository).save(argThat(e -> e.getContent().equals(request.getContent())));
    }

    @Test void rejectsHintWithoutDirectionOrLanguagePartsBeforeSaving() {
        when(lessonRepository.findById(mockLesson.getLessonId())).thenReturn(Optional.of(mockLesson));
        var request = hintRequest(); request.getContent().remove("hintDirection");
        assertThrows(BadRequestException.class, () -> curriculumService.importExercisesToExistingLesson(mockLesson.getLessonId(), List.of(request)));
        request.getContent().put("hintDirection", "hu-to-target"); request.getContent().remove("hintParts");
        assertThrows(BadRequestException.class, () -> curriculumService.importExercisesToExistingLesson(mockLesson.getLessonId(), List.of(request)));
        verifyNoInteractions(exerciseRepository);
    }

    @Test void rejectsHintPartsThatChangeTheDisplayedTextOrUseInvalidLanguage() {
        when(lessonRepository.findById(mockLesson.getLessonId())).thenReturn(Optional.of(mockLesson));
        var request = hintRequest();
        request.getContent().put("hintParts", List.of(java.util.Map.of("text", "Other", "lang", "de")));
        assertThrows(BadRequestException.class, () -> curriculumService.importExercisesToExistingLesson(mockLesson.getLessonId(), List.of(request)));
        request.getContent().put("hintParts", List.of(java.util.Map.of("text", "alma = Apfel", "lang", "de_DE")));
        assertThrows(BadRequestException.class, () -> curriculumService.importExercisesToExistingLesson(mockLesson.getLessonId(), List.of(request)));
        verifyNoInteractions(exerciseRepository);
    }
}
