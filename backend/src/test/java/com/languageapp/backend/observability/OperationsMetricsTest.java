package com.languageapp.backend.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationsMetricsTest {
    private final java.util.List<SimpleMeterRegistry> registries=new java.util.ArrayList<>();
    private SimpleMeterRegistry register(SimpleMeterRegistry registry){registries.add(registry);return registry;}
    @org.junit.jupiter.api.AfterEach void close(){registries.forEach(SimpleMeterRegistry::close);}

    @Test void failedAndSuccessfulJobsTrackActiveWorkAndTransactionCompletion() {
        {var registry=register(new SimpleMeterRegistry());
            var metrics=new OperationsMetrics(registry,Clock.systemUTC());
            var first=metrics.begin(OperationsMetrics.Operation.ASSIGNMENT_EXPIRY);
            assertEquals(1,registry.get("app.operation.active").tag("operation","ASSIGNMENT_EXPIRY").longTaskTimer().activeTasks());
            TransactionSynchronizationManager.initSynchronization();
            try{metrics.finishJob(first,true);
                assertEquals(1,registry.get("app.operation.active").tag("operation","ASSIGNMENT_EXPIRY").longTaskTimer().activeTasks());
                for(var synchronization:TransactionSynchronizationManager.getSynchronizations())synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }finally{TransactionSynchronizationManager.clearSynchronization();}
            assertEquals(0,registry.get("app.operation.active").tag("operation","ASSIGNMENT_EXPIRY").longTaskTimer().activeTasks());
            assertEquals(1,registry.get("app.operation.duration").tags("operation","ASSIGNMENT_EXPIRY","outcome","failure").timer().count());
            metrics.finishJob(metrics.begin(OperationsMetrics.Operation.ASSIGNMENT_EXPIRY),true);
            assertEquals(1,registry.get("app.operation.duration").tags("operation","ASSIGNMENT_EXPIRY","outcome","success").timer().count());
        }
    }
    @Test void pressureSignalsRiseAndClearWithoutSensitiveOrUnboundedTags() {
        {var registry=register(new SimpleMeterRegistry());
            Clock clock=Clock.systemUTC();var metrics=new OperationsMetrics(registry,clock);var probe=mock(DatabaseWaitProbe.class);
            when(probe.sample()).thenReturn(new DatabaseWaitProbe.Snapshot(2,4,3,6));
            var monitor=new OperationsMonitor(registry,clock,probe);
            for(int n=0;n<100;n++)metrics.http(n==0?500:200);
            monitor.evaluate();
            assertEquals(1,registry.get("app.operations.signal").tag("signal","DATABASE_WAIT").gauge().value());
            assertEquals(1,registry.get("app.operations.signal").tag("signal","HTTP_5XX").gauge().value());
            assertEquals(1,registry.get("app.operations.signal").tag("signal","SLOW_QUERY").gauge().value());
            when(probe.sample()).thenReturn(new DatabaseWaitProbe.Snapshot(0,0));monitor.evaluate();
            assertEquals(0,registry.get("app.operations.signal").tag("signal","DATABASE_WAIT").gauge().value());
            assertEquals(0,registry.get("app.operations.signal").tag("signal","HTTP_5XX").gauge().value());
            assertTrue(registry.getMeters().stream().flatMap(m->m.getId().getTags().stream()).allMatch(tag->java.util.Set.of("operation","outcome","signal","kind").contains(tag.getKey())));
        }
    }
    @Test void databaseSamplingDoesNotCompeteForAPoolThatAlreadyHasWaiters() {
        {var registry=register(new SimpleMeterRegistry());
            var probe=mock(DatabaseWaitProbe.class);var pending=new java.util.concurrent.atomic.AtomicInteger(1);
            registry.gauge("hikaricp.connections.pending",pending);
            var monitor=new OperationsMonitor(registry,Clock.systemUTC(),probe);monitor.evaluate();verifyNoInteractions(probe);
            assertEquals(1,registry.get("app.operations.signal").tag("signal","DATABASE_WAIT").gauge().value());
        }
    }
    @Test void mailFailuresAreCountedWithoutRecipientLabels() {
        {var registry=register(new SimpleMeterRegistry());
            var metrics=new OperationsMetrics(registry,Clock.systemUTC());var probe=mock(DatabaseWaitProbe.class);when(probe.sample()).thenReturn(new DatabaseWaitProbe.Snapshot(0,0));
            var monitor=new OperationsMonitor(registry,Clock.systemUTC(),probe);metrics.mailRejected();metrics.mailUncaught();metrics.mailRetry();monitor.evaluate();
            assertEquals(1,registry.get("app.operations.signal").tag("signal","MAIL_FAILURE").gauge().value());
            monitor.evaluate();assertEquals(0,registry.get("app.operations.signal").tag("signal","MAIL_FAILURE").gauge().value());
        }
    }
    @Test void staleProbeJobsAndSlowSendsRemainVisibleOnADedicatedSampler() {
        var meterClock=new MockClock();var instant=new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-10-29T12:00:00Z"));
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return instant.get();}};
        {var registry=register(new SimpleMeterRegistry(io.micrometer.core.instrument.simple.SimpleConfig.DEFAULT,meterClock));
            var metrics=new OperationsMetrics(registry,clock);var probe=mock(DatabaseWaitProbe.class);when(probe.sample()).thenThrow(new IllegalStateException("PRIVATE_DATABASE_ERROR"));
            var monitor=new OperationsMonitor(registry,clock,probe);var send=metrics.begin(OperationsMetrics.Operation.SSE_SEND);
            meterClock.add(Duration.ofSeconds(6));instant.set(instant.get().plusSeconds(181));monitor.evaluate();
            for(String signal:java.util.List.of("SSE_STALL","JOB_STALE","PROBE_STALE"))assertEquals(1,registry.get("app.operations.signal").tag("signal",signal).gauge().value());
            metrics.finish(send,false);
        }
    }
    @Test void httpObservationsExcludeUrlValuesAndCapUnknownMethodsAndRoutes() {
        {var registry=register(new SimpleMeterRegistry());
            var config=new ObservabilityConfiguration(new OperationsMetrics(registry,Clock.systemUTC()));
            var request=new org.springframework.mock.web.MockHttpServletRequest("PRIVATE_METHOD","/private@example.test");
            var context=new org.springframework.http.server.observation.ServerRequestObservationContext(request,new org.springframework.mock.web.MockHttpServletResponse());
            var convention=config.safeHttpObservationConvention();
            assertTrue(convention.getHighCardinalityKeyValues(context).stream().findAny().isEmpty());
            assertEquals("http other",convention.getContextualName(context));
            assertTrue(convention.getLowCardinalityKeyValues(context).stream().anyMatch(value->value.getKey().equals("method") && value.getValue().equals("OTHER")));
            assertFalse(convention.getLowCardinalityKeyValues(context).toString().contains("private@example.test"));
        }
    }

    @Test void uriSeriesAreActuallyDeniedBeyondTheConfiguredLimit() {
        var registry=register(new SimpleMeterRegistry());registry.config().meterFilter(ObservabilityConfiguration.httpUriCardinalityLimit());
        for(int n=0;n<170;n++)io.micrometer.core.instrument.Timer.builder("http.server.requests").tag("uri","/pattern/"+n).register(registry).record(Duration.ofMillis(1));
        assertEquals(150,registry.find("http.server.requests").timers().size());
    }
    @Test void saturatedMailQueueRejectsAtItsBoundAndRecordsTheRejection()throws Exception {
        var registry=register(new SimpleMeterRegistry());var metrics=new OperationsMetrics(registry,Clock.systemUTC());
        var config=new ObservabilityConfiguration(metrics);var executor=config.mailExecutor();executor.initialize();
        var release=new java.util.concurrent.CountDownLatch(1);
        Runnable held=()->{try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}};
        try{
            for(int n=0;n<104;n++)executor.execute(held);
            assertThrows(org.springframework.core.task.TaskRejectedException.class,()->executor.execute(held));
            assertTrue(executor.getPoolSize()<=4);assertTrue(executor.getThreadPoolExecutor().getQueue().size()<=100);
            assertEquals(1,registry.counter("app.mail.events","outcome","rejected").count());
        }finally{release.countDown();executor.shutdown();}
    }
}
