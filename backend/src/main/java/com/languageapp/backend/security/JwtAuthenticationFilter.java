package com.languageapp.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.Nonnull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull  HttpServletResponse response,
            @Nonnull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Megnézzük, van-e a kérés fejlécében "Authorization" sor
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 2. Ha nincs ilyen fejléc, vagy nem "Bearer " szóval kezdődik, akkor ez egy publikus kérés (pl. login)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response); // Továbbengedjük a kérést ellenőrzés nélkül
            return;
        }

        // 3. Kinyerjük magát a tokent a "Bearer " szó után (a 7. karaktertől)
        jwt = authHeader.substring(7);
        userEmail = jwtService.extractUsername(jwt); // A JwtService-től megkérdezzük, kié a token

        // 4. Ha találtunk emailt, és a felhasználó még nincs bejelentkeztetve a Spring rendszerébe
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Lekérjük a felhasználó adatait az adatbázisból (a CustomUserDetailsService segítségével)
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

            // 5. Megkérjük a JwtService-t, hogy ellenőrizze, érvényes-e a token
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // 6. Ha érvényes, létrehozunk egy "hivatalos" Spring Security belépőkártyát
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities() // Ide kerülnek a jogosultságok (pl. ROLE_STUDENT)
                );

                // Hozzáadjuk a kérés extra adatait (pl. IP cím, böngésző adatok)
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // 7. Betesszük a kártyát a Spring "zsebébe" (SecurityContext), így a szerver tudni fogja, ki van bent
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 8. Befejeztük az ellenőrzést, a kérés mehet tovább a Controller felé
        filterChain.doFilter(request, response);
    }
}