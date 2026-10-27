package com.languageapp.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Set;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {
    @Value("${app.frontend.url}") private String frontendUrl;
    private static final Set<String> CODES=Set.of("email_in_use","unverified_email","identity_unavailable","access_denied");
    @Override public void onAuthenticationFailure(HttpServletRequest request,HttpServletResponse response,AuthenticationException failure) throws IOException {
        String code=failure instanceof OAuth2AuthenticationException oauth?oauth.getError().getErrorCode():"identity_unavailable";
        redirect(response,code);
    }
    public void redirect(HttpServletResponse response,String code) throws IOException {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        response.setHeader("Cache-Control","no-store");
        response.sendRedirect(frontendUrl+"/login?oauth_error="+(CODES.contains(code)?code:"identity_unavailable"));
    }
}
