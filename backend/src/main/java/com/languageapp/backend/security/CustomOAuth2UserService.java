package com.languageapp.backend.security;

import com.languageapp.backend.entity.Course;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.AuthProvider;
import com.languageapp.backend.enums.DifficultyLevel;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.repository.CourseRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // Let Spring load the user data from the provider
        OAuth2User oAuth2User = super.loadUser(userRequest);

        try {
            return processOAuth2User(userRequest, oAuth2User);
        } catch (Exception ex) {
            log.error("Hiba az OAuth2 bejelentkezés során", ex);
            throw new OAuth2AuthenticationException(ex.getMessage());
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        // Source of the request?
        String registrationId = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        AuthProvider authProvider = AuthProvider.valueOf(registrationId);

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");

        // In case of Discord name could come in a 'username' field
        if (name == null && attributes.containsKey("username")) {
            name = (String) attributes.get("username");
        }

        if (email == null || email.isEmpty()) {
            throw new RuntimeException("Nem sikerült lekérdezni az E-mail címet a szolgáltatótól!");
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;

        if (userOptional.isPresent()) {
            user = userOptional.get();
            // ACCOUNT LINKING: If the email already exists in the DB let them through
            if (user.getAuthProvider().equals(AuthProvider.LOCAL)) {
                log.info("Létező lokális fiók automatikus összekapcsolása a(z) {} OAuth2 szolgáltatóval.", authProvider);
            }
        } else {
            // NEW REGISTRATION
            log.info("Új OAuth2 felhasználó regisztrációja: {}", email);
            user = new User();
            user.setEmail(email);
            user.setName(name != null ? name : "Felhasználó");
            user.setAuthProvider(authProvider);
            user.setProviderId(oAuth2User.getName());
            user.setRole(Role.STUDENT);
            user.setPreferredDifficulty(DifficultyLevel.DYNAMIC);
            user.setVerified(true); // email was verified externally
            user.setLastLogin(LocalDateTime.now());

            Course defaultCourse = courseRepository.findByLanguageCode("en")
                    .orElseThrow(() -> new RuntimeException("Alapértelmezett kurzus nem található!"));
            user.setActiveCourse(defaultCourse);

            // Friend code and tag generation
            user.setFriendCode(generateUniqueFriendCode());
            user.setUserTag(generateUniqueUserTag(user.getName()));

            // Get pfp if available
            if (authProvider == AuthProvider.DISCORD) {
                String avatarHash = (String) attributes.get("avatar");
                String discordId = (String) attributes.get("id");

                if (avatarHash != null && discordId != null) {
                    String discordImageUrl = String.format("https://cdn.discordapp.com/avatars/%s/%s.png", discordId, avatarHash);
                    user.setProfilePictureUrl(discordImageUrl);
                }
            } else if (attributes.containsKey("picture")) {
                user.setProfilePictureUrl((String) attributes.get("picture"));
            }

            userRepository.save(user);
        }

        return oAuth2User;
    }

    private String generateUniqueFriendCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rnd = new Random();
        String code;
        do {
            StringBuilder sb = new StringBuilder(7);
            for (int i = 0; i < 6; i++) {
                if (i == 3) sb.append('-');
                else sb.append(chars.charAt(rnd.nextInt(chars.length())));
            }
            code = sb.toString();
        } while (userRepository.existsByFriendCode(code));
        return code;
    }

    private String generateUniqueUserTag(String name) {
        Random rnd = new Random();
        String tag;
        do {
            tag = String.valueOf(1000 + rnd.nextInt(9000));
        } while (userRepository.existsByNameAndUserTag(name, tag));
        return tag;
    }
}