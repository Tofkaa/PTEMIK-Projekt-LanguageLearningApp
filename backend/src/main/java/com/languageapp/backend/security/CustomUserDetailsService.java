package com.languageapp.backend.security;

import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Nem található felhasználó ezzel az e-mail címmel: " + email));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash()) // Itt a titkosított (BCrypt) jelszót várja
                .roles(user.getRole().toUpperCase()) // A jogosultság (pl. "STUDENT", "TEACHER")
                .build();
    }
}