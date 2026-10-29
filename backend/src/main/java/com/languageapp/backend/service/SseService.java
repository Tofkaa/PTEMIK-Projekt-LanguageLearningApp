package com.languageapp.backend.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SseService {
    private com.languageapp.backend.observability.OperationsMetrics operations;
    @org.springframework.beans.factory.annotation.Autowired
    public void observe(com.languageapp.backend.observability.OperationsMetrics metrics){
        this.operations=metrics;metrics.gauge("app.sse.connections",this,value->value.emitters.values().stream().mapToInt(List::size).sum());
    }



    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

    public SseService() {

        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeatToAll, 25, 25, TimeUnit.SECONDS);
    }

    public SseEmitter subscribe(String email) { return subscribe(email,300000); }
    public SseEmitter subscribe(String email,long lifetimeMillis) {
        SseEmitter emitter = new SseEmitter(Math.max(1,Math.min(300000,lifetimeMillis)));

        emitters.compute(email,(key,current)->{
            var list=current==null?new CopyOnWriteArrayList<SseEmitter>():current;list.add(emitter);return list;
        });

        Runnable removeCallback = () -> removeEmitter(email, emitter);
        emitter.onCompletion(removeCallback);
        emitter.onTimeout(() -> {
            emitter.complete();
            removeCallback.run();
        });
        emitter.onError((e) -> removeCallback.run());


        try {
            sendObserved(emitter,SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            removeEmitter(email, emitter);
        }

        return emitter;
    }

    /** Clients must never refresh a snapshot before its transaction is committed. */
    public void sendPingAfterCommit(String targetEmail) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { sendPing(targetEmail); }
                    });
        } else sendPing(targetEmail);
    }

    public void sendPing(String targetEmail) {
        List<SseEmitter> userEmitters = emitters.get(targetEmail);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : userEmitters) {
            try {
                sendObserved(emitter,SseEmitter.event().name("ping").data("update"));
            } catch (Exception e) {
                removeEmitter(targetEmail, emitter);
            }
        }
    }

    private void sendHeartbeatToAll() {
        emitters.forEach((email, userEmitters) -> {
            for (SseEmitter emitter : userEmitters) {
                try {
                    sendObserved(emitter,SseEmitter.event().comment("heartbeat"));
                } catch (Exception e) {
                    removeEmitter(email, emitter);
                }
            }
        });
    }

    private void sendObserved(SseEmitter emitter,SseEmitter.SseEventBuilder event)throws IOException {
        var work=operations==null?null:operations.begin(com.languageapp.backend.observability.OperationsMetrics.Operation.SSE_SEND);
        boolean successful=false;
        try{emitter.send(event);successful=true;}
        finally{if(work!=null)operations.finish(work,successful);}
    }

    private void removeEmitter(String email, SseEmitter emitter) {
        emitters.computeIfPresent(email,(key,current)->{current.remove(emitter);return current.isEmpty()?null:current;});
    }

    @PreDestroy
    public void shutdown() {
        heartbeatScheduler.shutdownNow();
    }
}