package com.languageapp.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {
    // E-mail cím -> Emitter párosítás
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String email) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // Végtelen timeout
        emitters.put(email, emitter);

        emitter.onCompletion(() -> emitters.remove(email));
        emitter.onTimeout(() -> emitters.remove(email));
        emitter.onError((e) -> emitters.remove(email));

        return emitter;
    }

    // Ezt hívjuk meg, ha valami történik (pl. valakit kihívnak)
    public void sendPing(String targetEmail) {
        SseEmitter emitter = emitters.get(targetEmail);
        if (emitter != null) {
            try {
                // Csak egy üres "ping" eseményt küldünk, ami ráveszi a Reactot a frissítésre
                emitter.send(SseEmitter.event().name("ping").data("update"));
            } catch (IOException e) {
                emitters.remove(targetEmail);
            }
        }
    }
}