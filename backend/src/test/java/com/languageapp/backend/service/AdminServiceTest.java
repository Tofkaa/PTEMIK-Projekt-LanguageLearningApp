package com.languageapp.backend.service;

import com.languageapp.backend.entity.AdminLog;
import com.languageapp.backend.entity.LessonTopic;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AdminLogRepository adminLogRepository;
    @Mock private LessonTopicRepository topicRepository;
    // A többi repository mockolása is lehetséges, de ehhez a teszthez most a fenti 3 a legfontosabb

    @InjectMocks
    private AdminService adminService;

    private User adminUser;
    private User targetUser;
    private LessonTopic targetTopic;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setUserId(UUID.randomUUID());
        adminUser.setEmail("admin@languageapp.com");
        adminUser.setName("Fő Admin");
        adminUser.setRole(Role.ADMIN);

        targetUser = new User();
        targetUser.setUserId(UUID.randomUUID());
        targetUser.setEmail("diak@test.com");
        targetUser.setName("Teszt Diák");
        targetUser.setRole(Role.STUDENT);
        targetUser.setActive(true);

        targetTopic = new LessonTopic();
        targetTopic.setTopicId(UUID.randomUUID());
        targetTopic.setName("Alapvető Szókincs");
        targetTopic.setActive(true);
    }

    // --- 1. TESZT: Felhasználók lekérdezése (DTO konverzió ellenőrzése) ---
    @Test
    void getAllUsers_ShouldReturnUserAdminDTOList() {
        // Arrange
        when(userRepository.findAllUsersIncludingDeleted()).thenReturn(List.of(targetUser));

        // Act
        List<AdminService.UserAdminDTO> result = adminService.getAllUsers();

        // Assert
        assertEquals(1, result.size());
        assertEquals(targetUser.getUserId(), result.get(0).userId());
        assertEquals("diak@test.com", result.get(0).email());
        assertTrue(result.get(0).active());
    }

    // --- 2. TESZT: Jogosultság módosítása (Sikeres) ---
    @Test
    void updateUserRole_ValidUser_ShouldUpdateRoleAndLogAction() {
        // Arrange
        when(userRepository.findByEmail(adminUser.getEmail())).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(targetUser.getUserId())).thenReturn(Optional.of(targetUser));

        // Act
        adminService.updateUserRole(targetUser.getUserId(), Role.TEACHER, adminUser.getEmail());

        // Assert
        assertEquals(Role.TEACHER, targetUser.getRole(), "A felhasználó jogosultságának frissülnie kell TEACHER-re");
        verify(userRepository, times(1)).save(targetUser);

        // Ellenőrizzük, hogy az AdminLog is mentésre került-e!
        ArgumentCaptor<AdminLog> logCaptor = ArgumentCaptor.forClass(AdminLog.class);
        verify(adminLogRepository, times(1)).save(logCaptor.capture());

        AdminLog savedLog = logCaptor.getValue();
        assertEquals("ROLE_CHANGED", savedLog.getActionType());
        assertEquals(adminUser, savedLog.getAdmin());
        assertEquals(targetUser.getUserId(), savedLog.getTargetUserId());
        assertTrue(savedLog.getDetails().contains("TEACHER"));
    }

    // --- 3. TESZT: Jogosultság módosítása (Nem létező felhasználó) ---
    @Test
    void updateUserRole_UserNotFound_ShouldThrowException() {
        // Arrange
        when(userRepository.findByEmail(adminUser.getEmail())).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(targetUser.getUserId())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () ->
                adminService.updateUserRole(targetUser.getUserId(), Role.TEACHER, adminUser.getEmail())
        );

        verify(userRepository, never()).save(any(User.class));
        verify(adminLogRepository, never()).save(any(AdminLog.class));
    }

    // --- 4. TESZT: Témakör felfüggesztése (Sikeres) ---
    @Test
    void toggleTopicStatus_ValidTopic_ShouldToggleStatusAndLogAction() {
        // Arrange
        when(userRepository.findByEmail(adminUser.getEmail())).thenReturn(Optional.of(adminUser));
        when(topicRepository.findById(targetTopic.getTopicId())).thenReturn(Optional.of(targetTopic));

        // Act (felfüggesztjük -> isActive = false)
        adminService.toggleTopicStatus(targetTopic.getTopicId(), false, adminUser.getEmail());

        // Assert
        assertFalse(targetTopic.isActive(), "A témakörnek felfüggesztett státuszba kell kerülnie");
        verify(topicRepository, times(1)).save(targetTopic);

        // Ellenőrizzük a logolást
        ArgumentCaptor<AdminLog> logCaptor = ArgumentCaptor.forClass(AdminLog.class);
        verify(adminLogRepository, times(1)).save(logCaptor.capture());

        AdminLog savedLog = logCaptor.getValue();
        assertEquals("TOPIC_SUSPENDED", savedLog.getActionType());
    }
}