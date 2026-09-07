package com.pratikdairy.payment.gateway.impl;

import com.pratikdairy.payment.config.RazorpayProperties;
import com.pratikdairy.payment.enums.PaymentMethod;
import com.pratikdairy.payment.gateway.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

// Talks to Razorpay's REST API directly (Basic Auth with key_id:key_secret) rather than pulling
// in the official razorpay-java SDK, so this file is the complete, auditable picture of what
// leaves this service and what comes back - no card data ever appears in any request or
// response body here, only gateway-issued ids and non-sensitive display metadata.
//
// NOTE: verify exact JSON field names against Razorpay's current API docs
// (https://razorpay.com/docs/api/) before going live - the shapes below reflect the
// well-documented Orders/Payments/Customers/Tokens/Refunds APIs but Razorpay does version
// these over time.
@Component
@Slf4j
public class RazorpayGatewayClient implements PaymentGatewayClient {

    private final RazorpayProperties properties;
    private final RestTemplate restTemplate;

    @Autowired
    public RazorpayGatewayClient(RazorpayProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(properties.getKeyId(), properties.getKeySecret());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Override
    public GatewayOrder createOrder(BigDecimal amount, String currency, String receipt) {
        long amountInSubunits = amount.setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();

        Map<String, Object> body = new HashMap<>();
        body.put("amount", amountInSubunits);
        body.put("currency", currency);
        if (receipt != null) {
            body.put("receipt", receipt);
        }

        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getBaseUrl() + "/orders",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()),
                Map.class
        );

        Map<?, ?> result = response.getBody();
        if (result == null || result.get("id") == null) {
            throw new RuntimeException("Razorpay did not return an order id");
        }
        return new GatewayOrder((String) result.get("id"), currency, amountInSubunits);
    }

    @Override
    public boolean verifySignature(String gatewayOrderId, String gatewayPaymentId, String signature) {
        // Razorpay's documented checkout-verification formula:
        // expected = HMAC_SHA256(order_id + "|" + payment_id, key_secret)
        String payload = gatewayOrderId + "|" + gatewayPaymentId;
        String expected = hmacSha256Hex(payload, properties.getKeySecret());
        return constantTimeEquals(expected, signature);
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signatureHeader) {
        String expected = hmacSha256Hex(rawPayload, properties.getWebhookSecret());
        return constantTimeEquals(expected, signatureHeader);
    }

    @Override
    public GatewayPaymentDetails fetchPayment(String gatewayPaymentId) {
        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getBaseUrl() + "/payments/" + gatewayPaymentId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                Map.class
        );

        Map<?, ?> result = response.getBody();
        if (result == null) {
            throw new RuntimeException("Razorpay returned no payment details for " + gatewayPaymentId);
        }

        String methodRaw = String.valueOf(result.get("method"));
        PaymentMethod method = switch (methodRaw) {
            case "upi" -> PaymentMethod.UPI;
            case "card" -> PaymentMethod.CARD;
            case "netbanking" -> PaymentMethod.NETBANKING;
            case "wallet" -> PaymentMethod.WALLET;
            default -> PaymentMethod.CARD;
        };

        String upiVpa = (String) result.get("vpa");
        String cardLastFour = null;
        String cardNetwork = null;
        Object cardObj = result.get("card");
        if (cardObj instanceof Map<?, ?> card) {
            cardLastFour = (String) card.get("last4");
            cardNetwork = (String) card.get("network");
        }

        return new GatewayPaymentDetails(
                gatewayPaymentId,
                method,
                (String) result.get("status"),
                upiVpa,
                cardLastFour,
                cardNetwork
        );
    }

    @Override
    public String getOrCreateGatewayCustomer(String username) {
        // Simplified: creates a customer keyed by username as a note/reference. In production,
        // pass a real email/phone from user-service and check for an existing customer first
        // (e.g. via a locally-stored gatewayCustomerId on the user, or Razorpay's customer list
        // API) rather than creating a new one on every call.
        Map<String, Object> body = Map.of(
                "name", username,
                "fail_existing", "0"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getBaseUrl() + "/customers",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()),
                Map.class
        );

        Map<?, ?> result = response.getBody();
        if (result == null || result.get("id") == null) {
            throw new RuntimeException("Razorpay did not return a customer id");
        }
        return (String) result.get("id");
    }

    @Override
    public GatewaySavedCard tokenizeCard(String gatewayCustomerId, String gatewayPaymentId) {
        // Razorpay auto-saves the card as a token against the customer when Checkout was opened
        // with `save: 1` and a `customer_id` (wired in on the frontend - see PaymentInit.ts).
        // Rather than guess which token corresponds to this payment, we fetch the customer's
        // token list and take the most recent one.
        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getBaseUrl() + "/customers/" + gatewayCustomerId + "/tokens",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                Map.class
        );

        Map<?, ?> result = response.getBody();
        if (result == null) return null;
        Object itemsObj = result.get("items");
        if (!(itemsObj instanceof List<?> items) || items.isEmpty()) {
            return null;
        }
        Object first = items.get(0);
        if (!(first instanceof Map<?, ?> token)) return null;

        Object cardObj = token.get("card");
        String last4 = null;
        String network = null;
        Integer expMonth = null;
        Integer expYear = null;
        if (cardObj instanceof Map<?, ?> card) {
            last4 = (String) card.get("last4");
            network = (String) card.get("network");
            Object m = card.get("expiry_month");
            Object y = card.get("expiry_year");
            if (m != null) expMonth = Integer.valueOf(String.valueOf(m));
            if (y != null) expYear = Integer.valueOf(String.valueOf(y));
        }

        return new GatewaySavedCard((String) token.get("id"), last4, network, expMonth, expYear);
    }

    @Override
    public GatewayRefund refund(String gatewayPaymentId, BigDecimal amount) {
        Map<String, Object> body = amount == null
                ? Map.of()
                : Map.of("amount", amount.setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).longValueExact());

        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getBaseUrl() + "/payments/" + gatewayPaymentId + "/refund",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()),
                Map.class
        );

        Map<?, ?> result = response.getBody();
        if (result == null || result.get("id") == null) {
            throw new RuntimeException("Razorpay did not return a refund id");
        }
        return new GatewayRefund((String) result.get("id"), (String) result.get("status"));
    }

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("HMAC computation failed", e);
            throw new RuntimeException("Signature computation failed", e);
        }
    }

    // Avoids timing-attack leakage when comparing signatures, standard practice for any
    // secret-derived comparison.
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}