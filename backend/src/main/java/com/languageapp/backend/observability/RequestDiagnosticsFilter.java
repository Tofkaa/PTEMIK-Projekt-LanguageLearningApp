package com.languageapp.backend.observability;

import com.languageapp.backend.exception.dto.ErrorResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Outer servlet boundary covers security denials, controllers, error and async redispatches. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE+10) @RequiredArgsConstructor @Slf4j
public class RequestDiagnosticsFilter extends OncePerRequestFilter {
    private final OperationsMetrics metrics;
    private final ObjectMapper mapper;
    private final Clock clock;
    private static final String FINISHED=RequestDiagnosticsFilter.class.getName()+".finished";
    @Override protected boolean shouldNotFilterAsyncDispatch(){return false;}
    @Override protected boolean shouldNotFilterErrorDispatch(){return false;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        String id=RequestDiagnostics.valid((String)request.getAttribute(RequestDiagnostics.ATTRIBUTE));
        if(id==null){id=UUID.randomUUID().toString();request.setAttribute(RequestDiagnostics.ATTRIBUTE,id);}
        AtomicBoolean finished=(AtomicBoolean)request.getAttribute(FINISHED);
        if(finished==null){finished=new AtomicBoolean();request.setAttribute(FINISHED,finished);}
        var once=finished;
        response.setHeader(RequestDiagnostics.HEADER,id);
        String previous=MDC.get("requestId");MDC.put("requestId",id);
        try{chain.doFilter(request,response);}
        catch(ServletException|RuntimeException|IOException failure){
            SafeDiagnostics.failure(log,"http.unhandled",failure);
            if(!response.isCommitted()){
                response.resetBuffer();response.setStatus(500);response.setContentType("application/json;charset=UTF-8");
                try{response.getWriter().write(mapper.writeValueAsString(new ErrorResponse(clock.instant(),500,"Internal Server Error",
                        "An unexpected internal server error occurred.",request.getRequestURI())));}
                catch(IOException disconnected){log.debug("HTTP error response connection closed.");}
            }
        }finally{
            if(request.isAsyncStarted()){
                var listener=new AsyncListener(){
                    @Override public void onComplete(AsyncEvent event){if(once.compareAndSet(false,true))metrics.http(response.getStatus());}
                    @Override public void onTimeout(AsyncEvent event) { }
                    @Override public void onError(AsyncEvent event) { }
                    @Override public void onStartAsync(AsyncEvent event){event.getAsyncContext().addListener(this);}
                };
                try{request.getAsyncContext().addListener(listener);}catch(IllegalStateException alreadyCompleted){if(once.compareAndSet(false,true))metrics.http(response.getStatus());}
            }else if(once.compareAndSet(false,true))metrics.http(response.getStatus());
            if(previous==null)MDC.remove("requestId");else MDC.put("requestId",previous);
        }
    }
}
