package com.pratikdairy.user.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;

import java.time.Duration;

/** Real delivery over SMTP (configured with spring.mail.*). Runs off the request thread. */
@Slf4j
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    // Async so a slow mail server doesn't make "forgot password" noticeably slower for real accounts
    // than for unknown addresses (which would reveal which emails are registered).
    @Async
    @Override
    public void sendPasswordReset(String toEmail, String firstName, String resetLink, Duration validFor) {
        deliver(toEmail, null, EmailTemplates.passwordReset(firstName, resetLink, validFor));
    }

    @Async
    @Override
    public void sendPasswordChanged(String toEmail, String firstName) {
        deliver(toEmail, null, EmailTemplates.passwordChanged(firstName));
    }

    @Async
    @Override
    public void sendEmailVerification(String toEmail, String firstName, String verifyLink, Duration validFor) {
        deliver(toEmail, null, EmailTemplates.emailVerification(firstName, verifyLink, validFor));
    }

    @Async
    @Override
    public void sendContactMessage(String shopAddress, String fromName, String fromEmail, String phone, String message) {
        deliver(shopAddress, fromEmail, EmailTemplates.contactMessage(fromName, fromEmail, phone, message));
    }

    private void deliver(String to, String replyTo, EmailTemplates.Email email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");   // multipart: text + HTML
            helper.setFrom(from);
            helper.setTo(to);
            if (replyTo != null) {
                helper.setReplyTo(replyTo);
            }
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            mailSender.send(message);
            log.info("Email sent: {}", email.subject());
        } catch (Exception e) {
            // Not logged with the message: the failure reason is enough, and some emails carry credentials (links).
            log.error("Could not send the email '{}': {}", email.subject(), e.getMessage());
        }
    }
}
