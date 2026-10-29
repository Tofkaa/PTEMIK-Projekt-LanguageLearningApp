package com.languageapp.backend.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import jakarta.servlet.*;
import java.time.Clock;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class RequestDiagnosticsTest {
    SimpleMeterRegistry registry=new SimpleMeterRegistry();
    OperationsMetrics metrics=new OperationsMetrics(registry,Clock.systemUTC());
    RequestDiagnosticsFilter filter=new RequestDiagnosticsFilter(metrics,JsonMapper.builder().build(),Clock.systemUTC());
    @AfterEach void clean(){MDC.clear();registry.close();}
    @Test void serverReplacesClientIdsAndCorrelatesBodyHeaderAndMdc()throws Exception {
        var request=new MockHttpServletRequest("GET","/api/test");var response=new MockHttpServletResponse();
        String spoof=java.util.UUID.randomUUID().toString();request.addHeader("X-Request-ID",spoof);
        filter.doFilter(request,response,(req,res)->{
            assertNotEquals(spoof,RequestDiagnostics.currentId());
            ApiErrors.write(request,response,403,"Denied");
        });
        String id=response.getHeader("X-Request-ID");assertEquals(id,RequestDiagnostics.valid(id));
        assertEquals(id,JsonMapper.builder().build().readTree(response.getContentAsString()).get("requestId").asString());
        assertNull(MDC.get("requestId"));assertEquals(1,registry.counter("app.http.completed","outcome","client_error").count());
    }
    @Test void unexpectedFailureIsGenericAndRestoresPriorThreadContext()throws Exception {
        MDC.put("requestId","prior-thread-value");
        var request=new MockHttpServletRequest("GET","/api/test?ignored");var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->{throw new ServletException("PRIVATE_BODY_AND_TOKEN");});
        assertEquals(500,response.getStatus());assertFalse(response.getContentAsString().contains("PRIVATE_BODY_AND_TOKEN"));
        assertEquals("prior-thread-value",MDC.get("requestId"));
        assertEquals(1,registry.counter("app.http.completed","outcome","server_error").count());
    }
    @Test void asyncRedispatchKeepsTheIdAndCountsCompletionOnce()throws Exception {
        var request=new MockHttpServletRequest("GET","/api/stream");request.setAsyncSupported(true);var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->request.startAsync(request,response));
        String id=response.getHeader("X-Request-ID");assertNull(MDC.get("requestId"));
        assertEquals(0,registry.counter("app.http.completed","outcome","success").count());
        request.setDispatcherType(DispatcherType.ASYNC);
        filter.doFilter(request,response,(req,res)->assertEquals(id,RequestDiagnostics.currentId()));
        request.getAsyncContext().complete();
        assertEquals(1,registry.counter("app.http.completed","outcome","success").count());assertNull(MDC.get("requestId"));
    }
    @Test void taskDecorationTransfersOnlyServerCorrelationAndCleansUpOnFailure() {
        String id=java.util.UUID.randomUUID().toString();MDC.put("requestId",id);MDC.put("unsafe","PRIVATE_VALUE");
        Runnable task=RequestDiagnostics.decorate(()->{assertEquals(id,RequestDiagnostics.currentId());assertNull(MDC.get("unsafe"));throw new IllegalStateException("private");});
        MDC.clear();MDC.put("worker","prior");
        assertThrows(IllegalStateException.class,task::run);assertNull(MDC.get("requestId"));assertEquals("prior",MDC.get("worker"));
    }
    @Test void forgedOrMalformedMdcCannotBeTransferredAsARequestId() {
        MDC.put("requestId","PRIVATE_INVALID_MDC");Runnable task=RequestDiagnostics.decorate(()->assertNull(MDC.get("requestId")));
        MDC.clear();task.run();assertNull(RequestDiagnostics.valid("123"));assertNull(RequestDiagnostics.valid(java.util.UUID.randomUUID().toString().toUpperCase()));
    }
}
