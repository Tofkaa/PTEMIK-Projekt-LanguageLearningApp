package com.languageapp.backend.service;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Part;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import java.util.Properties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@test.com");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:3000");

        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
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

    @Test
    void perSecondThrottleIsRetriedWithTheSameMessage() {
        ReflectionTestUtils.setField(emailService, "rateLimitRetryDelayMillis", 0L);
        doThrow(new MailSendException("550 5.7.0 Too many emails per second"))
                .doNothing().when(mailSender).send(mimeMessage);
        emailService.sendEmailChangeOtp("student@example.test", "123456");
        verify(mailSender, times(2)).send(mimeMessage);
        verify(mailSender).createMimeMessage();
    }

    @Test
    void permanentRejectionIsNotRetried() {
        doThrow(new MailSendException("550 Recipient rejected")).when(mailSender).send(mimeMessage);
        assertThrows(MailSendException.class, () -> emailService.sendPasswordResetEmail("student@example.test", "test-token"));
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void persistentPerSecondThrottleStopsAfterThreeAttempts() {
        ReflectionTestUtils.setField(emailService, "rateLimitRetryDelayMillis", 0L);
        doThrow(new MailSendException("550 5.7.0 Too many emails per second")).when(mailSender).send(mimeMessage);
        assertThrows(MailSendException.class, () -> emailService.sendEmailChangeOtp("student@example.test", "123456"));
        verify(mailSender, times(3)).send(mimeMessage);
    }

    @Test
    void defaultRetryDelayExceedsMailtrapTenSecondWindow() {
        assertTrue((long) ReflectionTestUtils.getField(emailService, "rateLimitRetryDelayMillis") > 10000);
        emailService.sendEmailChangeOtp("student@example.test", "123456");
        verify(mailSender).send(mimeMessage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"verification", "reset", "otp", "security"})
    void emailDocumentsHaveHungarianLanguageReadableContentAndFunctionalLinks(String type) throws Exception {
        switch (type) {
            case "verification" -> emailService.sendVerificationEmail("student@example.test", "Teszt Diák", "test-token");
            case "reset" -> emailService.sendPasswordResetEmail("student@example.test", "test-token");
            case "otp" -> emailService.sendEmailChangeOtp("student@example.test", "123456");
            case "security" -> emailService.sendEmailChangeSecurityAlert("student@example.test", "new@example.test");
            default -> throw new IllegalArgumentException(type);
        }
        mimeMessage.saveChanges();
        String html = htmlContent(mimeMessage);
        assertTrue(html.contains("<html lang=\"hu\">"));
        assertTrue(html.contains("charset=\"UTF-8\""));
        assertTrue(html.contains("<h1>"));
        assertTrue(html.contains("background-color:#FFFFFF;color:#212529"));
        assertFalse(html.contains("<img"));
        if (type.equals("verification")) {
            assertTrue(html.contains("http://localhost:3000/verify?token=test-token"));
            assertTrue(html.contains("Fiók Megerősítése</a>"));
            assertTrue(html.contains("background-color:#0056B3;color:#FFF"));
            assertTrue(contrast("#0056B3", "#FFFFFF") >= 4.5);
        } else if (type.equals("reset")) {
            assertTrue(html.contains("http://localhost:3000/reset-password?token=test-token"));
            assertTrue(contrast("#DC3545", "#FFFFFF") >= 4.5);
        } else if (type.equals("otp")) {
            assertTrue(html.contains("123456</div>"));
            assertTrue(html.contains("background-color:#17a2b8;color:#212529"));
            assertTrue(contrast("#17A2B8", "#212529") >= 4.5);
        } else {
            assertTrue(html.contains("new@example.test"));
        }
        verify(mailSender).send(mimeMessage);
    }

    private String htmlContent(Part part) throws Exception {
        if (part.isMimeType("text/html")) return (String) part.getContent();
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String html = htmlContent(multipart.getBodyPart(i));
                if (!html.isEmpty()) return html;
            }
        }
        return "";
    }

    private double luminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double result = 0;
        double[] weights = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) {
            double value = ((rgb >> (16 - i * 8)) & 255) / 255.0;
            result += weights[i] * (value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4));
        }
        return result;
    }

    private double contrast(String first, String second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }
}
