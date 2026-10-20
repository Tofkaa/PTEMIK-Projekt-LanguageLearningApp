package com.languageapp.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.MailSendException;
import java.util.Locale;
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

    @Value("${app.mail.rate-limit-retry-delay-ms:11000}")
    private long rateLimitRetryDelayMillis = 11000;

    @Async
    public void sendVerificationEmail(String toEmail, String name, String token) {
        String verificationUrl = frontendUrl + "/verify?token=" + token;

        String htmlBody = String.format(
                "<h1>Üdvözlünk a LanguageApp-ban, %s!</h1>" +
                        "<p>Kérjük, kattints az alábbi linkre a fiókod megerősítéséhez. A link 24 óráig érvényes.</p>" +
                        "<a href='%s' style='display:inline-block;padding:10px 20px;background-color:#0056B3;color:#FFF;text-decoration:underline;border-radius:5px;'>Fiók Megerősítése</a>" +
                        "<p>Ha nem te regisztráltál, hagyd figyelmen kívül ezt az e-mailt.</p>",
                name, verificationUrl
        );

        sendHtmlEmail(toEmail, "Erősítsd meg az e-mail címedet", htmlBody);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetUrl = frontendUrl + "/reset-password?token=" + token;

        String htmlBody = String.format(
                "<h1>Jelszó visszaállítása</h1>" +
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
            String document = "<!doctype html><html lang=\"hu\"><head><meta charset=\"UTF-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head>"
                    + "<body style=\"background-color:#FFFFFF;color:#212529;font-family:Arial,sans-serif;"
                    + "font-size:16px;line-height:1.5;padding:16px;overflow-wrap:anywhere;\">"
                    + htmlBody + "</body></html>";
            helper.setText(document, true);

            sendWithRateLimitRetry(message);
            log.info("Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}", to, e);
        }
    }

    /** Mailtrap's SMTP 550 throttle uses a ten-second window despite its per-second wording. */
    private void sendWithRateLimitRetry(MimeMessage message) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                mailSender.send(message);
                return;
            } catch (MailSendException ex) {
                String details = ex.getMessage();
                boolean throttled = details != null
                        && details.toLowerCase(Locale.ROOT).contains("too many emails per second");
                if (!throttled || attempt == 3) throw ex;
                log.warn("SMTP rate limit; retrying email delivery in {} ms ({}/3).",
                        rateLimitRetryDelayMillis, attempt + 1);
                try {
                    Thread.sleep(rateLimitRetryDelayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new MailSendException("Email delivery retry interrupted.", interrupted);
                }
            }
        }
    }
    @Async
    public void sendEmailChangeOtp(String toNewEmail, String otpCode) {
        log.info("Sending email change OTP to new address: {}", toNewEmail);

        String htmlBody = String.format(
                "<h1>E-mail cím módosításának megerősítése</h1>" +
                        "<p>Kaptunk egy kérést, hogy a LanguageApp fiókod e-mail címét erre a címre módosítsuk.</p>" +
                        "<p>A módosítás véglegesítéséhez írd be az alábbi 6-jegyű ellenőrző kódot a Beállítások oldalon (a kód 15 percig érvényes):</p>" +
                        "<div style='display:inline-block;padding:12px 24px;background-color:#17a2b8;color:#212529;font-size:24px;font-weight:bold;letter-spacing:4px;border-radius:5px;margin:10px 0;'>%s</div>" +
                        "<p>Ha nem te kezdeményezted a módosítást, hagyd figyelmen kívül ezt az üzenetet.</p>",
                otpCode
        );

        sendHtmlEmail(toNewEmail, "E-mail cím módosítás megerősítése - LanguageApp", htmlBody);
    }

    @Async
    public void sendEmailChangeSecurityAlert(String toOldEmail, String newEmail) {
        log.info("Sending email change security alert to old address: {}", toOldEmail);

        String htmlBody = String.format(
                "<h1>Biztonsági értesítés: E-mail cím módosítás</h1>" +
                        "<p>Értesítünk, hogy a LanguageApp fiókodhoz e-mail cím módosítást kezdeményeztek a következő új címre: <strong>%s</strong></p>" +
                        "<p>A folyamat befejezéséhez az új e-mail címre küldött 6-jegyű kód megadása szükséges.</p>" +
                        "<p style='color:#DC3545;font-weight:bold;'>Ha NEM te kezdeményezted ezt a módosítást, valaki hozzáférhetett a jelszavadhoz! Kérjük, azonnal lépj be és változtasd meg a jelszavadat a Beállítások menüben.</p>",
                newEmail
        );

        sendHtmlEmail(toOldEmail, "Biztonsági értesítés: E-mail módosítási kérelem - LanguageApp", htmlBody);
    }
}
