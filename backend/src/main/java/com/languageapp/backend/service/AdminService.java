package com.languageapp.backend.service;

import com.languageapp.backend.entity.AdminLog;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.AdminLogRepository;
import com.languageapp.backend.repository.UserRepository;
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

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAllUsersIncludingDeleted();
    }

    @Transactional(readOnly = true)
    public List<AdminLog> getSystemLogs() {

        return adminLogRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "loggedAt"));
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
}