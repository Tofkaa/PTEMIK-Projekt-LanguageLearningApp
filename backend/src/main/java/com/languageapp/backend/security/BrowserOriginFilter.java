package com.languageapp.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/** CSRF protection for APIs that consume or issue browser cookies, including login CSRF. */
public final class BrowserOriginFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final BrowserOriginPolicy policy;

    public BrowserOriginFilter(BrowserOriginPolicy policy) { this.policy = policy; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean cookieApi = path.equals("/api/auth") || path.startsWith("/api/auth/")
                || path.equals("/api/oauth-links") || path.startsWith("/api/oauth-links/");
        return !cookieApi || SAFE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var origins = Collections.list(request.getHeaders("Origin"));
        var sites = Collections.list(request.getHeaders("Sec-Fetch-Site"));
        boolean untrustedSite = sites.size() > 1 || (!sites.isEmpty()
                && !Set.of("same-origin", "same-site").contains(sites.getFirst()));
        if (origins.size() != 1 || !policy.allows(origins.getFirst()) || untrustedSite) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.addHeader("Vary", "Origin, Sec-Fetch-Site");
            com.languageapp.backend.observability.ApiErrors.write(request,response,403,"A művelet csak az alkalmazás oldaláról indítható.");
            return;
        }
        chain.doFilter(request, response);
    }
}
