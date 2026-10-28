package com.languageapp.backend.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuthLoginHandoff handoff;
    private final OAuth2AuthenticationFailureHandler failureHandler;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (authentication instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken linked
                && linked.getPrincipal() instanceof OAuthLinkVerifiedUser principal) {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            response.setHeader("Cache-Control","no-store");
            response.setHeader("Referrer-Policy","no-referrer");
            if (!linked.getAuthorizedClientRegistrationId().equals(principal.proof().provider().name().toLowerCase(java.util.Locale.ROOT))) {
                failureHandler.redirectLink(response); return;
            }
            response.sendRedirect(frontendUrl+"/oauth-link/confirm#proof="+principal.proof().token());
            return;
        }
        if (!(authentication instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oauth)
                || !(oauth.getPrincipal() instanceof FederatedOAuth2User principal)
                || !oauth.getAuthorizedClientRegistrationId().equals(principal.provider().name().toLowerCase(java.util.Locale.ROOT))) {
            failureHandler.redirect(response,"identity_unavailable"); return;
        }
        String code;
        try { code=handoff.prepare(request,principal); }
        catch (RuntimeException unavailable) {
            log.warn("OAuth2 handoff could not be prepared.");
            failureHandler.redirect(response,"identity_unavailable"); return;
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        response.setHeader("Cache-Control","no-store");
        response.setHeader("Referrer-Policy","no-referrer");
        String redirectUrl=frontendUrl+"/oauth2/redirect#code="+code;

        log.info("OAuth2 sign-in completed for account ID: {}", principal.userId());
        response.sendRedirect(redirectUrl);
    }
}
