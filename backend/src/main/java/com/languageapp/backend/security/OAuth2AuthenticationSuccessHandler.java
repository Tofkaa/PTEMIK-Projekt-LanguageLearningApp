package com.languageapp.backend.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final FederatedIdentityService identities;
    private final OAuth2AuthenticationFailureHandler failureHandler;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oauth)
                || !(oauth.getPrincipal() instanceof FederatedOAuth2User principal)
                || !oauth.getAuthorizedClientRegistrationId().equals(principal.provider().name().toLowerCase(java.util.Locale.ROOT))) {
            failureHandler.redirect(response,"identity_unavailable"); return;
        }
        FederatedIdentityService.Login login;
        try { login=identities.issueSession(principal); }
        catch (org.springframework.security.oauth2.core.OAuth2AuthenticationException rejected) {
            failureHandler.redirect(response,rejected.getError().getErrorCode()); return;
        } catch (RuntimeException unavailable) {
            log.warn("OAuth2 session could not be issued.");
            failureHandler.redirect(response,"identity_unavailable"); return;
        }
        String jwtToken=login.accessToken(), refreshToken=login.refreshToken();

        // Refresh token into HttpOnly cookie
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(7 * 24 * 60 * 60) // 7 days
                .sameSite("Lax")
                .build();

        response.setHeader("Cache-Control","no-store");
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Redirect to the React Frontend's "landing" page with the Access Tokennel in URL params
        String redirectUrl = frontendUrl + "/oauth2/redirect?token=" + jwtToken;

        log.info("OAuth2 sign-in completed for account ID: {}", principal.userId());
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
