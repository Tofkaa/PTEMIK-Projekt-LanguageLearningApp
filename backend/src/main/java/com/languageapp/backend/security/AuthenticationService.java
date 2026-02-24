package com.languageapp.backend.security;

import com.languageapp.backend.dto.request.LoginRequest;
import com.languageapp.backend.dto.request.RegisterRequest;
import com.languageapp.backend.dto.response.AuthResponse;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    // BELSŐ FUTÁR: Ez viszi át az adatokat a Controllerhez
    public record AuthResult(AuthResponse responseDto, String refreshToken) {}

    // 1. REGISZTRÁCIÓ
    public AuthResult register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Ez az e-mail cím már foglalt!");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());

        if (request.getRole() != null && !request.getRole().trim().isEmpty()) {
            user.setRole(request.getRole().toUpperCase());
        } else {
            user.setRole("STUDENT");
        }

        userRepository.save(user);
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(user.getRole())
                .build();

        String jwtToken = jwtService.generateToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(user.getUserId());

        AuthResponse response = new AuthResponse(
                jwtToken,
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );

        return new AuthResult(response, refreshToken);
    }

    // 2. BEJELENTKEZÉS
    public AuthResult authenticate(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Felhasználó nem található"));

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        String jwtToken = jwtService.generateToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(user.getUserId());

        AuthResponse response = new AuthResponse(
                jwtToken,
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );

        return new AuthResult(response, refreshToken);
    }
}