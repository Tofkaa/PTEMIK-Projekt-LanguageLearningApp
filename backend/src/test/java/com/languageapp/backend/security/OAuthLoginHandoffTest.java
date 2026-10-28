package com.languageapp.backend.security;

import com.languageapp.backend.enums.AuthProvider;
import com.languageapp.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class OAuthLoginHandoffTest {
    FederatedOAuth2User principal(){return new FederatedOAuth2User(new FederatedIdentityService.Binding(UUID.randomUUID(),AuthProvider.GOOGLE,"provider-subject"),
            new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),Map.of("sub","provider-subject"),"sub"));}
    @Test void proofRequiresTheSameBrowserSessionAndCanBeConsumedOnlyOnce() {
        var handoff=new OAuthLoginHandoff(Clock.systemUTC());var browser=new MockHttpServletRequest();var principal=principal();
        String code=handoff.prepare(browser,principal);assertTrue(code.matches("[A-Za-z0-9_-]{43}"));
        assertThrows(BadRequestException.class,()->handoff.consume(new MockHttpServletRequest(),code));
        assertThrows(BadRequestException.class,()->handoff.consume(browser,"x".repeat(43)));
        assertEquals(principal.userId(),handoff.consume(browser,code).userId());
        assertThrows(BadRequestException.class,()->handoff.consume(browser,code));
    }
    @Test void expiryAndReplacementInvalidateOldProofsWithoutCreatingTokens() {
        var time=new AtomicReference<>(Instant.parse("2026-10-28T10:00:00Z"));
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return time.get();}};
        var handoff=new OAuthLoginHandoff(clock);var browser=new MockHttpServletRequest();
        String old=handoff.prepare(browser,principal()),fresh=handoff.prepare(browser,principal());
        assertThrows(BadRequestException.class,()->handoff.consume(browser,old));time.set(time.get().plusSeconds(60));
        assertThrows(BadRequestException.class,()->handoff.consume(browser,fresh));
    }
    @Test void concurrentConsumersHaveExactlyOneWinner()throws Exception {
        var handoff=new OAuthLoginHandoff(Clock.systemUTC());var browser=new MockHttpServletRequest();String code=handoff.prepare(browser,principal());
        var go=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<Boolean> redeem=()->{go.await();try{handoff.consume(browser,code);return true;}catch(BadRequestException duplicate){return false;}};
        try{var one=pool.submit(redeem);var two=pool.submit(redeem);go.countDown();assertNotEquals(one.get(3,TimeUnit.SECONDS),two.get(3,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();}
    }
}
