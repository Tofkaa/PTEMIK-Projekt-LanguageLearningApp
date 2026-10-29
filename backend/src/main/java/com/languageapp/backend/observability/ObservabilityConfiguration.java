package com.languageapp.backend.observability;

import io.micrometer.common.*;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.*;
import org.springframework.http.server.observation.*;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Configuration @Slf4j
public class ObservabilityConfiguration implements AsyncConfigurer {
    private final OperationsMetrics metrics;
    public ObservabilityConfiguration(OperationsMetrics metrics){this.metrics=metrics;}
    @Bean public static ServerRequestObservationConvention safeHttpObservationConvention(){return new DefaultServerRequestObservationConvention(){
        @Override public String getContextualName(ServerRequestObservationContext context){return "http "+method(context).getValue().toLowerCase(java.util.Locale.ROOT);}
        @Override public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context){return KeyValues.empty();}
        @Override protected KeyValue method(ServerRequestObservationContext context){
            String method=context.getCarrier().getMethod();return KeyValue.of("method",List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS","HEAD").contains(method)?method:"OTHER");
        }
    };}
    @Bean public static MeterFilter httpUriCardinalityLimit(){return MeterFilter.maximumAllowableTags("http.server.requests","uri",150,MeterFilter.deny());}
    @Bean(name="mailExecutor") public ThreadPoolTaskExecutor mailExecutor(){
        var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(2);executor.setMaxPoolSize(4);executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-task-");executor.setTaskDecorator(RequestDiagnostics::decorate);
        executor.setRejectedExecutionHandler((task,pool)->{metrics.mailRejected();throw new java.util.concurrent.RejectedExecutionException("Mail scheduling capacity exhausted.");});
        metrics.gauge("app.mail.queue",executor,value->value.getThreadPoolExecutor().getQueue().size());
        metrics.gauge("app.mail.active",executor,ThreadPoolTaskExecutor::getActiveCount);
        return executor;
    }
    // Keep MVC Callable execution separate when the explicitly named mail executor is present.
    @Bean(name={"applicationTaskExecutor","taskExecutor"}) @Primary
    public ThreadPoolTaskExecutor applicationTaskExecutor(){
        var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(2);executor.setMaxPoolSize(8);executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("http-async-");executor.setTaskDecorator(RequestDiagnostics::decorate);
        metrics.gauge("app.async.queue",executor,value->value.getThreadPoolExecutor().getQueue().size());return executor;
    }
    @Bean(name="taskScheduler") public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler safeTaskScheduler(){
        var scheduler=new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.setErrorHandler(failure->SafeDiagnostics.failure(log,"scheduled.failed",failure));return scheduler;
    }
    @Override public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler(){return (failure,method,arguments)->{
        metrics.mailUncaught();SafeDiagnostics.failure(log,"async.failed",failure);
    };}
}
