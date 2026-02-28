package com.languageapp.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service responsible for JSON Web Token (JWT) operations.
 * <p>
 * Handles the creation, parsing, validation, and cryptographic
 * signing of stateless access tokens used for API authentication.
 */
@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    /**
     * Generates a JWT access token for the given user.
     *
     * @param userDetails the authenticated user details
     * @return a signed JWT token string
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Generates a JWT access token with additional custom claims.
     * Automatically injects the user's primary role into the claims.
     *
     * @param extraClaims additional claims to include in the token payload
     * @param userDetails the authenticated user details
     * @return a signed JWT token string
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        log.debug("Generating JWT token for user: {}", userDetails.getUsername());

        // Add the user's primary authority (role) to the token payload
        extraClaims.put("role", userDetails.getAuthorities().iterator().next().getAuthority());

        String token = Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();

        log.info("JWT token successfully generated for user: {}", userDetails.getUsername());
        return token;
    }

    /**
     * Extracts the subject (username/email) from the token payload.
     *
     * @param token the JWT string
     * @return the extracted username
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Validates the token against the user details and checks its expiration.
     *
     * @param token       the JWT string
     * @param userDetails the expected user details
     * @return true if the token is valid and belongs to the user, false otherwise
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            boolean isValid = username.equals(userDetails.getUsername()) && !isTokenExpired(token);

            if (!isValid) {
                log.warn("JWT validation failed for user: {}. Token might be expired or subject mismatch.", userDetails.getUsername());
            }
            return isValid;
        } catch (Exception e) {
            log.error("JWT validation encountered an unexpected error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the given token has expired.
     *
     * @param token the JWT string
     * @return true if the expiration date is before the current time
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extracts the expiration date from the token.
     *
     * @param token the JWT string
     * @return the expiration {@link Date}
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Generic method to extract a specific claim from the token payload.
     *
     * @param token          the JWT string
     * @param claimsResolver a function to extract the desired claim type
     * @param <T>            the type of the claim
     * @return the extracted claim value
     */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parses the token and extracts all claims.
     * Cryptographically verifies the signature before parsing.
     *
     * @param token the JWT string
     * @return the verified {@link Claims} payload
     * @throws JwtException if the token is invalid, tampered with, or expired
     */
    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("JWT Token is expired: {}", e.getMessage());
            throw e; // Rethrow to be handled by the security filter
        } catch (JwtException e) {
            log.error("Invalid JWT token detected (Possible tampering attempt): {}", e.getMessage());
            throw e; // Rethrow to be handled by the security filter
        }
    }

    /**
     * Decodes the Base64 encoded secret key and returns a cryptographic {@link SecretKey}.
     *
     * @return the HMAC SHA signing key
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}