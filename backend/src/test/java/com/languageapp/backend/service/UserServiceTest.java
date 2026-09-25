package com.languageapp.backend.service;

import com.languageapp.backend.entity.User;
import com.languageapp.backend.repository.ProgressRepository;
import com.languageapp.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProgressRepository progressRepository; // Szükséges a UserService konstruktorához

    @Mock
    private ImageStorageService imageStorageService; // Ezt mockoljuk, hogy ne hívja a Cloudinary-t

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
}