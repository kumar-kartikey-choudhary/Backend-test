package com.pratikdairy.user.mail;

import java.time.Duration;

/** Sends the emails the user service needs. Implementations must never throw into the caller. */
public interface EmailSender {

    void sendPasswordReset(String toEmail, String firstName, String resetLink, Duration validFor);

    /** "Your password was changed" - so the owner notices if it wasn't them. */
    void sendPasswordChanged(String toEmail, String firstName);

    void sendEmailVerification(String toEmail, String firstName, String verifyLink, Duration validFor);

    /** Passes a contact-form message to the shop; replying to it should reach the person who wrote it. */
    void sendContactMessage(String shopAddress, String fromName, String fromEmail, String phone, String message);
}
