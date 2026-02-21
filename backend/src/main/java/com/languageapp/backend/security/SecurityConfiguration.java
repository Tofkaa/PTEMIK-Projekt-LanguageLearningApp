package com.languageapp.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;

    // 1. A fő HTTP biztonsági szabályok (A Házirend)
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable) // API-knál és JWT-nél a CSRF védelem felesleges, kikapcsoljuk
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // A login és register végpontokra bárki bejöhet
                        .anyRequest().authenticated() // Minden más végponthoz (pl. /api/lessons) "útlevél" (token) kell
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // Állapotmentes működés (Nincs Session)
                )
                .authenticationProvider(authenticationProvider()) // Megmondjuk, hogyan ellenőrizze az adatbázist
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class); // Beállítjuk az Ajtónállónkat a kapuba

        return http.build();
    }

    // 2. Az Adatbázis-ellenőrző (Megmondjuk, hol a UserDetailsService és milyen a jelszótitkosítás)
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    // 3. A Bejelentkeztető Menedzser (Őt fogjuk hívni jövő héten a bejelentkezésnél)
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)  {
        return config.getAuthenticationManager();
    }

    // 4. A Jelszótitkosító algoritmus (BCrypt a specifikáció szerint)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}