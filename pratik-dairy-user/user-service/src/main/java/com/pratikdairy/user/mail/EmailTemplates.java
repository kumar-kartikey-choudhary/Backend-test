package com.pratikdairy.user.mail;

import org.springframework.web.util.HtmlUtils;

import java.time.Duration;

/**
 * The wording and look of every email, as a plain-text version and a branded HTML version (sent together,
 * so mail clients that can't show HTML still read something sensible).
 *
 * Anything a person typed (names, their message) is HTML-escaped before it goes into the markup; links are
 * built by this service, never taken from a request.
 */
final class EmailTemplates {

    record Email(String subject, String text, String html) {}

    private static final String SHOP = "Pratik Dairy & Sweets";
    private static final String NAVY = "#0F172A";
    private static final String GOLD = "#F5A623";

    private EmailTemplates() {}

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private static String greeting(String firstName) {
        return (firstName == null || firstName.isBlank()) ? "there" : firstName.trim();
    }

    private static String describe(Duration d) {
        long hours = d.toHours();
        if (hours >= 1 && d.toMinutes() % 60 == 0) {
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return d.toMinutes() + " minutes";
    }

    /** The shared frame: a navy header with the shop name, a white card, a quiet footer. */
    private static String layout(String heading, String bodyHtml) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width\"></head>"
                + "<body style=\"margin:0;padding:0;background:#f4f1ea;font-family:Segoe UI,Arial,sans-serif;color:#1f2937;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f1ea;padding:24px 12px;\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;width:100%;background:#ffffff;border-radius:12px;overflow:hidden;\">"
                + "<tr><td style=\"background:" + NAVY + ";padding:22px 28px;color:#ffffff;font-size:20px;font-weight:700;\">"
                + "<span style=\"color:" + GOLD + ";\">&#9679;</span> " + esc(SHOP) + "</td></tr>"
                + "<tr><td style=\"padding:28px;\"><h1 style=\"margin:0 0 14px;font-size:20px;color:" + NAVY + ";\">" + esc(heading) + "</h1>"
                + bodyHtml + "</td></tr>"
                + "<tr><td style=\"padding:16px 28px;background:#faf8f3;color:#6b7280;font-size:12px;\">"
                + "You are receiving this because of activity on your " + esc(SHOP) + " account.</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private static String button(String link, String label) {
        return "<p style=\"margin:24px 0;\"><a href=\"" + esc(link) + "\" style=\"background:" + GOLD + ";color:" + NAVY
                + ";text-decoration:none;font-weight:700;padding:12px 24px;border-radius:8px;display:inline-block;\">"
                + esc(label) + "</a></p>"
                + "<p style=\"font-size:12px;color:#6b7280;word-break:break-all;\">If the button doesn't work, copy this address into your browser:<br>"
                + esc(link) + "</p>";
    }

    private static String p(String text) {
        return "<p style=\"margin:0 0 12px;line-height:1.5;\">" + text + "</p>";
    }

    static Email passwordReset(String firstName, String link, Duration validFor) {
        String name = greeting(firstName);
        String text = "Hi " + name + ",\n\n"
                + "We received a request to reset the password for your " + SHOP + " account.\n\n"
                + "Choose a new password here (the link works once and expires in " + describe(validFor) + "):\n"
                + link + "\n\n"
                + "If you didn't ask for this, you can ignore this email - your password won't change.\n\n"
                + SHOP;
        String html = layout("Reset your password",
                p("Hi " + esc(name) + ",")
                        + p("We received a request to reset the password for your " + esc(SHOP) + " account.")
                        + button(link, "Choose a new password")
                        + p("The link works once and expires in " + esc(describe(validFor)) + ".")
                        + p("If you didn't ask for this, you can ignore this email &mdash; your password won't change."));
        return new Email("Reset your Pratik Dairy password", text, html);
    }

    static Email passwordChanged(String firstName) {
        String name = greeting(firstName);
        String text = "Hi " + name + ",\n\n"
                + "The password for your " + SHOP + " account was just changed, and any other devices were signed out.\n\n"
                + "If that was you, there's nothing more to do.\n\n"
                + "If it wasn't you, please reset your password straight away using \"Forgot password\" on the login page "
                + "and contact us.\n\n" + SHOP;
        String html = layout("Your password was changed",
                p("Hi " + esc(name) + ",")
                        + p("The password for your " + esc(SHOP) + " account was just changed, and any other devices were signed out.")
                        + p("If that was you, there's nothing more to do.")
                        + p("<strong>If it wasn't you</strong>, reset your password straight away using &ldquo;Forgot password&rdquo; on the login page, and contact us."));
        return new Email("Your Pratik Dairy password was changed", text, html);
    }

    static Email emailVerification(String firstName, String link, Duration validFor) {
        String name = greeting(firstName);
        String text = "Hi " + name + ",\n\n"
                + "Please confirm this email address for your " + SHOP + " account:\n"
                + link + "\n\n"
                + "The link expires in " + describe(validFor) + ". If you didn't create an account, ignore this email.\n\n"
                + SHOP;
        String html = layout("Confirm your email address",
                p("Hi " + esc(name) + ",")
                        + p("Please confirm this email address for your " + esc(SHOP) + " account.")
                        + button(link, "Confirm my email")
                        + p("The link expires in " + esc(describe(validFor)) + ". If you didn't create an account, you can ignore this email."));
        return new Email("Confirm your email address - Pratik Dairy", text, html);
    }

    static Email contactMessage(String fromName, String fromEmail, String phone, String message) {
        String phoneLine = (phone == null || phone.isBlank()) ? "" : phone.trim();
        String text = "New message from the website contact form\n\n"
                + "From: " + fromName + " <" + fromEmail + ">\n"
                + (phoneLine.isEmpty() ? "" : "Phone: " + phoneLine + "\n")
                + "\n" + message + "\n";
        String html = layout("New message from the website",
                p("<strong>" + esc(fromName) + "</strong> &lt;" + esc(fromEmail) + "&gt;"
                        + (phoneLine.isEmpty() ? "" : "<br>Phone: " + esc(phoneLine)))
                        + "<div style=\"background:#faf8f3;border-left:4px solid " + GOLD + ";padding:12px 16px;border-radius:4px;white-space:pre-wrap;line-height:1.5;\">"
                        + esc(message) + "</div>"
                        + p("<span style=\"color:#6b7280;font-size:12px;\">Reply to this email to answer them directly.</span>"));
        return new Email("Website message from " + fromName.replaceAll("[\\r\\n]", " "), text, html);
    }
}
