package com.languageapp.backend.security;

import com.languageapp.backend.exception.BadRequestException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** A browser-bound, short-lived handoff. No application tokens are stored in the OAuth HTTP session. */
@Component @RequiredArgsConstructor
public class OAuthLoginHandoff {
    private static final String ATTRIBUTE=OAuthLoginHandoff.class.getName();
    private static final SecureRandom RANDOM=new SecureRandom();
    private final Clock clock;
    private record Pending(FederatedIdentityService.Binding identity,String codeHash,Instant expiresAt) implements java.io.Serializable { }
    public String prepare(HttpServletRequest request,FederatedOAuth2User principal) {
        byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String code=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var identity=new FederatedIdentityService.Binding(principal.userId(),principal.provider(),principal.subject());
        HttpSession session=request.getSession(true);
        synchronized(session) {
            session.removeAttribute("SPRING_SECURITY_CONTEXT");
            session.setAttribute(ATTRIBUTE,new Pending(identity,hash(code),clock.instant().plusSeconds(60)));
        }
        return code;
    }
    public FederatedIdentityService.Binding consume(HttpServletRequest request,String code) {
        if(code==null || !code.matches("[A-Za-z0-9_-]{43}"))throw invalid();
        HttpSession session=request.getSession(false);if(session==null)throw invalid();
        try {
            synchronized(session) {
                Object saved=session.getAttribute(ATTRIBUTE);
                if(!(saved instanceof Pending pending) || !clock.instant().isBefore(pending.expiresAt())
                        || !MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.UTF_8),pending.codeHash().getBytes(StandardCharsets.UTF_8)))throw invalid();
                session.removeAttribute(ATTRIBUTE);session.invalidate();return pending.identity();
            }
        }catch(IllegalStateException expiredSession){throw invalid();}
    }
    private static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable");}
    }
    private BadRequestException invalid(){return new BadRequestException("A közösségi belépés jóváhagyása érvénytelen vagy lejárt. Indíts új belépést.");}
}
