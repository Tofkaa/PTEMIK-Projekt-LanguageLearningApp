package com.languageapp.backend.security;

import com.languageapp.backend.exception.TooManyRequestsException;
import com.languageapp.backend.exception.dto.ErrorResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;

public final class ApiRateLimitFilter extends OncePerRequestFilter {
    private final ApiRequestLimiter limiter;
    private final ClientAddressResolver addresses;
    private final ObjectMapper mapper;
    private final Clock clock;
    public ApiRateLimitFilter(ApiRequestLimiter limiter,ClientAddressResolver addresses,ObjectMapper mapper,Clock clock){
        this.limiter=limiter;this.addresses=addresses;this.mapper=mapper;this.clock=clock;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getServletPath();
        if(path==null || path.isEmpty()) path=request.getRequestURI().substring(request.getContextPath().length());
        RequestOperation operation=RequestOperation.route(request.getMethod(),path);
        if(operation!=null) {
            var auth=SecurityContextHolder.getContext().getAuthentication();
            String account=auth!=null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)?auth.getName():null;
            try { limiter.request(operation,addresses.address(request),account,path.startsWith("/api/invitations/")); }
            catch(TooManyRequestsException limited) {
                response.setStatus(429);response.setHeader("Retry-After",Long.toString(limited.retryAfterSeconds()));
                response.setHeader("Cache-Control","no-store");response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(mapper.writeValueAsString(new ErrorResponse(clock.instant(),429,"Too Many Requests",limited.getMessage(),path)));
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
