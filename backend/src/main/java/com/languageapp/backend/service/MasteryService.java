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

    /** Immutable, request-scoped state shared by every lesson card in one response. */
    public record LessonMastery(List<String> availableLevels, List<String> completedLevels,
                                boolean completed, String completedDifficulty, boolean mastered) {
        public LessonMastery {
            availableLevels = List.copyOf(availableLevels);
            completedLevels = List.copyOf(completedLevels);
        }
    }

    public record CourseSnapshot(List<LessonRepository.CourseLesson> lessons,
                                 Map<UUID, LessonMastery> byLesson, Set<UUID> masteredTopics) {
        public CourseSnapshot {
            lessons = List.copyOf(lessons);
            byLesson = Map.copyOf(byLesson);
            masteredTopics = Set.copyOf(masteredTopics);
        }
    }

    private String canonicalLevel(String value) {
        return LEVELS.stream().filter(level -> level.equalsIgnoreCase(value)).findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public CourseSnapshot getCourseSnapshot(User user) { return getCourseSnapshot(user,user.getActiveCourse().getCourseId()); }
    @Transactional(readOnly = true)
    public CourseSnapshot getCourseSnapshot(User user, UUID courseId) {
        var lessons = lessonRepository.findCourseOverview(courseId);
        Map<UUID, Set<String>> available = new HashMap<>();
        for (var level : lessonRepository.findAvailableCourseLevels(courseId)) {
            String canonical = canonicalLevel(level.getDifficulty());
            if (canonical != null) available.computeIfAbsent(level.getLessonId(), key -> new HashSet<>()).add(canonical);
        }
        Map<UUID, Set<String>> passed = new HashMap<>();
        Map<UUID, String> firstPass = new HashMap<>();
        for (var level : progressRepository.findPassedCourseLevels(user.getUserId(), courseId)) {
            // A successful historical pass still advances the learning path, even if its level was retired.
            if (!firstPass.containsKey(level.getLessonId())) firstPass.put(level.getLessonId(), level.getDifficulty());
            String canonical = canonicalLevel(level.getDifficulty());
            if (canonical != null) passed.computeIfAbsent(level.getLessonId(), key -> new HashSet<>()).add(canonical);
        }
        Map<UUID, LessonMastery> states = new HashMap<>();
        Map<UUID, Boolean> topics = new HashMap<>();
        for (var lesson : lessons) {
            UUID id = lesson.getLessonId();
            Set<String> required = available.getOrDefault(id, Set.of());
            Set<String> successful = passed.getOrDefault(id, Set.of());
            List<String> levels = LEVELS.stream().filter(required::contains).toList();
            List<String> completed = levels.stream().filter(successful::contains).toList();
            boolean mastered = !levels.isEmpty() && completed.size() == levels.size();
            states.put(id, new LessonMastery(levels, completed, firstPass.containsKey(id), firstPass.get(id), mastered));
            // Empty lessons cannot grant mastery, but do not block eligible lessons in the same topic.
            if (!levels.isEmpty()) topics.merge(lesson.getTopicId(), mastered, (previous, current) -> previous && current);
        }
        Set<UUID> masteredTopics = new HashSet<>();
        topics.forEach((id, mastered) -> { if (mastered) masteredTopics.add(id); });
        return new CourseSnapshot(lessons, states, masteredTopics);
    }

    public record MasteryCounts(long lessons, long topics) { }

    @Transactional(readOnly = true)
    public MasteryCounts countMasteredBoth(User user) {
        CourseSnapshot snapshot = getCourseSnapshot(user);
        return new MasteryCounts(snapshot.byLesson().values().stream().filter(LessonMastery::mastered).count(),
                snapshot.masteredTopics().size());
    }

    @Transactional(readOnly = true)
    public long countMastered(User user, boolean topics) {
        MasteryCounts counts = countMasteredBoth(user);
        return topics ? counts.topics() : counts.lessons();
    }
}
