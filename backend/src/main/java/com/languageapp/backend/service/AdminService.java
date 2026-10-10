package com.languageapp.backend.service;

import com.languageapp.backend.entity.Achievement;
import com.languageapp.backend.entity.AdminLog;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final AdminLogRepository adminLogRepository;
    private final AchievementRepository achievementRepository;
    private final LessonTopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final ExerciseRepository exerciseRepository;
    private final ClassroomRepository classroomRepository;

    public record UserAdminDTO(UUID userId, String name, String userTag, String email, LocalDateTime createdAt, boolean active, Role role) {}

    @Transactional(readOnly = true)
    public List<UserAdminDTO> getAllUsers() {
        return userRepository.findAllUsersIncludingDeleted().stream()
                .map(u -> new UserAdminDTO(
                        u.getUserId(),
                        u.getName(),
                        u.getUserTag() != null ? u.getUserTag() : "0000",
                        u.getEmail(),
                        u.getCreatedAt() != null ? u.getCreatedAt() : LocalDateTime.now(),
                        u.isActive(),
                        u.getRole()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<com.languageapp.backend.dto.response.AdminLogResponse> getSystemLogs() {
        return adminLogRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "loggedAt"))
                .stream()
                .map(log -> {
                    com.languageapp.backend.dto.response.AdminLogResponse dto = new com.languageapp.backend.dto.response.AdminLogResponse();
                    dto.setLogId(log.getLogId());
                    dto.setActionType(log.getActionType());
                    dto.setDetails(log.getDetails());
                    dto.setLoggedAt(log.getLoggedAt());

                    // Itt "erőszakoljuk" ki a Lazy betöltést a tranzakción belül!
                    dto.setAdmin(new com.languageapp.backend.dto.response.AdminLogResponse.AdminUserDto(
                            log.getAdmin().getName(),
                            log.getAdmin().getEmail()
                    ));

                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateUserRole(UUID targetUserId, Role newRole, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        String oldRole = targetUser.getRole().name();
        targetUser.setRole(newRole);
        userRepository.save(targetUser);

        logAdminAction(admin, "ROLE_CHANGED", targetUserId,
                "Jogosultság módosítva: " + oldRole + " -> " + newRole.name() + " (" + targetUser.getEmail() + ") [ID: " + targetUserId + "]");
    }

    @Transactional
    public void toggleUserStatus(UUID targetUserId, boolean isActive, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        User targetUser = userRepository.findAllUsersIncludingDeleted().stream()
                .filter(u -> u.getUserId().equals(targetUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        userRepository.updateUserStatus(targetUserId, isActive);

        String action = isActive ? "USER_UNBANNED" : "USER_BANNED";
        logAdminAction(admin, action, targetUserId,
                "Fiók " + (isActive ? "visszaállítva" : "felfüggesztve") + ": " + targetUser.getEmail() + " [ID: " + targetUserId + "]");
    }

    private void logAdminAction(User admin, String actionType, UUID targetUserId, String details) {
        AdminLog log = new AdminLog();
        log.setAdmin(admin);
        log.setActionType(actionType);
        log.setTargetUserId(targetUserId);
        log.setDetails(details);
        adminLogRepository.save(log);
    }

    public void logImportAction(String adminEmail, String actionType, String details) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        logAdminAction(admin, actionType, null, details);
    }

    @Transactional
    public void importAchievements(List<Achievement> achievements) {
        achievementRepository.saveAll(achievements);
    }

    @Transactional
    public void toggleTopicStatus(UUID topicId, boolean isActive, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        var topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found"));

        topic.setActive(isActive);
        topicRepository.save(topic);

        String action = isActive ? "TOPIC_RESTORED" : "TOPIC_SUSPENDED";
        logAdminAction(admin, action, null,
                "Témakör " + (isActive ? "visszaállítva" : "felfüggesztve") + ": " + topic.getName() + " [ID: " + topicId + "]");
    }

    @Transactional
    public void toggleLessonStatus(UUID lessonId, boolean isActive, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        var lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found"));

        lesson.setActive(isActive);
        lessonRepository.save(lesson);

        String action = isActive ? "LESSON_RESTORED" : "LESSON_SUSPENDED";
        logAdminAction(admin, action, null,
                "Lecke " + (isActive ? "visszaállítva" : "felfüggesztve") + ": " + lesson.getTitle() + " (" + lesson.getDifficulty() + ") [ID: " + lessonId + "]");
    }

    @Transactional
    public void toggleExerciseStatus(UUID exerciseId, boolean isActive, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        var exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise not found"));

        exercise.setActive(isActive);
        exerciseRepository.save(exercise);

        String action = isActive ? "EXERCISE_RESTORED" : "EXERCISE_SUSPENDED";
        logAdminAction(admin, action, null,
                "Feladat (" + exercise.getType() + " - " + exercise.getLesson().getDifficulty() + ") " + (isActive ? "visszaállítva" : "felfüggesztve") + " [ID: " + exerciseId + "]");
    }

    @Transactional
    public void toggleAchievementStatus(UUID achievementId, boolean isActive, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        var achievement = achievementRepository.findById(achievementId)
                .orElseThrow(() -> new ResourceNotFoundException("Achievement not found"));

        achievement.setActive(isActive);
        achievementRepository.save(achievement);

        String action = isActive ? "ACHIEVEMENT_RESTORED" : "ACHIEVEMENT_SUSPENDED";
        logAdminAction(admin, action, null,
                "Kitüntetés " + (isActive ? "visszaállítva" : "felfüggesztve") + ": " + achievement.getName() + " [ID: " + achievementId + "]");
    }

    @Transactional(readOnly = true)
    public List<com.languageapp.backend.dto.response.ClassroomAdminResponse> getAllClassrooms() {
        return classroomRepository.findAllClassroomsIncludingDeleted().stream().map(c ->
                com.languageapp.backend.dto.response.ClassroomAdminResponse.builder()
                        .classroomId(c.getClassroomId())
                        .name(c.getName())
                        .description(c.getDescription())
                        .inviteCode(c.getInviteCode())
                        .createdAt(c.getCreatedAt())
                        .isActive(c.isActive())
                        .teacherName(c.getTeacher().getName())
                        .teacherEmail(c.getTeacher().getEmail())
                        .build()
        ).collect(Collectors.toList());
    }

    @Transactional
    public void toggleClassroomStatus(UUID classroomId, boolean isActive, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        var classroom = classroomRepository.findAllClassroomsIncludingDeleted().stream()
                .filter(c -> c.getClassroomId().equals(classroomId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));

        classroomRepository.updateClassroomStatus(classroomId, isActive);

        String action = isActive ? "CLASSROOM_RESTORED" : "CLASSROOM_BANNED";
        logAdminAction(admin, action, null,
                "Osztályterem " + (isActive ? "visszaállítva" : "felfüggesztve") + ": " + classroom.getName() + " [ID: " + classroomId + "]");
    }

    @Transactional(readOnly = true)
    public List<com.languageapp.backend.dto.response.TopicAdminResponse> getAllTopics() {
        return topicRepository.findAllTopicsIncludingDeleted().stream().map(topic ->
                com.languageapp.backend.dto.response.TopicAdminResponse.builder()
                        .topicId(topic.getTopicId())
                        .topicName(topic.getName())
                        .courseCode(topic.getCourse() != null ? topic.getCourse().getLanguageCode() : "en")
                        .isActive(topic.isActive())
                        .lessons(topic.getLessons().stream().map(lesson ->
                                com.languageapp.backend.dto.response.TopicAdminResponse.LessonDto.builder()
                                        .lessonId(lesson.getLessonId())
                                        .title(lesson.getTitle())
                                        .difficulty(lesson.getDifficulty())
                                        .isActive(lesson.isActive())
                                        .exercises(lesson.getExercises().stream().map(exercise ->
                                                com.languageapp.backend.dto.response.TopicAdminResponse.ExerciseDto.builder()
                                                        .exerciseId(exercise.getExerciseId())
                                                        .type(exercise.getType())
                                                        .content(exercise.getContent())
                                                        .correctAnswer(exercise.getCorrectAnswer())
                                                        .isActive(exercise.isActive())
                                                        .build()
                                        ).collect(Collectors.toList()))
                                        .build()
                        ).collect(Collectors.toList()))
                        .build()
        ).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Achievement> getAllAchievements() {
        return achievementRepository.findAll();
    }
}