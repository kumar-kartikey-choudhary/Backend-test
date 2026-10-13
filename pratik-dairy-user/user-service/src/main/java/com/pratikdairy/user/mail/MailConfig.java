package com.pratikdairy.user.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class MailConfig {

    /** Real email, chosen when spring.mail.host is set. */
    @Bean
    @ConditionalOnProperty(prefix = "spring.mail", name = "host")
    public EmailSender smtpEmailSender(JavaMailSender mailSender,
                                       @Value("${app.mail.from:no-reply@pratikdairy.com}") String from) {
        return new SmtpEmailSender(mailSender, from);
    }

    /** Otherwise: log the link (local development). */
    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }
}
