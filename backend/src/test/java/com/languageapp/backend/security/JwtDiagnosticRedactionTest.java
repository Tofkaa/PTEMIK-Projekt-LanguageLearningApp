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
}
