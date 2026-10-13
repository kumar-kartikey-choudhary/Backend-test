package com.pratikdairy.user.mail;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Used when no mail server is configured (spring.mail.host unset), so the flows can still be tried
 * locally: links are written to the log instead of being emailed. A reset or verification link is a
 * credential, so point spring.mail.* at a real server before running anywhere that matters.
 */
@Slf4j
public class LoggingEmailSender implements EmailSender {

    public LoggingEmailSender() {
        log.warn("No mail server configured (spring.mail.host) - emails (reset and verification links, contact "
                + "messages) will be written to this log instead of being sent. Configure spring.mail.* before deploying.");
    }

    @Override
    public void sendPasswordReset(String toEmail, String firstName, String resetLink, Duration validFor) {
        log.warn("PASSWORD RESET LINK for {} (valid {} min): {}", toEmail, validFor.toMinutes(), resetLink);
    }

    @Override
    public void sendPasswordChanged(String toEmail, String firstName) {
        log.warn("EMAIL to {}: your password was changed", toEmail);
    }

    @Override
    public void sendEmailVerification(String toEmail, String firstName, String verifyLink, Duration validFor) {
        log.warn("EMAIL VERIFICATION LINK for {} (valid {} h): {}", toEmail, validFor.toHours(), verifyLink);
    }

    @Override
    public void sendContactMessage(String shopAddress, String fromName, String fromEmail, String phone, String message) {
        log.warn("CONTACT MESSAGE for {} from {} <{}>: {}", shopAddress, fromName, fromEmail, message);
    }
}
