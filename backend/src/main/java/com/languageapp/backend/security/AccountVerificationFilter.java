package com.languageapp.backend.security;

import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.dto.ErrorResponse;
import com.languageapp.backend.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;

/** Server counterpart of VerifiedRoute. Recovery, profile and normal practice remain usable. */
public final class AccountVerificationFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final Clock clock;
    public AccountVerificationFilter(UserRepository users,ObjectMapper mapper,Clock clock){this.users=users;this.mapper=mapper;this.clock=clock;}
    static boolean requiresVerification(String path,String challengeId){
        for(String prefix:new String[]{"/actuator","/api/classrooms","/api/assignments","/api/friendships","/api/challenges","/api/invitations","/api/admin","/api/exercises"})
            if(path.equals(prefix)||path.startsWith(prefix+"/"))return true;
        return path.equals("/api/lessons/all-for-challenge") || (path.startsWith("/api/lessons/") && challengeId!=null);
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(requiresVerification(path,request.getParameter("challengeId")) && auth!=null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)){
            var state=users.findAccessState(auth.getName()).orElse(null);
            if(state==null || !state.active() || !state.verified() || state.role()==Role.DELETED){
                response.setStatus(403);response.setContentType("application/json;charset=UTF-8");response.setHeader("Cache-Control","no-store");
                response.getWriter().write(mapper.writeValueAsString(new ErrorResponse(clock.instant(),403,"Forbidden",
                        "Ennek a funkciónak a használatához előbb meg kell erősítened az e-mail címedet.",path)));
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
