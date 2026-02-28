package com.languageapp.backend.security;

import com.languageapp.backend.entity.User;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom implementation of Spring Security's {@link UserDetailsService}.
 * <p>
 * Responsible for retrieving user authentication and authorization
 * details directly from the database during the login process and JWT validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user's specific data by their email address.
     *
     * @param email the email identifying the user whose data is required.
     * @return a fully populated {@link UserDetails} object.
     * @throws UsernameNotFoundException if the user could not be found.
     * Required by Spring Security to prevent user enumeration attacks.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Attempting to load user details for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Authentication failed: User not found with email: {}", email);
                    return new UsernameNotFoundException("User not found with email: " + email);
                });

        log.debug("User details successfully loaded for email: {}", email);

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash()) // Expected to be a BCrypt hash
                .authorities(user.getRole())
                .build();
    }
}