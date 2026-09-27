package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.UserResponse;
import com.languageapp.backend.entity.EmailChangeToken;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.DifficultyLevel;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.EmailChangeTokenRepository;
import com.languageapp.backend.repository.ProgressRepository;
import com.languageapp.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProgressRepository progressRepository; // Szükséges a UserService konstruktorához

    @Mock
    private ImageStorageService imageStorageService; // Ezt mockoljuk, hogy ne hívja a Cloudinary-t

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailChangeTokenRepository emailChangeTokenRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    @Test
    void updateProfilePicture_ShouldUploadAndSaveUrl() throws Exception {
        // 1. Arrange: Tesztadatok és viselkedés előkészítése
        String testEmail = "diak@gmail.com";
        String expectedUrl = "https://res.cloudinary.com/demo/image/upload/v1234/test.jpg";

        User mockUser = new User();
        mockUser.setEmail(testEmail);

        // Egy memóriában lévő, fiktív fájl létrehozása
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "fiktiv_kep_tartalom".getBytes()
        );

        // Szabályok megadása a mockok számára
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(mockUser));
        when(imageStorageService.uploadProfileImage(mockFile)).thenReturn(expectedUrl);

        // 2. Act: A tesztelendő metódus meghívása
        String actualUrl = userService.updateProfilePicture(testEmail, mockFile);

        // 3. Assert: Eredmények ellenőrzése
        assertEquals(expectedUrl, actualUrl, "A visszaadott URL-nek egyeznie kell a mockolt URL-lel");
        assertEquals(expectedUrl, mockUser.getProfilePictureUrl(), "A User entitásban is frissülnie kellett az URL-nek");

        // Ellenőrizzük, hogy a mentés és a feltöltés pontosan egyszer futott-e le
        verify(userRepository, times(1)).save(mockUser);
        verify(imageStorageService, times(1)).uploadProfileImage(mockFile);
    }

    @Test
    void updateUserName_ShouldUpdateNameAndGenerateNewFourDigitTag() {
        // 1. Arrange
        String testEmail = "diak@gmail.com";
        User mockUser = new User();
        mockUser.setUserId(UUID.randomUUID());
        mockUser.setEmail(testEmail);
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
        User mockUser = new User();
        mockUser.setEmail(testEmail);
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
        User mockUser = new User();
        mockUser.setEmail(testEmail);
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
        User mockUser = new User();
        mockUser.setUserId(UUID.randomUUID());
        mockUser.setEmail(currentEmail);
        mockUser.setPasswordHash("hashed_password");

        when(userRepository.findByEmail(currentEmail)).thenReturn(Optional.of(mockUser));
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
        assertTrue(savedToken.getExpiryDate().isAfter(LocalDateTime.now()));

        verify(emailService, times(1)).sendEmailChangeOtp(eq(newEmail), eq(savedToken.getOtpCode()));
        verify(emailService, times(1)).sendEmailChangeSecurityAlert(eq(currentEmail), eq(newEmail));
    }

    @Test
    void verifyEmailChange_WithValidOtp_ShouldUpdateEmailAndDeleteToken() {
        // 1. Arrange
        String currentEmail = "diak@gmail.com";
        String newEmail = "uj.diak@gmail.com";
        User mockUser = new User();
        mockUser.setUserId(UUID.randomUUID());
        mockUser.setEmail(currentEmail);

        EmailChangeToken validToken = new EmailChangeToken();
        validToken.setUser(mockUser);
        validToken.setNewEmail(newEmail);
        validToken.setOtpCode("123456");
        validToken.setExpiryDate(LocalDateTime.now().plusMinutes(10));

        when(userRepository.findByEmail(currentEmail)).thenReturn(Optional.of(mockUser));
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
        User mockUser = new User();
        mockUser.setUserId(UUID.randomUUID());
        mockUser.setEmail(currentEmail);

        EmailChangeToken validToken = new EmailChangeToken();
        validToken.setUser(mockUser);
        validToken.setNewEmail("uj.diak@gmail.com");
        validToken.setOtpCode("123456");
        validToken.setExpiryDate(LocalDateTime.now().plusMinutes(10));

        when(userRepository.findByEmail(currentEmail)).thenReturn(Optional.of(mockUser));
        when(emailChangeTokenRepository.findByUser_UserId(mockUser.getUserId())).thenReturn(Optional.of(validToken));

        // 2. & 3. Act & Assert
        assertThrows(BadRequestException.class, () ->
                userService.verifyEmailChange(currentEmail, "000000")
        );

        assertEquals(currentEmail, mockUser.getEmail(), "Hibás kód esetén az eredeti e-mailnek kell maradnia");
        verify(userRepository, never()).save(any());
        verify(emailChangeTokenRepository, never()).delete(any());
    }
}