package com.languageapp.backend.service;

import com.languageapp.backend.entity.*;
import com.languageapp.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Mastery means passing every currently available level, never a perfect-score requirement. */
@Service @RequiredArgsConstructor
public class MasteryService {
    private final LessonRepository lessonRepository;
    private final ProgressRepository progressRepository;
    private static final List<String> LEVELS = List.of("EASY", "MEDIUM", "HARD");

    public List<String> availableLevels(Lesson lesson) {
        return LEVELS.stream().filter(level -> lesson.getExercises().stream()
                .anyMatch(e -> e.isActive() && level.equalsIgnoreCase(e.getDifficulty()))).toList();
    }

    public List<String> completedLevels(Lesson lesson, List<Progress> progress) {
        return availableLevels(lesson).stream().filter(level -> progress.stream().anyMatch(p ->
                Boolean.TRUE.equals(p.getIsCompleted()) && lesson.getLessonId().equals(p.getLesson().getLessonId())
                        && level.equalsIgnoreCase(p.getCompletedDifficulty()))).toList();
    }

    public boolean isMastered(Lesson lesson, List<Progress> progress) {
        var available = availableLevels(lesson);
        return !available.isEmpty() && completedLevels(lesson, progress).containsAll(available);
    }

    public boolean isTopicMastered(UUID topicId, List<Lesson> lessons, List<Progress> progress) {
        var eligible = lessons.stream().filter(l -> l.isActive() && l.getTopic().isActive()
                && topicId.equals(l.getTopic().getTopicId()) && !availableLevels(l).isEmpty()).toList();
        return !eligible.isEmpty() && eligible.stream().allMatch(l -> isMastered(l, progress));
    }

    @Transactional(readOnly = true)
    public long countMastered(User user, boolean topics) {
        var lessons = lessonRepository.findByTopic_Course_CourseId(user.getActiveCourse().getCourseId()).stream()
                .filter(l -> l.isActive() && l.getTopic().isActive()).toList();
        var progress = progressRepository.findByUserUserId(user.getUserId());
        if (!topics) return lessons.stream().filter(l -> isMastered(l, progress)).count();
        return lessons.stream().map(l -> l.getTopic().getTopicId()).distinct()
                .filter(id -> isTopicMastered(id, lessons, progress)).count();
    }
}
