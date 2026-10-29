package com.languageapp.backend.observability;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Dedicated sampler stays independent of business jobs. Signals are advisory thresholds, not a capacity claim. */
@Component @Slf4j
public class OperationsMonitor {
    public enum Signal { DATABASE_WAIT, SLOW_QUERY, MAIL_QUEUE, MAIL_FAILURE, HTTP_5XX, JOB_STALE, SSE_STALL, PROBE_STALE }
    private final MeterRegistry registry;
    private final Clock clock;
    private final DatabaseWaitProbe probe;
    private final EnumMap<Signal,AtomicInteger> signals=new EnumMap<>(Signal.class);
    private final AtomicInteger waiters=new AtomicInteger();
    private final AtomicInteger activeQueries=new AtomicInteger();
    private final AtomicReference<Double> oldestQuery=new AtomicReference<>(0d);
    private final AtomicReference<Double> oldest=new AtomicReference<>(0d);
    private final AtomicReference<Instant> probeAt;
    private double previousRequests,previousErrors,previousMailFailures;
    private ScheduledExecutorService sampler;
    @Value("${app.observability.monitor.enabled:true}") private boolean enabled=true;
    public OperationsMonitor(MeterRegistry registry,Clock clock,DatabaseWaitProbe probe){
        this.registry=registry;this.clock=clock;this.probe=probe;probeAt=new AtomicReference<>(clock.instant());
        for(var signal:Signal.values()){
            var value=new AtomicInteger();signals.put(signal,value);Gauge.builder("app.operations.signal",value,AtomicInteger::get).tag("signal",signal.name()).register(registry);
        }
        Gauge.builder("app.database.query.active",activeQueries,AtomicInteger::get).register(registry);
        Gauge.builder("app.database.query.oldest",oldestQuery,AtomicReference::get).baseUnit("seconds").register(registry);
        Gauge.builder("app.database.lock.waiters",waiters,AtomicInteger::get).register(registry);
        Gauge.builder("app.database.lock.oldest",oldest,AtomicReference::get).baseUnit("seconds").register(registry);
        Gauge.builder("app.observability.probe.age",probeAt,value->age(value.get())).baseUnit("seconds").register(registry);
        registry.counter("app.observability.probe.failures");registry.counter("app.observability.probe.successes");
    }
    @PostConstruct void start(){if(enabled){
        sampler=Executors.newSingleThreadScheduledExecutor(task->{var thread=new Thread(task,"operations-monitor");thread.setDaemon(true);return thread;});
        sampler.scheduleWithFixedDelay(()->{try{evaluate();}catch(RuntimeException failure){SafeDiagnostics.failure(log,"operations.monitor.failed",failure);}},60,60,TimeUnit.SECONDS);
    }}
    private long age(Instant at){Instant now=clock.instant();return at==null||now==null?0:Math.max(0,Duration.between(at,now).toSeconds());}
    private double gauges(String name){return registry.find(name).gauges().stream().mapToDouble(g->Double.isFinite(g.value())?g.value():0).sum();}
    private double counter(String name,String... tags){return registry.find(name).tags(tags).counters().stream().mapToDouble(Counter::count).sum();}
    private double failedMail(){return counter("app.mail.events","outcome","rejected")+counter("app.mail.events","outcome","uncaught")
            +registry.find("app.operation.duration").tags("operation","MAIL","outcome","failure").timers().stream().mapToDouble(Timer::count).sum();}
    private boolean staleJobs(){
        for(var entry:Map.of("ASSIGNMENT_EXPIRY",45,"CHALLENGE_EXPIRY",10800,"CREDENTIAL_CLEANUP",900,"STREAK_RESET",172800).entrySet()){
            var gauge=registry.find("app.operation.success.age").tag("operation",entry.getKey()).gauge();
            if(gauge!=null && gauge.value()>entry.getValue())return true;
        }
        return false;
    }
    public synchronized void evaluate(){
        double poolWait=gauges("hikaricp.connections.pending");
        if(poolWait==0)try{
            var value=probe.sample();waiters.set(value.waiters());oldest.set(value.oldestSeconds());activeQueries.set(value.activeQueries());oldestQuery.set(value.oldestQuerySeconds());probeAt.set(clock.instant());
            registry.counter("app.observability.probe.successes").increment();
        }catch(RuntimeException failure){registry.counter("app.observability.probe.failures").increment();SafeDiagnostics.failure(log,"operations.database_probe.failed",failure);}
        double all=counter("app.http.completed"),errors=counter("app.http.completed","outcome","server_error"),mail=failedMail();
        double requests=Math.max(0,all-previousRequests),failures=Math.max(0,errors-previousErrors);
        set(Signal.DATABASE_WAIT,poolWait>0 || waiters.get()>0);
        set(Signal.SLOW_QUERY,oldestQuery.get()>5);
        set(Signal.MAIL_QUEUE,gauges("app.mail.queue")>=80);
        set(Signal.MAIL_FAILURE,mail>previousMailFailures);
        set(Signal.HTTP_5XX,requests>=100 && failures/requests>=0.01);
        set(Signal.JOB_STALE,staleJobs());
        var sends=registry.find("app.operation.active").tag("operation","SSE_SEND").longTaskTimer();
        set(Signal.SSE_STALL,sends!=null && sends.max(TimeUnit.SECONDS)>5);
        set(Signal.PROBE_STALE,age(probeAt.get())>180);
        previousRequests=all;previousErrors=errors;previousMailFailures=mail;
    }
    private void set(Signal signal,boolean active){int next=active?1:0;
        if(signals.get(signal).getAndSet(next)!=next){
            if(active)log.warn("Operations signal active: {}",signal.name());
            else log.info("Operations signal cleared: {}",signal.name());
        }
    }
    @PreDestroy void stop(){if(sampler!=null)sampler.shutdownNow();}
}
