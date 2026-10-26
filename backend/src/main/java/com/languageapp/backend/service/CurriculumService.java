package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.ExerciseImportRequest;
import com.languageapp.backend.dto.request.LessonImportRequest;
import com.languageapp.backend.dto.request.TopicImportRequest;
import com.languageapp.backend.entity.Course;
import com.languageapp.backend.entity.Exercise;
import com.languageapp.backend.entity.Lesson;
import com.languageapp.backend.entity.LessonTopic;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.CourseRepository;
import com.languageapp.backend.repository.ExerciseRepository;
import com.languageapp.backend.repository.LessonRepository;
import com.languageapp.backend.repository.LessonTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service responsible for managing curriculum data (Topics, Lessons, Exercises).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurriculumService {
    private static final int IMPORT_LIMIT = com.languageapp.backend.validation.CurriculumRules.MAX_ITEMS;

    private final LessonTopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final ExerciseRepository exerciseRepository;
    private final CourseRepository courseRepository;

    @Transactional
    public void importCurriculum(List<TopicImportRequest> requests) {
        com.languageapp.backend.validation.CurriculumRules.list(requests,100);
        int total=0;
        int totalLessons=0;
        for(var request:requests) {
            validateTopic(request);
            totalLessons+=request.getLessons()==null?0:request.getLessons().size();
            if(totalLessons>IMPORT_LIMIT) throw new BadRequestException("Egy import legfeljebb 1000 leckét tartalmazhat.");
            if(request.getLessons()!=null) for(var lesson:request.getLessons()) if(lesson.getExercises()!=null) total+=lesson.getExercises().size();
        }
        if(total>IMPORT_LIMIT) throw new BadRequestException("Egy import legfeljebb 1000 feladatot tartalmazhat.");
        for(var request:requests) importTopicAndLessons(request);
    }

    @Transactional
    public void importTopicAndLessons(TopicImportRequest request) {
        validateTopic(request);
        log.info("Starting curriculum import for topic: {}", request.getTopicName());
        String targetCourseCode = request.getCourseCode() != null ? request.getCourseCode() : "en";

        Course course = courseRepository.findByLanguageCode(targetCourseCode)
                .orElseThrow(() -> new ResourceNotFoundException("Nem található kurzus ezzel a kóddal: " + targetCourseCode));

        LessonTopic topic = new LessonTopic();
        topic.setName(request.getTopicName());
        topic.setDescription(request.getDescription());
        topic.setCourse(course);

        LessonTopic savedTopic = topicRepository.save(topic);
        log.info("Saved Topic with ID: {}", savedTopic.getTopicId());

        if (request.getLessons() != null) {
            importLessonsInternal(savedTopic, request.getLessons());
        }
        log.info("Curriculum import completed successfully.");
    }

    @Transactional
    public void importLessonsToExistingTopic(UUID topicId, List<LessonImportRequest> lessonRequests) {
        com.languageapp.backend.validation.CurriculumRules.list(lessonRequests,IMPORT_LIMIT);
        log.info("Importing {} new lessons to existing topic ID: {}", lessonRequests.size(), topicId);

        LessonTopic existingTopic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic nem található az importáláshoz!"));

        validateLessons(lessonRequests);

        importLessonsInternal(existingTopic, lessonRequests);
    }

    @Transactional
    public void importExercisesToExistingLesson(UUID lessonId, List<ExerciseImportRequest> exerciseRequests) {
        com.languageapp.backend.validation.CurriculumRules.list(exerciseRequests,IMPORT_LIMIT);
        log.info("Importing {} new exercises to existing lesson ID: {}", exerciseRequests.size(), lessonId);

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lecke nem található az importáláshoz!"));

        validateExercises(exerciseRequests);

        importExercisesInternal(lesson, exerciseRequests);
    }

    private void importLessonsInternal(LessonTopic parentTopic, List<LessonImportRequest> lessonRequests) {
        for (LessonImportRequest lessonReq : lessonRequests) {
            Lesson lesson = new Lesson();
            lesson.setTopic(parentTopic);
            lesson.setTitle(lessonReq.getTitle());
            lesson.setOrderIndex(lessonReq.getOrderIndex());
            lesson.setDescription(lessonReq.getDescription());
            lesson.setLanguage(lessonReq.getLanguage());

            Lesson savedLesson = lessonRepository.save(lesson);

            if (lessonReq.getExercises() != null) {
                importExercisesInternal(savedLesson, lessonReq.getExercises());
            }
        }
    }

    private void importExercisesInternal(Lesson parentLesson, List<ExerciseImportRequest> exerciseRequests) {
        for (ExerciseImportRequest exerciseReq : exerciseRequests) {
            Exercise exercise = new Exercise();
            exercise.setLesson(parentLesson);
            exercise.setType(exerciseReq.getType());
            exercise.setDifficulty(exerciseReq.getDifficulty());
            exercise.setContent(exerciseReq.getContent());
            exercise.setCorrectAnswer(exerciseReq.getCorrectAnswer());

            if (exerciseReq.getImageUrl() != null) {
                exercise.setImageUrl(exerciseReq.getImageUrl());
            }

            exerciseRepository.save(exercise);
        }
    }

    private void validateTopic(TopicImportRequest request) {
        com.languageapp.backend.validation.CurriculumRules.topic(request);
        if(request.getLessons()!=null) validateLessons(request.getLessons());
    }
    private void validateLessons(List<LessonImportRequest> requests) {
        com.languageapp.backend.validation.CurriculumRules.list(requests,IMPORT_LIMIT);
        int total=0;
        for(var lesson:requests) {
            com.languageapp.backend.validation.CurriculumRules.lesson(lesson);
            if(lesson.getExercises()!=null) { validateExercises(lesson.getExercises());total+=lesson.getExercises().size(); }
        }
        if(total>IMPORT_LIMIT) throw new BadRequestException("Egy import legfeljebb 1000 feladatot tartalmazhat.");
    }
    private void validateExercises(List<ExerciseImportRequest> requests) {
        com.languageapp.backend.validation.CurriculumRules.list(requests,IMPORT_LIMIT);
        for(var request:requests) {
            com.languageapp.backend.validation.CurriculumRules.exercise(request);
            validateAccessibilityMetadata(request);
        }
    }
    private void validateAccessibilityMetadata(ExerciseImportRequest request) {
        var content = request.getContent();
        if (content == null) throw new BadRequestException("A feladat content mezője kötelező.");
        requireLanguage(content.get("questionLang"));
        requireLanguage(content.get("answerLang"));
        if (content.get("hint") instanceof String hint && !hint.isBlank()) {
            if (!"hu-to-target".equals(content.get("hintDirection")) || !content.containsKey("hintParts")) {
                throw new BadRequestException("Tipphez hintDirection: hu-to-target és nyelvjelölt hintParts szükséges.");
            }
        }
        for (String field : List.of("question", "hint")) {
            String partsField = field + "Parts";
            if (!content.containsKey(partsField)) continue;
            if (!(content.get(partsField) instanceof List<?> parts) || parts.isEmpty()) {
                throw new BadRequestException("A " + partsField + " nem üres szövegrészlista kell legyen.");
            }
            StringBuilder textValue = new StringBuilder();
            for (var part : parts) {
                if (!(part instanceof java.util.Map<?, ?> segment) || !(segment.get("text") instanceof String text)) {
                    throw new BadRequestException("Minden " + partsField + " elemhez text és lang szükséges.");
                }
                requireLanguage(segment.get("lang"));
                if(textValue.length()+text.length()>10_000) throw new BadRequestException("Túl hosszú nyelvjelölt szöveg.");
                textValue.append(text);
            }
            if (!textValue.toString().equals(content.get(field))) {
                throw new BadRequestException("A " + partsField + " szövege pontosan egyezzen a " + field + " értékével.");
            }
        }
        boolean hasImage = request.getImageUrl() != null && !request.getImageUrl().isBlank()
                || content.get("imageUrl") instanceof String image && !image.isBlank();
        if ("IMAGE_CHOICE".equals(request.getType()) && !hasImage) throw new BadRequestException("A képes feladathoz kép szükséges.");
        if (hasImage) {
            if (!(content.get("imageAlt") instanceof String alt) || alt.isBlank()
                    || !(content.get("imagePurpose") instanceof String purpose)
                    || !List.of("informative", "visual-assessment").contains(purpose)) {
                throw new BadRequestException("Képhez imageAlt és imagePurpose (informative vagy visual-assessment) szükséges.");
            }
            requireLanguage(content.get("imageAltLang"));
        }
    }

    private void requireLanguage(Object value) {
        com.languageapp.backend.validation.CurriculumRules.language(value);
    }
}
