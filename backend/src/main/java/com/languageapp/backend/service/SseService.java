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


    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

    public SseService() {

        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeatToAll, 25, 25, TimeUnit.SECONDS);
    }

    public SseEmitter subscribe(String email) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        List<SseEmitter> userEmitters = emitters.computeIfAbsent(email, k -> new CopyOnWriteArrayList<>());
        userEmitters.add(emitter);

        Runnable removeCallback = () -> removeEmitter(email, emitter);
        emitter.onCompletion(removeCallback);
        emitter.onTimeout(() -> {
            emitter.complete();
            removeCallback.run();
        });
        emitter.onError((e) -> removeCallback.run());


        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
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
                emitter.send(SseEmitter.event().name("ping").data("update"));
            } catch (Exception e) {
                removeEmitter(targetEmail, emitter);
            }
        }
    }

    private void sendHeartbeatToAll() {
        emitters.forEach((email, userEmitters) -> {
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (Exception e) {
                    removeEmitter(email, emitter);
                }
            }
        });
    }

    private void removeEmitter(String email, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(email);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(email, userEmitters);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        heartbeatScheduler.shutdownNow();
    }
}