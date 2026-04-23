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

import java.util.List;
import java.util.UUID;

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

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAllUsersIncludingDeleted();
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
                .collect(java.util.stream.Collectors.toList());
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
                "Role changed from " + oldRole + " to " + newRole.name());
    }

    @Transactional
    public void toggleUserStatus(UUID targetUserId, boolean isActive, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();

        userRepository.updateUserStatus(targetUserId, isActive);

        String action = isActive ? "USER_UNBANNED" : "USER_BANNED";
        logAdminAction(admin, action, targetUserId, "User status set to active=" + isActive);
    }

    private void logAdminAction(User admin, String actionType, UUID targetUserId, String details) {
        AdminLog log = new AdminLog();
        log.setAdmin(admin);
        log.setActionType(actionType);
        log.setTargetUserId(targetUserId);
        log.setDetails(details);
        adminLogRepository.save(log);
    }

    @Transactional
    public void importAchievements(List<Achievement> achievements) {
        achievementRepository.saveAll(achievements);
    }

    @Transactional
    public void deleteTopic(UUID topicId, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        topicRepository.deleteById(topicId);
        logAdminAction(admin, "TOPIC_DELETED", null, "Topic deleted: " + topicId);
    }

    @Transactional
    public void deleteLesson(UUID lessonId, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        lessonRepository.deleteById(lessonId);
        logAdminAction(admin, "LESSON_DELETED", null, "Lesson deleted: " + lessonId);
    }

    @Transactional
    public void deleteExercise(UUID exerciseId, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        exerciseRepository.deleteById(exerciseId);
        logAdminAction(admin, "EXERCISE_DELETED", null, "Exercise deleted: " + exerciseId);
    }

    @Transactional
    public void deleteAchievement(UUID achievementId, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        achievementRepository.deleteById(achievementId);
        logAdminAction(admin, "ACHIEVEMENT_DELETED", null, "Achievement deleted: " + achievementId);
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
        ).collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public void toggleClassroomStatus(UUID classroomId, boolean isActive, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        classroomRepository.updateClassroomStatus(classroomId, isActive);

        String action = isActive ? "CLASSROOM_RESTORED" : "CLASSROOM_BANNED";
        logAdminAction(admin, action, null, "Classroom status set to active=" + isActive + " for ID: " + classroomId);
    }
}