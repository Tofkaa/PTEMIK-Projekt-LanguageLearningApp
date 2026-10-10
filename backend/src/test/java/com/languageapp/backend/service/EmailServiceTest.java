package com.languageapp.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@test.com");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:3000");

        // Mindig adjon vissza egy mockolt MimeMessage-et, amikor a sender-t hívjuk
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void sendVerificationEmail_ShouldCreateAndSendHtmlEmail() {
        // Act
        emailService.sendVerificationEmail("diak@test.com", "Teszt Diák", "12345-token");

        // Assert
        verify(mailSender, times(1)).createMimeMessage();
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendEmailChangeOtp_ShouldFormatOtpAndSend() {
        // Act
        emailService.sendEmailChangeOtp("ujcím@test.com", "987654");

        // Assert
        verify(mailSender, times(1)).createMimeMessage();
        verify(mailSender, times(1)).send(mimeMessage);
    }
}