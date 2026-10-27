package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.UserResponse;
import com.languageapp.backend.entity.Course;
import com.languageapp.backend.entity.EmailChangeToken;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.DifficultyLevel;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.enums.AuthProvider;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.CourseRepository;
import com.languageapp.backend.repository.EmailChangeTokenRepository;
import com.languageapp.backend.repository.ProgressRepository;
import com.languageapp.backend.repository.UserRepository;
import com.languageapp.backend.repository.FriendshipRepository;
import com.languageapp.backend.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Spy;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private com.languageapp.backend.repository.InvitationLinkRepository invitationLinkRepository;
    @Mock private com.languageapp.backend.repository.UserAchievementRepository userAchievementRepository;
    @Mock private com.languageapp.backend.repository.LessonAttemptRepository lessonAttemptRepository;
    @Mock private com.languageapp.backend.repository.ResultRepository resultRepository;
    @Spy private Clock clock = Clock.fixed(Instant.parse("2026-10-16T16:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProgressRepository progressRepository;

    @Mock
    private ImageStorageService imageStorageService;
    @Spy private ProfileImageSanitizer profileImageSanitizer = new ProfileImageSanitizer();

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailChangeTokenRepository emailChangeTokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private CourseRepository courseRepository; // ÚJ: Mockoljuk a CourseRepository-t

    @Mock private FriendshipRepository friendshipRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void deletionWithoutConfirmationCannotModifyAccountOrRevokeSessions() {
        User user = createTestUserWithCourse("local@example.test");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash("stored-hash");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        assertThrows(BadRequestException.class,
                () -> userService.deleteAndAnonymizeMyAccount(user.getEmail(), "password", false));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder, friendshipRepository, refreshTokenRepository, emailChangeTokenRepository);
        assertEquals("local@example.test", user.getEmail());
    }

    @Test
    void confirmedDeletionWithWrongPasswordCannotModifyAccount() {
        User user = createTestUserWithCourse("local@example.test");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash("stored-hash");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);
        assertThrows(BadRequestException.class,
                () -> userService.deleteAndAnonymizeMyAccount(user.getEmail(), "wrong", true));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(friendshipRepository, refreshTokenRepository, emailChangeTokenRepository);
    }

    @Test
    void confirmedDeletionVerifiesPasswordOnceAndAnonymizesAccount() {
        User user = createTestUserWithCourse("local@example.test");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash("stored-hash");
        user.setActive(true);
        user.setVerified(true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "stored-hash")).thenReturn(true);
        userService.deleteAndAnonymizeMyAccount(user.getEmail(), "correct", true);
        verify(passwordEncoder).matches("correct", "stored-hash");
        verify(refreshTokenRepository).deleteByUserUserId(user.getUserId());
        verify(lessonAttemptRepository).deleteByUserUserId(user.getUserId());
        verify(emailChangeTokenRepository).deleteByUser_UserId(user.getUserId());
        verify(friendshipRepository).deleteByUserUserIdOrFriendUserId(user.getUserId(), user.getUserId());
        verify(userRepository).save(user);
        assertFalse(user.isActive());
        assertFalse(user.isVerified());
        assertEquals("DELETED", user.getPasswordHash());
        assertTrue(user.getEmail().startsWith("deleted_"));
    }

    // Segédmetódus a teszt User létrehozására Course-al
    @Test void dataExportIncludesOnlyTheOwnersLessonAttempts() {
        User user = createTestUserWithCourse("export@example.test");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var attempt = new com.languageapp.backend.entity.LessonAttempt();
        var lesson = new com.languageapp.backend.entity.Lesson(); lesson.setLessonId(UUID.randomUUID());
        attempt.setAttemptId(UUID.randomUUID()); attempt.setUser(user); attempt.setLesson(lesson);
        attempt.setDifficulty("EASY"); attempt.setManualPractice(true);
        when(lessonAttemptRepository.findByUserUserId(user.getUserId())).thenReturn(java.util.List.of(attempt));
        var exported = userService.exportUserData(user.getEmail());
        var records = assertInstanceOf(java.util.List.class, exported.get("lessonAttempts"));
        assertEquals(1, records.size());
        var record = assertInstanceOf(java.util.Map.class, records.getFirst());
        assertEquals(attempt.getAttemptId(), record.get("attemptId")); assertEquals(true, record.get("manualPractice"));
        verify(lessonAttemptRepository).findByUserUserId(user.getUserId());
    }

    @Test
    void oauthCannotBypassEmailApprovalThroughLegacyDeletionEndpoint() {
        User user = createTestUserWithCourse("oauth@example.test");
        user.setAuthProvider(AuthProvider.DISCORD);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        assertThrows(BadRequestException.class,
                () -> userService.deleteAndAnonymizeMyAccount(user.getEmail(), "TÖRLÉS", true));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(friendshipRepository, refreshTokenRepository, emailChangeTokenRepository);
    }

    private User createTestUserWithCourse(String email) {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setEmail(email);

        Course course = new Course();
        course.setCourseId(UUID.randomUUID());
        course.setLanguageCode("en");
        user.setActiveCourse(course);

        return user;
    }

    @Test
    void updateProfilePicture_ShouldUploadAndSaveUrl() throws Exception {
        // 1. Arrange: Tesztadatok és viselkedés előkészítése
        String testEmail = "diak@gmail.com";
        String expectedUrl = "https://res.cloudinary.com/demo/image/upload/v1234/test.jpg";

        User mockUser = createTestUserWithCourse(testEmail);

        // Egy memóriában lévő, fiktív fájl létrehozása
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                validProfilePng()
        );

        // Szabályok megadása a mockok számára
        when(userRepository.findActiveProfileUserId(testEmail)).thenReturn(Optional.of(mockUser.getUserId()));
        when(userRepository.updateActiveProfilePicture(mockUser.getUserId(),expectedUrl)).thenReturn(1);
        when(imageStorageService.uploadProfileImage(any(byte[].class))).thenReturn(expectedUrl);

        // 2. Act: A tesztelendő metódus meghívása
        String actualUrl = userService.updateProfilePicture(testEmail, mockFile);

        // 3. Assert: Eredmények ellenőrzése
        assertEquals(expectedUrl, actualUrl, "A visszaadott URL-nek egyeznie kell a mockolt URL-lel");

        // Ellenőrizzük, hogy a mentés és a feltöltés pontosan egyszer futott-e le
        verify(userRepository).updateActiveProfilePicture(mockUser.getUserId(),expectedUrl);
        verify(userRepository,never()).save(any());
        ArgumentCaptor<byte[]> upload = ArgumentCaptor.forClass(byte[].class);
        verify(imageStorageService, times(1)).uploadProfileImage(upload.capture());
        var normalized = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(upload.getValue()));
        assertEquals(250, normalized.getWidth());
        assertEquals(250, normalized.getHeight());
    }

    private byte[] validProfilePng() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    @Test void invalidProfileCannotReachStorageOrChangeTheExistingUrl() {
        User user = createTestUserWithCourse("image@example.test");
        user.setProfilePictureUrl("https://example.test/original.png");
        when(userRepository.findActiveProfileUserId(user.getEmail())).thenReturn(Optional.of(user.getUserId()));
        var file = new MockMultipartFile("file", "avatar.png", "image/png", "<svg onload='alert(1)'/>".getBytes());
        assertThrows(BadRequestException.class, () -> userService.updateProfilePicture(user.getEmail(), file));
        assertEquals("https://example.test/original.png", user.getProfilePictureUrl());
        verifyNoInteractions(imageStorageService);
        verify(userRepository, never()).save(any());
        verify(userRepository,never()).updateActiveProfilePicture(any(),any());
    }

    @Test void accountDisappearingDuringUploadCannotBeRecreatedByAStaleSave() throws Exception {
        UUID id=UUID.randomUUID();
        when(userRepository.findActiveProfileUserId("image@example.test")).thenReturn(Optional.of(id));
        when(imageStorageService.uploadProfileImage(any(byte[].class))).thenReturn("https://example.test/new.png");
        when(userRepository.updateActiveProfilePicture(id,"https://example.test/new.png")).thenReturn(0);
        assertThrows(com.languageapp.backend.exception.ResourceNotFoundException.class,()->userService.updateProfilePicture(
                "image@example.test",new MockMultipartFile("file","avatar.png","image/png",validProfilePng())));
        verify(userRepository,never()).save(any());
    }

    @Test
    void updateUserName_ShouldUpdateNameAndGenerateNewFourDigitTag() {
        // 1. Arrange
        String testEmail = "diak@gmail.com";
        User mockUser = createTestUserWithCourse(testEmail);
        mockUser.setName("RegiNev");
        mockUser.setUserTag("1234");
        mockUser.setFriendCode("AB1-CD");
        mockUser.setRole(Role.STUDENT);
        mockUser.setPreferredDifficulty(DifficultyLevel.MEDIUM);

        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(mockUser));
        when(userRepository.existsByNameAndUserTag(eq("UjNev"), anyString())).thenReturn(false);

        // 2. Act
        UserResponse response = userService.updateUserName(testEmail, "  UjNev  ");

        // 3. Assert
        assertEquals("UjNev", mockUser.getName());
        assertEquals("UjNev", response.getName());
        assertNotNull(response.getUserTag());
        assertEquals(4, response.getUserTag().length(), "Az új userTag-nek 4 számjegyűnek kell lennie");
        assertEquals("AB1-CD", response.getFriendCode(), "A barátkódnak változatlanul kell maradnia");
        verify(userRepository, times(1)).save(mockUser);
    }

    @Test
    void changePassword_WithCorrectCurrentPassword_ShouldEncodeAndSaveNewPassword() {
        // 1. Arrange
        String testEmail = "diak@gmail.com";
        User mockUser = createTestUserWithCourse(testEmail);
        mockUser.setPasswordHash("hashed_old_password");

        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("jelenlegiJelszo", "hashed_old_password")).thenReturn(true);
        when(passwordEncoder.matches("ujJelszo123", "hashed_old_password")).thenReturn(false);
        when(passwordEncoder.encode("ujJelszo123")).thenReturn("hashed_new_password");

        // 2. Act
        userService.changePassword(testEmail, "jelenlegiJelszo", "ujJelszo123");

        // 3. Assert
        assertEquals("hashed_new_password", mockUser.getPasswordHash());
        verify(userRepository, times(1)).save(mockUser);
    }

    @Test
    void changePassword_WithWrongCurrentPassword_ShouldThrowBadRequestException() {
        // 1. Arrange
        String testEmail = "diak@gmail.com";
        User mockUser = createTestUserWithCourse(testEmail);
        mockUser.setPasswordHash("hashed_old_password");

        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("hibasJelszo", "hashed_old_password")).thenReturn(false);

        // 2. & 3. Act & Assert
        assertThrows(BadRequestException.class, () ->
                userService.changePassword(testEmail, "hibasJelszo", "ujJelszo123")
        );
        verify(userRepository, never()).save(any());
    }

    @Test
    void requestEmailChange_ShouldGenerateOtpAndSendBothEmails() {
        // 1. Arrange
        String currentEmail = "diak@gmail.com";
        String newEmail = "uj.diak@gmail.com";
        User mockUser = createTestUserWithCourse(currentEmail);
        mockUser.setPasswordHash("hashed_password");

        when(userRepository.findByEmailForAuthentication(currentEmail)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("helyesJelszo", "hashed_password")).thenReturn(true);
        when(userRepository.existsByEmail(newEmail)).thenReturn(false);

        // 2. Act
        userService.requestEmailChange(currentEmail, "  UJ.DIAK@gmail.com ", "helyesJelszo");

        // 3. Assert
        ArgumentCaptor<EmailChangeToken> tokenCaptor = ArgumentCaptor.forClass(EmailChangeToken.class);
        verify(emailChangeTokenRepository, times(1)).deleteByUser_UserId(mockUser.getUserId());
        verify(emailChangeTokenRepository, times(1)).save(tokenCaptor.capture());

        EmailChangeToken savedToken = tokenCaptor.getValue();
        assertEquals(newEmail, savedToken.getNewEmail());
        assertEquals(6, savedToken.getOtpCode().length(), "Az OTP kódnak 6 karakteresnek kell lennie");
        assertTrue(savedToken.getExpiryDate().isAfter(clock.instant()));

        verify(emailService, times(1)).sendEmailChangeOtp(eq(newEmail), eq(savedToken.getOtpCode()));
        verify(emailService, times(1)).sendEmailChangeSecurityAlert(eq(currentEmail), eq(newEmail));
    }

    @Test
    void verifyEmailChange_WithValidOtp_ShouldUpdateEmailAndDeleteToken() {
        // 1. Arrange
        String currentEmail = "diak@gmail.com";
        String newEmail = "uj.diak@gmail.com";
        User mockUser = createTestUserWithCourse(currentEmail);

        EmailChangeToken validToken = new EmailChangeToken();
        validToken.setUser(mockUser);
        validToken.setNewEmail(newEmail);
        validToken.setOtpCode("123456");
        validToken.setExpiryDate(clock.instant().plus(10, ChronoUnit.MINUTES));

        when(userRepository.findByEmailForAuthentication(currentEmail)).thenReturn(Optional.of(mockUser));
        when(emailChangeTokenRepository.findByUser_UserId(mockUser.getUserId())).thenReturn(Optional.of(validToken));
        when(userRepository.existsByEmail(newEmail)).thenReturn(false);

        // 2. Act
        userService.verifyEmailChange(currentEmail, "123456");

        // 3. Assert
        assertEquals(newEmail, mockUser.getEmail(), "A felhasználó e-mail címének frissülnie kellett");
        verify(userRepository, times(1)).save(mockUser);
        verify(emailChangeTokenRepository, times(1)).delete(validToken);
    }

    @Test
    void verifyEmailChange_WithInvalidOtp_ShouldThrowBadRequestException() {
        // 1. Arrange
        String currentEmail = "diak@gmail.com";
        User mockUser = createTestUserWithCourse(currentEmail);

        EmailChangeToken validToken = new EmailChangeToken();
        validToken.setUser(mockUser);
        validToken.setNewEmail("uj.diak@gmail.com");
        validToken.setOtpCode("123456");
        validToken.setExpiryDate(clock.instant().plus(10, ChronoUnit.MINUTES));

        when(userRepository.findByEmailForAuthentication(currentEmail)).thenReturn(Optional.of(mockUser));
        when(emailChangeTokenRepository.findByUser_UserId(mockUser.getUserId())).thenReturn(Optional.of(validToken));

        // 2. & 3. Act & Assert
        assertThrows(BadRequestException.class, () ->
                userService.verifyEmailChange(currentEmail, "000000")
        );

        assertEquals(currentEmail, mockUser.getEmail(), "Hibás kód esetén az eredeti e-mailnek kell maradnia");
        verify(userRepository, never()).save(any());
        verify(emailChangeTokenRepository, never()).delete(any());
    }

    // --- ÚJ TESZT: Nyelvváltás (changeActiveCourse) ---
    @Test
    void changeActiveCourse_WithValidCourse_ShouldUpdateAndReturnProfile() {
        // 1. Arrange
        String email = "diak@gmail.com";
        User mockUser = createTestUserWithCourse(email); // Jelenleg 'en'
        mockUser.setRole(Role.STUDENT);
        mockUser.setPreferredDifficulty(DifficultyLevel.EASY);

        Course newCourse = new Course();
        newCourse.setCourseId(UUID.randomUUID());
        newCourse.setLanguageCode("es");

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(courseRepository.findByLanguageCode("es")).thenReturn(Optional.of(newCourse));
        // A save() ne csináljon semmit, csak fusson le

        // 2. Act
        UserResponse response = userService.changeActiveCourse(email, "es");

        // 3. Assert
        assertEquals("es", mockUser.getActiveCourse().getLanguageCode());
        assertEquals("es", response.getActiveCourseCode());
        verify(userRepository, times(1)).save(mockUser);
    }

    @Test
    void changeActiveCourse_SameCourse_ShouldNotHitDatabase() {
        // 1. Arrange
        String email = "diak@gmail.com";
        User mockUser = createTestUserWithCourse(email); // Jelenleg 'en'
        mockUser.setRole(Role.STUDENT);
        mockUser.setPreferredDifficulty(DifficultyLevel.EASY);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));

        // 2. Act
        UserResponse response = userService.changeActiveCourse(email, "en");

        // 3. Assert
        assertEquals("en", response.getActiveCourseCode());
        verify(courseRepository, never()).findByLanguageCode(anyString());
        verify(userRepository, never()).save(any());
    }
    @Test void accountAnonymizationRevokesReusableInvitationLinks() {
        User user = createTestUserWithCourse("invitation-deletion@example.test");
        UUID ownerId = user.getUserId();
        userService.anonymizeAccount(user);
        verify(invitationLinkRepository).revokeAllForOwner(ownerId, clock.instant());
        assertFalse(user.isActive());
        assertFalse(user.isVerified());
    }

    @Test void dataExportIncludesInvitationMetadataWithoutReusableToken() {
        User user = createTestUserWithCourse("invitation-export@example.test");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var link = new com.languageapp.backend.entity.InvitationLink();
        link.setInvitationId(UUID.randomUUID()); link.setKind("friend"); link.setOwner(user);
        link.setToken("a".repeat(43)); link.setCreatedAt(clock.instant());
        when(invitationLinkRepository.findByOwnerUserId(user.getUserId())).thenReturn(java.util.List.of(link));
        var exported = userService.exportUserData(user.getEmail());
        var records = assertInstanceOf(java.util.List.class, exported.get("invitationLinks"));
        var record = assertInstanceOf(java.util.Map.class, records.getFirst());
        assertEquals(link.getInvitationId(), record.get("invitationId"));
        assertEquals("friend", record.get("kind"));
        assertEquals(clock.instant(), record.get("createdAt"));
        assertFalse(record.containsKey("token"));
        assertFalse(record.toString().contains(link.getToken()));
    }

    @Test void dataExportIncludesAchievementPresentationAndReadState() {
        User user = createTestUserWithCourse("achievement-export@example.test");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var trophy = new com.languageapp.backend.entity.Achievement(); trophy.setAchievementId(UUID.randomUUID());
        var award = new com.languageapp.backend.entity.UserAchievement(); award.setUserAchievementId(UUID.randomUUID());
        award.setAchievement(trophy); award.setAchievedAt(clock.instant()); award.setFeedbackEnabled(true);
        award.setPresentedAt(clock.instant());
        when(userAchievementRepository.findByUserUserId(user.getUserId())).thenReturn(java.util.List.of(award));
        var exported = userService.exportUserData(user.getEmail());
        var records = assertInstanceOf(java.util.List.class, exported.get("achievements"));
        var record = assertInstanceOf(java.util.Map.class, records.getFirst());
        assertEquals(award.getUserAchievementId(), record.get("userAchievementId"));
        assertEquals(clock.instant(), record.get("presentedAt")); assertNull(record.get("readAt"));
    }


    @ParameterizedTest @ValueSource(longs = {-1, 0, 1})
    void emailChangeExpiryIsExclusiveAndCannotConsumeExpiredCodes(long seconds) {
        User user = createTestUserWithCourse("expiry@example.test");
        when(userRepository.findByEmailForAuthentication(user.getEmail())).thenReturn(Optional.of(user));
        EmailChangeToken token = new EmailChangeToken(); token.setUser(user); token.setOtpCode("123456");
        token.setNewEmail("changed@example.test"); token.setExpiryDate(clock.instant().plusSeconds(seconds));
        when(emailChangeTokenRepository.findByUser_UserId(user.getUserId())).thenReturn(Optional.of(token));
        if (seconds > 0) {
            userService.verifyEmailChange(user.getEmail(), "123456");
            assertEquals("changed@example.test", user.getEmail());
            verify(emailChangeTokenRepository).delete(token);
        } else {
            assertThrows(BadRequestException.class, () -> userService.verifyEmailChange(user.getEmail(), "123456"));
            verify(userRepository, never()).save(any()); verify(emailChangeTokenRepository, never()).delete(any());
        }
    }
    @Test void incorrectEmailChangePasswordCannotReplaceAPendingChallengeOrSendMail() {
        User user = createTestUserWithCourse("wrong-password@example.test"); user.setPasswordHash("hash");
        when(userRepository.findByEmailForAuthentication(user.getEmail())).thenReturn(Optional.of(user));
        assertThrows(BadRequestException.class, () -> userService.requestEmailChange(user.getEmail(), "target@example.test", "incorrect"));
        verifyNoInteractions(emailChangeTokenRepository, emailService);
    }
    @Test void emailChangeRejectsInactiveAccountsBeforeLoadingOrSendingCodes() {
        User user = createTestUserWithCourse("inactive@example.test"); user.setActive(false);
        when(userRepository.findByEmailForAuthentication(user.getEmail())).thenReturn(Optional.of(user));
        assertThrows(com.languageapp.backend.exception.ForbiddenException.class,
                () -> userService.requestEmailChange(user.getEmail(), "target@example.test", "password"));
        assertThrows(com.languageapp.backend.exception.ForbiddenException.class,
                () -> userService.verifyEmailChange(user.getEmail(), "123456"));
        verifyNoInteractions(emailChangeTokenRepository, emailService, passwordEncoder);
    }
    @Test void failedOtpSchedulingStillAttemptsTheIndependentSecurityNotice() {
        User user = createTestUserWithCourse("notice@example.test"); user.setPasswordHash("hash");
        when(userRepository.findByEmailForAuthentication(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        doThrow(new org.springframework.core.task.TaskRejectedException("test-only queue rejection"))
                .when(emailService).sendEmailChangeOtp(anyString(), anyString());
        assertDoesNotThrow(() -> userService.requestEmailChange(user.getEmail(), "target@example.test", "password"));
        verify(emailChangeTokenRepository).save(any());
        verify(emailService).sendEmailChangeSecurityAlert(user.getEmail(), "target@example.test");
    }
}
