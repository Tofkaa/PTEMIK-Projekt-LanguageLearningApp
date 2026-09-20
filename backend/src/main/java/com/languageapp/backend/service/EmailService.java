package com.languageapp.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@languageapp.com}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Async
    public void sendVerificationEmail(String toEmail, String name, String token) {
        String verificationUrl = frontendUrl + "/verify?token=" + token;

        String htmlBody = String.format(
                "<h2>Üdvözlünk a LanguageApp-ban, %s!</h2>" +
                        "<p>Kérjük, kattints az alábbi linkre a fiókod megerősítéséhez. A link 24 óráig érvényes.</p>" +
                        "<a href='%s' style='display:inline-block;padding:10px 20px;background-color:#007BFF;color:#FFF;text-decoration:none;border-radius:5px;'>Fiók Megerősítése</a>" +
                        "<p>Ha nem te regisztráltál, hagyd figyelmen kívül ezt az e-mailt.</p>",
                name, verificationUrl
        );

        sendHtmlEmail(toEmail, "Erősítsd meg az e-mail címedet", htmlBody);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetUrl = frontendUrl + "/reset-password?token=" + token;

        String htmlBody = String.format(
                "<h2>Jelszó visszaállítása</h2>" +
                        "<p>Kaptunk egy kérést a jelszavad visszaállítására. Kattints a linkre az új jelszó beállításához. A link 1 óráig érvényes.</p>" +
                        "<a href='%s' style='display:inline-block;padding:10px 20px;background-color:#DC3545;color:#FFF;text-decoration:none;border-radius:5px;'>Jelszó Visszaállítása</a>" +
                        "<p>Ha nem te kérted a visszaállítást, a jelszavad biztonságban van, és ezt az e-mailt ignorálhatod.</p>",
                resetUrl
        );

        sendHtmlEmail(toEmail, "Jelszó visszaállítás - LanguageApp", htmlBody);
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML formátum

            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}", to, e);
        }
    }
}