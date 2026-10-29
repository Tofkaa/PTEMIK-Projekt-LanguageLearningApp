package com.languageapp.backend.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtDiagnosticRedactionTest {
    @Test void parserDetailsAndRawCredentialsNeverReachDiagnosticsOrTheResponse()throws Exception {
        String secret="synthetic.raw.jwt";var jwt=mock(JwtService.class);when(jwt.extractUsername(secret)).thenThrow(new JwtException("Parser rejected "+secret));
        var filter=new JwtAuthenticationFilter(jwt,mock(UserDetailsService.class),mock(RefreshTokenService.class));
        Logger logger=(Logger)LoggerFactory.getLogger(JwtAuthenticationFilter.class);var captured=new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();captured.start();logger.addAppender(captured);
        try{
            var request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer "+secret);var response=new MockHttpServletResponse();
            filter.doFilter(request,response,new MockFilterChain());assertEquals(401,response.getStatus());assertFalse(response.getContentAsString().contains(secret));
            assertFalse(captured.list.isEmpty());assertTrue(captured.list.stream().noneMatch(event->event.getFormattedMessage().contains(secret)));
        }finally{logger.detachAppender(captured);captured.stop();}
    }

    private static final String PRIVATE_EMAIL="private-claims@example.test";
    private static final String TEST_KEY=java.util.Base64.getEncoder().encodeToString(new byte[64]);
    private static final java.time.Instant NOW=java.time.Instant.parse("2026-10-29T12:00:00Z");
    private JwtService service(java.time.Instant at,boolean otherKey) {
        var service=new JwtService(java.time.Clock.fixed(at,java.time.ZoneOffset.UTC));
        byte[] bytes=new byte[64];if(otherKey)java.util.Arrays.fill(bytes,(byte)7);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"secretKey",java.util.Base64.getEncoder().encodeToString(bytes));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"jwtExpiration",1000L);
        service.initializeSigningKey();return service;
    }
    private org.springframework.security.core.userdetails.UserDetails user(String email) {
        return org.springframework.security.core.userdetails.User.withUsername(email).password("unused").roles("STUDENT").build();
    }
    /** Capture DEBUG too: sensitive diagnostics must not reappear when troubleshooting is enabled. */
    private void captureServiceLogs(java.util.function.Consumer<ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>> test) {
        Logger logger=(Logger)LoggerFactory.getLogger(JwtService.class);var previous=logger.getLevel();
        var captured=new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();captured.start();logger.addAppender(captured);logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {test.accept(captured);}finally{logger.detachAppender(captured);logger.setLevel(previous);captured.stop();}
    }
    private void assertRedacted(ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> captured,String... sensitive) {
        assertFalse(captured.list.isEmpty());
        for(var event:captured.list) {
            assertNull(event.getThrowableProxy(),"Parser causes/claims must not be attached to diagnostic events");
            for(String value:sensitive)assertFalse(event.getFormattedMessage().contains(value),"Sensitive value in JWT diagnostic");
            assertFalse(event.getFormattedMessage().contains(TEST_KEY));
        }
    }
    @Test void issuanceAndSubjectMismatchDoNotLogAccountOrSessionClaimsEvenAtDebug() {
        captureServiceLogs(captured->{
            var service=service(NOW,false);var sid=java.util.UUID.randomUUID();
            String token=service.generateSessionToken(user(PRIVATE_EMAIL),sid);
            assertEquals(PRIVATE_EMAIL,service.extractUsername(token));assertEquals(sid,service.extractSessionId(token));
            assertTrue(service.isTokenValid(token,user(PRIVATE_EMAIL)));
            assertFalse(service.isTokenValid(token,user("other-private@example.test")));
            assertRedacted(captured,PRIVATE_EMAIL,"other-private@example.test",token,sid.toString());
        });
    }
    @Test void malformedAndInvalidSignatureDiagnosticsDoNotIncludeSubmittedCredentials() {
        captureServiceLogs(captured->{
            String token=service(NOW,true).generateToken(user(PRIVATE_EMAIL));
            var verifier=service(NOW,false);
            String malformed="PRIVATE_MALFORMED_INPUT.raw.jwt";
            assertThrows(JwtException.class,()->verifier.extractUsername(malformed));
            assertThrows(JwtException.class,()->verifier.extractUsername(token));
            assertFalse(verifier.isTokenValid(token,user(PRIVATE_EMAIL)));
            assertRedacted(captured,PRIVATE_EMAIL,malformed,token);
        });
    }
    @Test void expiredClaimsAreRejectedWithoutLoggingParserDetails() {
        captureServiceLogs(captured->{
            String token=service(NOW,false).generateToken(user(PRIVATE_EMAIL));var verifier=service(NOW.plusSeconds(60),false);
            assertThrows(io.jsonwebtoken.ExpiredJwtException.class,()->verifier.extractUsername(token));
            assertFalse(verifier.isTokenValid(token,user(PRIVATE_EMAIL)));
            assertRedacted(captured,PRIVATE_EMAIL,token,"JWT expired","milliseconds","Allowed clock skew");
        });
    }
    @Test void unexpectedParserExceptionCannotInjectItsMessageOrThrowableIntoLogs() {
        captureServiceLogs(captured->{
            var verifier=spy(service(NOW,false));String token="PRIVATE_RAW_CREDENTIAL";
            doThrow(new IllegalStateException("PRIVATE_PARSER_DETAIL\n"+token+PRIVATE_EMAIL+TEST_KEY)).when(verifier).extractUsername(token);
            assertFalse(verifier.isTokenValid(token,user(PRIVATE_EMAIL)));
            assertRedacted(captured,PRIVATE_EMAIL,token,"PRIVATE_PARSER_DETAIL");
        });
    }
}
