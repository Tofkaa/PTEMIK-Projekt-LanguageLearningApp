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
        log.debug("auth.account.load_requested");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("auth.account.not_found");
                    return new UsernameNotFoundException("User not found with email: " + email);
                });

        log.debug("auth.account.loaded");

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .disabled(!user.isActive() || user.getRole() == com.languageapp.backend.enums.Role.DELETED) // Inaktív vagy törölt user nem léphet be!
                .authorities("ROLE_" + user.getRole().name())
                .build();
    }
}