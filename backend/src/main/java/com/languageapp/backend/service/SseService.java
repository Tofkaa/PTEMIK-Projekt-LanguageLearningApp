package com.languageapp.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for managing Server-Sent Events (SSE) connections.
 * It maintains active client connections and dispatches real-time ping events
 * to specific users when their notification state changes.
 */
@Service
public class SseService {

    /**
     * Thread-safe map to store active SSE emitters.
     * Key: User's email address.
     * Value: The active SseEmitter object for that user.
     */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Subscribes a user to the SSE stream.
     * * @param email The email address of the authenticated user.
     * @return SseEmitter instance keeping the connection open.
     */
    public SseEmitter subscribe(String email) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // Infinite timeout
        emitters.put(email, emitter);

        // Remove emitter from the map when the connection is closed or fails
        emitter.onCompletion(() -> emitters.remove(email));
        emitter.onTimeout(() -> emitters.remove(email));
        emitter.onError((e) -> emitters.remove(email));

        return emitter;
    }

    /**
     * Sends a real-time update signal (ping) to a specific user.
     * This prompts the client application to fetch the latest notification data.
     * * @param targetEmail The email address of the user who should receive the ping.
     */
    public void sendPing(String targetEmail) {
        SseEmitter emitter = emitters.get(targetEmail);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("ping").data("update"));
            } catch (IOException e) {
                // If sending fails (e.g., client disconnected abruptly), clean up the dead emitter
                emitters.remove(targetEmail);
            }
        }
    }
}