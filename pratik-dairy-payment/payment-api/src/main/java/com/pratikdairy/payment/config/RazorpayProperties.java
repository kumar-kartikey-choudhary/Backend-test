package com.pratikdairy.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

// Bound from application.properties: razorpay.key-id / razorpay.key-secret / razorpay.webhook-secret.
// keyId is Razorpay's PUBLIC key (safe to hand to the frontend for Checkout.js).
// keySecret and webhookSecret are private - used only server-side to sign/verify requests and
// must never be logged or returned in any API response.
@Configuration
@ConfigurationProperties(prefix = "razorpay")
@Data
public class RazorpayProperties {
    private String keyId;
    private String keySecret;
    private String webhookSecret;
    private String baseUrl = "https://api.razorpay.com/v1";
}