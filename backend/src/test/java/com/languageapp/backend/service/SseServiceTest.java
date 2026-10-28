package com.languageapp.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

class SseServiceTest {

    private SseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new SseService();
    }

    @org.junit.jupiter.api.AfterEach void stop(){sseService.shutdown();}

    @Test
    void subscribe_ShouldReturnValidSseEmitter() {
        // Act
        SseEmitter emitter = sseService.subscribe("diak@test.com");

        // Assert
        assertNotNull(emitter, "A feliratkozásnak egy élő SseEmitter objektumot kell visszaadnia");
        assertEquals(300000L, emitter.getTimeout(), "A kapcsolat élettartama legyen korlátos");
    }

    @Test
    void sendPing_ToUnsubscribedUser_ShouldNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            sseService.sendPing("nemletezo@test.com");
        }, "Ha olyan felhasználónak pingelünk, aki nincs bejelentkezve, némán le kell kezelnie.");
    }
}