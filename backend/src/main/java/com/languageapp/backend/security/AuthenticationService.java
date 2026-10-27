package com.languageapp.backend.security;

import com.languageapp.backend.dto.request.LoginRequest;
import com.languageapp.backend.dto.request.RegisterRequest;
import com.languageapp.backend.dto.response.AuthResponse;
import com.languageapp.backend.entity.Course;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.entity.VerificationToken;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.CourseRepository;
import com.languageapp.backend.repository.PasswordResetTokenRepository;
import com.languageapp.backend.repository.UserRepository;
import com.languageapp.backend.repository.VerificationTokenRepository;
import com.languageapp.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * Service responsible for user authentication and registration logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final Clock clock;

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final CourseRepository courseRepository;

    public record AuthResult(AuthResponse responseDto, String refreshToken) {}

    @Transactional
    public AuthResult register(RegisterRequest request) {
        log.info("Attempting to register new user with email: {}", request.getEmail());

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("Registration failed: Email already exists - {}", request.getEmail());
            throw new BadRequestException("This email already exists!");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setVerified(false);

        if (request.getPreferredDifficulty() != null && !request.getPreferredDifficulty().isBlank()) {
            try {
                user.setPreferredDifficulty(com.languageapp.backend.enums.DifficultyLevel.valueOf(request.getPreferredDifficulty().toUpperCase()));
            } catch (IllegalArgumentException e) {
                user.setPreferredDifficulty(com.languageapp.backend.enums.DifficultyLevel.DYNAMIC);
            }
        } else {
            user.setPreferredDifficulty(com.languageapp.backend.enums.DifficultyLevel.DYNAMIC);
        }

        if (Role.ADMIN.equals(request.getRole())) {
            log.warn("Security Alert: Attempted ADMIN registration with email: {}", request.getEmail());
            throw new BadRequestException("Cannot register as an ADMIN user.");
        }

        user.setRole(request.getRole() != null ? request.getRole() : Role.STUDENT);
        log.info("Registering {} user with email: {}...", user.getRole(), request.getEmail());

        if (Boolean.TRUE.equals(request.getAcceptTerms())) {
            user.setTermsAcceptedAt(clock.instant());
        }

        String generatedFriendCode;
        do {
            generatedFriendCode = generateFriendCode();
        } while (userRepository.existsByFriendCode(generatedFriendCode));
        user.setFriendCode(generatedFriendCode);

        String generatedTag;
        int attempts = 0;
        do {
            int randomTag = 1000 + new java.util.Random().nextInt(9000);
            generatedTag = String.valueOf(randomTag);
            attempts++;
            if (attempts > 100) {
                throw new BadRequestException("Túl sok felhasználó van ezzel a névvel. Kérlek, válassz egy egyedibb nevet!");
            }
        } while (userRepository.existsByNameAndUserTag(request.getName(), generatedTag));
        user.setUserTag(generatedTag);

        String targetCourseCode = request.getCourseCode() != null ? request.getCourseCode() : "en";
        Course defaultCourse = courseRepository.findByLanguageCode(targetCourseCode)
                .orElseThrow(() -> new BadRequestException("Belső szerverhiba: A kiválasztott kurzus nem található!"));
        user.setActiveCourse(defaultCourse);

        userRepository.save(user);
        log.info("User successfully saved to database with ID: {}", user.getUserId());

        String tokenStr = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setToken(tokenStr);
        verificationToken.setUser(user);
        verificationToken.setExpiryDate(clock.instant().plus(24, ChronoUnit.HOURS));
        verificationTokenRepository.save(verificationToken);

        String verificationRecipient = user.getEmail();
        String verificationName = user.getName();
        sendMailAfterCommit(() -> emailService.sendVerificationEmail(verificationRecipient, verificationName, tokenStr));


        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().name())
                .build();

        user.setLastLogin(clock.instant());
        userRepository.save(user);

        var session = refreshTokenService.createSession(user.getUserId());
        String jwtToken = jwtService.generateSessionToken(userDetails, session.id());
        String refreshToken = session.rawToken();

        AuthResponse response = new AuthResponse(
                jwtToken,
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getUserTag(),
                user.getFriendCode(),
                user.isVerified(),
                user.getActiveCourse().getCourseId(),
                user.getActiveCourse().getLanguageCode()
        );

        log.info("Tokens successfully generated for user: {}", user.getEmail());
        return new AuthResult(response, refreshToken);
    }

    /**
     * Verifies the user's email using the provided token.
     */
    @Transactional
    public void verifyEmail(String token) {
        // Resolve only the ID before the shared user lock; do not cache a stale User via the token relation.
        UUID userId = verificationTokenRepository.findUserIdByToken(token)
                .orElseThrow(() -> new BadRequestException("Érvénytelen vagy lejárt token!"));
        User user = userRepository.findForSessionUpdate(userId)
                .filter(account -> account.isActive() && account.getRole() != Role.DELETED)
                .orElseThrow(() -> new BadRequestException("Érvénytelen vagy lejárt token!"));
        // Another consumer/deletion may have committed while this request waited for the user lock.
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Érvénytelen vagy lejárt token!"));

        if (!clock.instant().isBefore(verificationToken.getExpiryDate())) {
            throw new BadRequestException("A megerősítő link lejárt. Kérlek, igényelj újat a profilodban.");
        }

        user.setVerified(true);
        userRepository.save(user);

        verificationTokenRepository.delete(verificationToken);
        log.info("User {} successfully verified their email address.", user.getEmail());
    }

    /**
     * Starts the password reset process.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        log.info("Password reset requested for email: {}", email);

        userRepository.findByEmailForAuthentication(email).ifPresent(user -> {
            // Delete previous tokens
            passwordResetTokenRepository.deleteByUser_UserId(user.getUserId());

            // New token with 1 hour lifetime
            String tokenStr = UUID.randomUUID().toString();
            com.languageapp.backend.entity.PasswordResetToken resetToken = new com.languageapp.backend.entity.PasswordResetToken();
            resetToken.setToken(tokenStr);
            resetToken.setUser(user);
            resetToken.setExpiryDate(clock.instant().plus(1, ChronoUnit.HOURS));

            passwordResetTokenRepository.save(resetToken);

            // Capture scalars; the async dispatcher must not see an uncommitted token or a managed entity.
            String recipient = user.getEmail();
            sendMailAfterCommit(() -> emailService.sendPasswordResetEmail(recipient, tokenStr));
            log.info("Password reset token generated; email scheduled after commit for user ID: {}", user.getUserId());
        });

        // No exception, if the email does not exist
    }

    /**
     * Sets the new password based on the token
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        // Lock the user before loading mutable state; login uses the same lock before credential checks.
        // The second token lookup occurs after the lock, so concurrent consumption cannot reuse stale state.
        UUID userId = passwordResetTokenRepository.findUserIdByToken(token)
                .orElseThrow(() -> new BadRequestException("Érvénytelen vagy már felhasznált token!"));
        User user = userRepository.findForSessionUpdate(userId)
                .orElseThrow(() -> new BadRequestException("Érvénytelen vagy már felhasznált token!"));
        com.languageapp.backend.entity.PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Érvénytelen vagy már felhasznált token!"));

        if (!clock.instant().isBefore(resetToken.getExpiryDate())) {
            passwordResetTokenRepository.delete(resetToken);
            throw new BadRequestException("A jelszóvisszaállító link lejárt. Kérlek, igényelj újat.");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("Az új jelszó nem lehet azonos a jelenlegi jelszavaddal!");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Delete token to make sure it can not be used again
        passwordResetTokenRepository.delete(resetToken);

        // Security step: log out all sessions
        refreshTokenService.revokeAllForUser(user.getUserId());
        log.info("Password successfully reset for user: {}", user.getEmail());
    }

    @Transactional
    public AuthResult authenticate(LoginRequest request) {
        log.info("Authentication attempt for email: {}", request.getEmail());

        User user = userRepository.findByEmailForAuthentication(request.getEmail()).orElse(null);

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Unknown accounts still go through the DAO provider's password-timing protection.
        if (user == null) throw new org.springframework.security.authentication.BadCredentialsException("Invalid login credentials.");

        user.setLastLogin(clock.instant());
        userRepository.save(user);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        var session = refreshTokenService.createSession(user.getUserId());
        String jwtToken = jwtService.generateSessionToken(userDetails, session.id());
        String refreshToken = session.rawToken();

        AuthResponse response = new AuthResponse(
                jwtToken,
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getUserTag(),
                user.getFriendCode(),
                user.isVerified(),
                user.getActiveCourse().getCourseId(),
                user.getActiveCourse().getLanguageCode()
        );

        log.info("User successfully authenticated: {}", user.getEmail());
        return new AuthResult(response, refreshToken);
    }

    @Transactional
    public String logout(String rawRefreshToken) {
        return refreshTokenService.deleteByRawToken(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String rawRefreshToken) {
        log.info("Attempting to refresh access token...");

        return refreshTokenService.findByRawToken(rawRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(session -> {
                    User user = session.getUser();
                    if (!user.isActive()) throw new BadRequestException("Session unavailable, please log in again.");
                    UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                            .username(user.getEmail())
                            .password(user.getPasswordHash())
                            .authorities("ROLE_" + user.getRole().name())
                            .build();

                    String newAccessToken = jwtService.generateSessionToken(userDetails, session.getRefreshTokenId());
                    log.info("Access token successfully refreshed for user: {}", user.getEmail());

                    return new AuthResponse(
                            newAccessToken,
                            user.getUserId(),
                            user.getName(),
                            user.getEmail(),
                            user.getRole(),
                            user.getUserTag(),
                            user.getFriendCode(),
                            user.isVerified(),
                            user.getActiveCourse().getCourseId(),
                            user.getActiveCourse().getLanguageCode()
                    );
                })
                .orElseThrow(() -> {
                    log.warn("Refresh token validation failed: Token not found or invalid hash.");
                    return new BadRequestException("An error occurred, please login again.");
                });
    }

    /** External email scheduling must never race a token commit or turn a committed signup into an HTTP error. */
    private void sendMailAfterCommit(Runnable dispatch) {
        Runnable safeDispatch = () -> {
            try { dispatch.run(); }
            catch (RuntimeException failure) {
                // Do not log exception messages, addresses or the usable token.
                log.warn("Authentication email could not be scheduled after commit.");
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { safeDispatch.run(); }
            });
        } else {
            safeDispatch.run();
        }
    }

    private String generateFriendCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.util.Random rnd = new java.util.Random();
        StringBuilder sb = new StringBuilder(7);
        for (int i = 0; i < 6; i++) {
            if (i == 3) {
                sb.append('-');
            } else {
                sb.append(chars.charAt(rnd.nextInt(chars.length())));
            }
        }
        return sb.toString();
    }
}
