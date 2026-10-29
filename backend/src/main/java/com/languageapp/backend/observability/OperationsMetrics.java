package com.languageapp.backend.observability;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;

/** Fixed enums define the complete label vocabulary. No account, URL, request or exception values are tags. */
@Component
public class OperationsMetrics {
    public enum Operation { MAIL, SSE_SEND, ASSIGNMENT_EXPIRY, CHALLENGE_EXPIRY, CREDENTIAL_CLEANUP, STREAK_RESET }
    public enum Conflict { CONSTRAINT, OPTIMISTIC, LOCK }
    private final MeterRegistry registry;
    private final Clock clock;
    private final EnumMap<Operation,LongTaskTimer> running=new EnumMap<>(Operation.class);
    private final EnumMap<Operation,AtomicReference<Instant>> lastSuccess=new EnumMap<>(Operation.class);
    public OperationsMetrics(MeterRegistry registry,Clock clock) {
        this.registry=registry;this.clock=clock;
        for(var op:Operation.values()) {
            running.put(op,LongTaskTimer.builder("app.operation.active").tag("operation",op.name()).register(registry));
            lastSuccess.put(op,new AtomicReference<>(clock.instant()));
            for(String outcome:List.of("success","failure"))Timer.builder("app.operation.duration").tags("operation",op.name(),"outcome",outcome).register(registry);
            Gauge.builder("app.operation.success.age",lastSuccess.get(op),value->age(value.get()))
                    .tag("operation",op.name()).register(registry);
        }
        for(var conflict:Conflict.values())registry.counter("app.persistence.conflicts","kind",conflict.name());
        for(String outcome:List.of("success","client_error","server_error"))registry.counter("app.http.completed","outcome",outcome);
        for(String outcome:List.of("rejected","uncaught","retry"))registry.counter("app.mail.events","outcome",outcome);
        registry.counter("app.job.item.failures");registry.counter("app.job.scan.saturated");
    }
    private long age(Instant at){Instant now=clock.instant();return at==null||now==null?0:Math.max(0,java.time.Duration.between(at,now).toSeconds());}
    public record Work(Operation operation,Timer.Sample elapsed,LongTaskTimer.Sample active) { }
    public Work begin(Operation operation) {return new Work(operation,Timer.start(registry),running.get(operation).start());}
    public void finish(Work work,boolean successful) {
        work.active().stop();work.elapsed().stop(registry.timer("app.operation.duration","operation",work.operation().name(),"outcome",successful?"success":"failure"));
        if(successful)lastSuccess.get(work.operation()).set(clock.instant());
    }
    public void finishJob(Work work,boolean successful) {
        if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){finish(work,successful && status==STATUS_COMMITTED);}
        });else finish(work,successful);
    }
    public void conflict(Conflict kind){registry.counter("app.persistence.conflicts","kind",kind.name()).increment();}
    public void http(int status){registry.counter("app.http.completed","outcome",status>=500?"server_error":status>=400?"client_error":"success").increment();}
    public void mailRejected(){registry.counter("app.mail.events","outcome","rejected").increment();}
    public void mailUncaught(){registry.counter("app.mail.events","outcome","uncaught").increment();}
    public void mailRetry(){registry.counter("app.mail.events","outcome","retry").increment();}
    public void jobItemFailed(){registry.counter("app.job.item.failures").increment();}
    public void scanSaturated(){registry.counter("app.job.scan.saturated").increment();}
    public <T> void gauge(String name,T owner,ToDoubleFunction<T> value){Gauge.builder(name,owner,value).register(registry);}
}
