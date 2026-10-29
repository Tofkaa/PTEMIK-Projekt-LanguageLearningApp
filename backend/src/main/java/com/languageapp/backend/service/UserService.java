package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.ProgressResponse;
import com.languageapp.backend.dto.response.UserResponse;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.DifficultyLevel;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.exception.IncorrectOtpException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * Service handling user profile and progress retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final Clock clock;
    private static final int EMAIL_CHANGE_MAX_FAILURES = 5;

    private final UserRepository userRepository;
    private final ProgressRepository progressRepository;
    private final ImageStorageService imageStorageService;
    private final ProfileImageSanitizer profileImageSanitizer;
    private final PasswordEncoder passwordEncoder;
    private final EmailChangeTokenRepository emailChangeTokenRepository;
    private final EmailService emailService;
    private final CourseRepository courseRepository;
    private final SseService courseNotifications;
    private final ResultRepository resultRepository;
    private final FriendshipRepository friendshipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LessonAttemptRepository lessonAttemptRepository;
    private final DynamicPracticeAttemptRepository dynamicPracticeAttemptRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final InvitationLinkRepository invitationLinkRepository;
    private final OAuthIdentityRepository oAuthIdentityRepository;
    private final OAuthLinkIntentRepository oauthLinkIntentRepository;

    /**
     * Retrieves the profile information of the authenticated user.
     *
     * @param email the email extracted from the security token
     * @return a safe {@link UserResponse} DTO
     */
    @Transactional(readOnly = true)
    public UserResponse getUserProfile(String email) {
        log.debug("Fetching profile for user: {}", email);

        User user = getUserByEmail(email);

        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .xp(user.getXp())
                .streak(user.getStreak())
                .preferredDifficulty(user.getPreferredDifficulty().name())
                .userTag(user.getUserTag())
                .friendCode(user.getFriendCode())
                .isVerified(user.isVerified())
                .profilePictureUrl(user.getProfilePictureUrl())
                .activeCourseId(user.getActiveCourse().getCourseId())
                .activeCourseCode(user.getActiveCourse().getLanguageCode())
                .activeCourseRevision(user.getActiveCourseRevision())
                .authProvider(user.getAuthProvider() != null ? user.getAuthProvider().name() : "LOCAL")
                .build();
    }

    /**
     * Retrieves the learning progress of the authenticated user.
     *
     * @param email the email extracted from the security token
     * @return a list of {@link ProgressResponse} DTOs
     */
    @Transactional(readOnly = true)
    public List<ProgressResponse> getUserProgress(String email) {
        log.debug("Fetching progress for user: {}", email);

        User user = getUserByEmail(email);

        return progressRepository.findByUserUserId(user.getUserId()).stream()
                .map(progress -> ProgressResponse.builder()
                        .lessonId(progress.getLesson().getLessonId())
                        .lessonTitle(progress.getLesson().getTitle())
                        .highestScore(progress.getHighestScore())
                        .isCompleted(progress.getIsCompleted())
                        .lastAttemptAt(progress.getLastAttemptAt())
                        .completedAt(progress.getCompletedAt())
                        .build())
                .toList();
    }

    /**
     * Fetches a user by email or throws a standard ResourceNotFoundException.
     */
    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("Authenticated user not found in the database with email: {}", email);
                    return new ResourceNotFoundException("User not found");
                });
    }

    /**
     * Updates a user's preferences by email or throws a standard ResourceNotFoundException
     * @param email - The user's email address allocated to the account
     * @param newDifficulty - The new difficulty setting they wish to save to their profile
     */

    @Transactional
    public void updateUserPreferences(String email, String newDifficulty) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPreferredDifficulty(DifficultyLevel.valueOf(newDifficulty));
        userRepository.save(user);
        log.info("User {} updated preferred difficulty to {}", email, newDifficulty);
    }

    @Transactional
    public UserResponse changeActiveCourse(String email, String courseCode, long expectedRevision, UUID intentId) {
        if (courseCode == null || courseCode.isBlank() || expectedRevision < 0 || intentId == null)
            throw new BadRequestException("Érvényes kurzus, verzió és műveletazonosító szükséges.");
        String code=courseCode.trim().toLowerCase(Locale.ROOT);
        var selection=userRepository.findCourseSelection(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (intentId.equals(selection.lastIntentId())) {
            if (!code.equals(selection.code())) throw new com.languageapp.backend.exception.ConflictException("A műveletazonosító már másik kurzushoz tartozik.");
            return getUserProfile(email);
        }
        if (selection.revision()!=expectedRevision) throw courseConflict();
        Course course=code.equals(selection.code()) ? null : courseRepository.findByLanguageCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Nem található kurzus ezzel a kóddal: "+code));
        UUID target=course==null?selection.courseId():course.getCourseId();
        if (userRepository.changeCourseSelection(selection.userId(),target,expectedRevision,intentId)!=1) {
            var latest=userRepository.findCourseSelection(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
            if (!intentId.equals(latest.lastIntentId()) || !code.equals(latest.code())) throw courseConflict();
        } else courseNotifications.sendPingAfterCommit(email);
        return getUserProfile(email);
    }
    private com.languageapp.backend.exception.ConflictException courseConflict() {
        return new com.languageapp.backend.exception.ConflictException("A kurzusválasztás egy másik lapon vagy eszközön megváltozott. Frissítettük az állapotot; válassz újra.");
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public String updateProfilePicture(String email, MultipartFile file) {
        UUID userId = userRepository.findActiveProfileUserId(email)
                .orElseThrow(() -> new ResourceNotFoundException("Aktív felhasználó nem található."));

        try {

            byte[] image = profileImageSanitizer.sanitize(file);
            String imageUrl = imageStorageService.uploadProfileImage(image);

            if (userRepository.updateActiveProfilePicture(userId, imageUrl) != 1)
                throw new ResourceNotFoundException("A fiók a feltöltés közben megszűnt vagy inaktívvá vált.");

            log.info("User {} updated profile picture", email);
            return imageUrl;
        } catch (IOException e) {
            log.error("Failed to upload profile picture for user {}: {}", email, e.getMessage());
            throw new RuntimeException("Nem sikerült feltölteni a képet. Kérlek, próbáld újra.");
        }
    }

    /**
     * Updates the user's display name and generates a new unique 4-digit userTag.
     */
    @Transactional
    public UserResponse updateUserName(String email, String newName) {
        if (newName == null || newName.trim().length() < 3) {
            throw new BadRequestException("A felhasználónévnek legalább 3 karakter hosszúnak kell lennie!");
        }

        String trimmedName = newName.trim();
        User user = getUserByEmail(email);

        if (trimmedName.equals(user.getName())) {
            throw new BadRequestException("Az új felhasználónév nem lehet azonos a jelenlegivel!");
        }

        String generatedTag;
        int attempts = 0;
        java.util.Random random = new java.util.Random();
        do {
            int randomTag = 1000 + random.nextInt(9000);
            generatedTag = String.valueOf(randomTag);
            attempts++;
            if (attempts > 100) {
                throw new BadRequestException("Túl sok felhasználó van ezzel a névvel. Kérlek, válassz egy egyedibb nevet!");
            }
        } while (userRepository.existsByNameAndUserTag(trimmedName, generatedTag));

        user.setName(trimmedName);
        user.setUserTag(generatedTag);
        userRepository.save(user);

        log.info("User {} updated name to {}#{}", email, trimmedName, generatedTag);

        return getUserProfile(email);
    }
    /**
     * Changes the authenticated user's password after verifying the current password.
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        // Login, reset and password change serialize credential checks on the same account row.
        User user = userRepository.findByEmailForAuthentication(email)
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (currentPassword == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("A megadott jelenlegi jelszó hibás!");
        }

        if (newPassword == null || newPassword.length() < 6) {
            throw new BadRequestException("Az új jelszónak legalább 6 karakter hosszúnak kell lennie!");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("Az új jelszó nem lehet azonos a jelenlegi jelszavaddal!");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        // Atomic with the credential write: every prior access-token SID and refresh token is revoked.
        refreshTokenRepository.deleteByUserUserId(user.getUserId());
        log.info("Password changed and prior sessions revoked for account ID: {}", user.getUserId());
    }

    /**
     * Step 1: Validates password and target email, then generates a 6-digit OTP.
     */
    @Transactional
    public void requestEmailChange(String currentEmail, String newEmail, String currentPassword) {
        User user = getAccountForEmailChange(currentEmail);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("A megadott jelenlegi jelszó hibás!");
        }

        if (newEmail == null || newEmail.isBlank() || !newEmail.contains("@")) {
            throw new BadRequestException("Kérlek, adj meg egy érvényes e-mail címet!");
        }

        String normalizedNewEmail = newEmail.trim().toLowerCase(Locale.ROOT);

        if (normalizedNewEmail.equalsIgnoreCase(currentEmail)) {
            throw new BadRequestException("Az új e-mail cím nem lehet azonos a jelenlegivel!");
        }

        if (userRepository.existsByEmail(normalizedNewEmail)) {
            throw new BadRequestException("Ez az e-mail cím már használatban van!");
        }

        emailChangeTokenRepository.deleteByUser_UserId(user.getUserId());
        emailChangeTokenRepository.flush();

        // 6-digit safe OTP  (100000 - 999999)
        String otpCode = String.valueOf(100000 + new SecureRandom().nextInt(900000));

        EmailChangeToken token = new EmailChangeToken();
        token.setUser(user);
        token.setNewEmail(normalizedNewEmail);
        token.setOtpCode(otpCode);
        token.setExpiryDate(clock.instant().plus(15, ChronoUnit.MINUTES));

        emailChangeTokenRepository.save(token);

        String recipient = user.getEmail();
        // Independent callbacks: failure to schedule one email must not suppress the other.
        CommittedEmail.send(() -> emailService.sendEmailChangeOtp(normalizedNewEmail, otpCode));
        CommittedEmail.send(() -> emailService.sendEmailChangeSecurityAlert(recipient, normalizedNewEmail));
        log.info("Email change OTP generated for user {} -> new email: {}", currentEmail, normalizedNewEmail);
    }

    /**
     * Step 2: Verifies the 6-digit OTP and updates the user's primary email.
     */
    @Transactional(noRollbackFor = IncorrectOtpException.class)
    public void verifyEmailChange(String currentEmail, String otpCode) {
        User user = getAccountForEmailChange(currentEmail);

        EmailChangeToken token = emailChangeTokenRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new BadRequestException("Nincs folyamatban lévő e-mail módosítási kérés!"));

        if (!clock.instant().isBefore(token.getExpiryDate())) {
            throw new BadRequestException("Az ellenőrző kód lejárt! Kérlek, indítsd újra a folyamatot.");
        }

        if (token.getFailedAttempts() >= EMAIL_CHANGE_MAX_FAILURES) {
            throw new IncorrectOtpException("Túl sok hibás próbálkozás történt. Kérj új ellenőrző kódot.");
        }
        if (otpCode == null || !token.getOtpCode().equals(otpCode.trim())) {
            int failures = token.getFailedAttempts() + 1;
            token.setFailedAttempts(failures);
            emailChangeTokenRepository.save(token);
            // This exception alone commits: no account/email mutation has happened in this branch.
            throw new IncorrectOtpException(failures >= EMAIL_CHANGE_MAX_FAILURES
                    ? "Túl sok hibás próbálkozás történt. Kérj új ellenőrző kódot."
                    : "Hibás ellenőrző kód. Még " + (EMAIL_CHANGE_MAX_FAILURES - failures) + " próbálkozásod maradt.");
        }

        if (userRepository.existsByEmail(token.getNewEmail())) {
            throw new BadRequestException("Ez az e-mail cím időközben foglalt lett!");
        }

        String oldEmail = user.getEmail();
        user.setEmail(token.getNewEmail());
        userRepository.save(user);

        emailChangeTokenRepository.delete(token);
        log.info("User successfully changed email from {} to {}", oldEmail, user.getEmail());
    }

    /** Serialize issue/reissue/consume and avoid loading a stale User before the shared account lock. */
    private User getAccountForEmailChange(String email) {
        User user = userRepository.findByEmailForAuthentication(email)
                .orElseThrow(() -> new ResourceNotFoundException("User nem található."));
        if (!user.isActive() || user.getRole() == Role.DELETED)
            throw new com.languageapp.backend.exception.ForbiddenException("Ez a fiók nem használható e-mail módosítására.");
        return user;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User nem található."));

        Map<String, Object> exportData = new LinkedHashMap<>();

        // 1. Personal data
        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", user.getUserId());
        profile.put("name", user.getName());
        profile.put("email", user.getEmail());
        profile.put("userTag", user.getUserTag());
        profile.put("registeredAt", user.getCreatedAt());
        profile.put("xp", user.getXp());
        profile.put("streak", user.getStreak());
        exportData.put("profile", profile);

        // 2. Learning results
        List<Result> results = resultRepository.findByUserUserId(user.getUserId());
        List<Map<String, Object>> resultData = results.stream().map(r -> {
            Map<String, Object> res = new HashMap<>();
            res.put("lessonTitle", r.getLesson().getTitle());
            res.put("difficulty", r.getDifficulty());
            res.put("score", r.getScore());
            res.put("submittedAt", r.getSubmittedAt());
            return res;
        }).toList();
        exportData.put("learningResults", resultData);
        exportData.put("lessonAttempts", lessonAttemptRepository.findByUserUserId(user.getUserId()).stream().map(a -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("attemptId", a.getAttemptId());
            data.put("lessonId", a.getLesson().getLessonId());
            data.put("challengeId", a.getChallengeId());
            data.put("difficulty", a.getDifficulty());
            data.put("manualPractice", a.isManualPractice());
            data.put("startedAt", a.getStartedAt());
            data.put("exerciseIds", a.getExerciseIds());
            data.put("result", a.getSubmissionResult());
            return data;
        }).toList());

        exportData.put("dynamicPracticeAttempts", dynamicPracticeAttemptRepository.findByUserUserId(user.getUserId()).stream().map(a -> {
            Map<String,Object> data=new LinkedHashMap<>();data.put("attemptId",a.getAttemptId());data.put("courseId",a.getCourse().getCourseId());
            data.put("startedAt",a.getStartedAt());data.put("completedAt",a.getCompletedAt());data.put("answers",a.getAcceptedAnswers());data.put("result",a.getResult());return data;
        }).toList());

        // 3. Progress
        List<Progress> progress = progressRepository.findByUserUserId(user.getUserId());
        List<Map<String, Object>> progressData = progress.stream().map(p -> {
            Map<String, Object> prog = new HashMap<>();
            prog.put("lessonTitle", p.getLesson().getTitle());
            prog.put("isCompleted", p.getIsCompleted());
            prog.put("completedDifficulty", p.getCompletedDifficulty());
            return prog;
        }).toList();
        exportData.put("progress", progressData);

        exportData.put("achievements", userAchievementRepository.findByUserUserId(user.getUserId()).stream().map(a -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("userAchievementId", a.getUserAchievementId());
            entry.put("achievementId", a.getAchievement().getAchievementId());
            entry.put("achievedAt", a.getAchievedAt());
            entry.put("feedbackEnabled", a.isFeedbackEnabled());
            entry.put("presentedAt", a.getPresentedAt());
            entry.put("readAt", a.getReadAt());
            return entry;
        }).toList());
        exportData.put("invitationLinks", invitationLinkRepository.findByOwnerUserId(user.getUserId()).stream().map(link -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("invitationId", link.getInvitationId()); entry.put("kind", link.getKind());
            entry.put("classroomId", link.getClassroom() == null ? null : link.getClassroom().getClassroomId());
            entry.put("createdAt", link.getCreatedAt()); entry.put("updatedAt", link.getUpdatedAt());
            entry.put("expiresAt", link.getExpiresAt()); entry.put("revokedAt", link.getRevokedAt());
            return entry;
        }).toList());
        exportData.put("oauthIdentities", oAuthIdentityRepository.findByUserUserId(user.getUserId()).stream().map(identity -> {
            Map<String,Object> entry = new LinkedHashMap<>();
            entry.put("provider", identity.getProvider().name()); entry.put("subject", identity.getSubject());
            entry.put("linkedAt", identity.getLinkedAt()); return entry;
        }).toList());
        return exportData;
    }

    @Transactional
    public void deleteAndAnonymizeMyAccount(String email, String confirmationInput, boolean confirmed) {
        User user = userRepository.findByEmailForAccountDeletion(email)
                .orElseThrow(() -> new ResourceNotFoundException("User nem található."));

        boolean isLocalWithPassword = user.getAuthProvider() == com.languageapp.backend.enums.AuthProvider.LOCAL
                && user.getPasswordHash() != null;

        if (isLocalWithPassword) {
            if (!confirmed) {
                throw new BadRequestException("A fióktörléshez külön meg kell erősítened a törlési szándékodat.");
            }
            if (confirmationInput == null || !passwordEncoder.matches(confirmationInput, user.getPasswordHash())) {
                throw new BadRequestException("A megadott jelszó hibás!");
            }
        } else {
            throw new BadRequestException("Google- vagy Discord-fiók törléséhez kérj e-mailes jóváhagyó linket a Beállításokban.");
        }

        anonymizeAccount(user);
    }

    /** Only called after local credentials or an email challenge have been verified. */
    void anonymizeAccount(User user) {
        if (user.getRole() == Role.ADMIN) throw new com.languageapp.backend.exception.ForbiddenException(
                "Adminisztrátori fiók törléséhez előbb egy másik admin módosítsa a jogosultságodat.");
        oauthLinkIntentRepository.deleteById(user.getUserId());
        oAuthIdentityRepository.deleteByUserUserId(user.getUserId());
        invitationLinkRepository.revokeAllForOwner(user.getUserId(), clock.instant());
        lessonAttemptRepository.deleteByUserUserId(user.getUserId());
        dynamicPracticeAttemptRepository.deleteByUserUserId(user.getUserId());
        friendshipRepository.deleteByUserUserIdOrFriendUserId(user.getUserId(), user.getUserId());
        refreshTokenRepository.deleteByUserUserId(user.getUserId());
        emailChangeTokenRepository.deleteByUser_UserId(user.getUserId());


        user.setName("Törölt Felhasználó");
        user.setEmail("deleted_" + UUID.randomUUID() + "@languageapp.com");
        user.setPasswordHash("DELETED");
        user.setUserTag("0000");
        user.setFriendCode(null);
        user.setProfilePictureUrl(null);
        user.setProviderId(null);

        user.setVerified(false);
        user.setActive(false);

        userRepository.save(user);

        log.info("GDPR Anonymization completed for user ID: {}", user.getUserId());
    }
}
