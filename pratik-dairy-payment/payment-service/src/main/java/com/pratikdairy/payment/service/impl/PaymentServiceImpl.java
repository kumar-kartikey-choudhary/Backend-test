package com.pratikdairy.payment.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pratikdairy.payment.client.OrderStatusClient;
import com.pratikdairy.payment.config.RazorpayProperties;
import com.pratikdairy.payment.dto.*;
import com.pratikdairy.payment.enums.CardNetwork;
import com.pratikdairy.payment.enums.PaymentMethod;
import com.pratikdairy.payment.enums.PaymentStatus;
import com.pratikdairy.payment.gateway.GatewayOrder;
import com.pratikdairy.payment.gateway.GatewayPaymentDetails;
import com.pratikdairy.payment.gateway.GatewayRefund;
import com.pratikdairy.payment.gateway.GatewaySavedCard;
import com.pratikdairy.payment.gateway.PaymentGatewayClient;
import com.pratikdairy.payment.model.PaymentTransaction;
import com.pratikdairy.payment.model.SavedPaymentMethod;
import com.pratikdairy.payment.repository.PaymentTransactionRepository;
import com.pratikdairy.payment.repository.SavedPaymentMethodRepository;
import com.pratikdairy.payment.service.PaymentService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final SavedPaymentMethodRepository savedMethodRepository;
    private final PaymentGatewayClient gateway;
    private final RazorpayProperties razorpayProperties;
    private final OrderStatusClient orderStatusClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public PaymentServiceImpl(PaymentTransactionRepository transactionRepository,
                              SavedPaymentMethodRepository savedMethodRepository,
                              PaymentGatewayClient gateway,
                              RazorpayProperties razorpayProperties,
                              OrderStatusClient orderStatusClient) {
        this.transactionRepository = transactionRepository;
        this.savedMethodRepository = savedMethodRepository;
        this.gateway = gateway;
        this.razorpayProperties = razorpayProperties;
        this.orderStatusClient = orderStatusClient;
    }

    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return auth.getName();
    }

    @Override
    @Transactional
    public InitiatePaymentResponse initiate(InitiatePaymentRequest request) {
        log.info("Inside @class PaymentServiceImpl @method initiate @param request: {}", request);
        String username = getUsername();

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(request.getOrderId());
        transaction.setUsername(username);
        transaction.setAmount(request.getAmount());
        transaction.setCurrency("INR");
        transaction.setMethod(request.getMethod());
        transaction.setStatus(PaymentStatus.CREATED);

        // COD needs no gateway interaction at all - record it for the audit trail (every order
        // has SOME payment record, even "pay on delivery") and tell the frontend there's no
        // Checkout widget to open.
        if (request.getMethod() == PaymentMethod.COD) {
            transaction.setStatus(PaymentStatus.PENDING); // becomes SUCCESS when admin records collection at delivery
            PaymentTransaction saved = transactionRepository.saveAndFlush(transaction);
            return new InitiatePaymentResponse(saved.getId(), null, null, request.getAmount(), "INR");
        }

        GatewayOrder gatewayOrder = gateway.createOrder(request.getAmount(), "INR", null);
        transaction.setGatewayOrderId(gatewayOrder.id());
        transaction.setStatus(PaymentStatus.PENDING);

        PaymentTransaction saved = transactionRepository.saveAndFlush(transaction);
        // receipt = our own transaction id, set after save so it's the real id, not a temp one.
        // (createOrder() above passes null for receipt since the id doesn't exist yet -
        // Razorpay's receipt field is informational only, so this ordering is fine.)

        return new InitiatePaymentResponse(
                saved.getId(),
                gatewayOrder.id(),
                razorpayProperties.getKeyId(),
                request.getAmount(),
                "INR"
        );
    }

    @Override
    @Transactional
    public PaymentTransactionDto verify(VerifyPaymentRequest request) {
        log.info("Inside @class PaymentServiceImpl @method verify @param request: {}", request);
        String username = getUsername();

        PaymentTransaction transaction = transactionRepository.findById(request.getPaymentTransactionId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment transaction not found: " + request.getPaymentTransactionId()));

        if (!transaction.getUsername().equals(username)) {
            throw new AccessDeniedException("This payment does not belong to the current user");
        }
        if (transaction.getGatewayOrderId() == null
                || !transaction.getGatewayOrderId().equals(request.getRazorpayOrderId())) {
            throw new IllegalArgumentException("Order id mismatch - possible tampering");
        }

        boolean signatureValid = gateway.verifySignature(
                request.getRazorpayOrderId(), request.getRazorpayPaymentId(), request.getRazorpaySignature());

        if (!signatureValid) {
            transaction.setStatus(PaymentStatus.FAILED);
            transaction.setFailureReason("Signature verification failed");
            transactionRepository.saveAndFlush(transaction);
            notifyOrderService(transaction, "FAILED");
            return toDto(transaction);
        }

        transaction.setGatewayPaymentId(request.getRazorpayPaymentId());
        transaction.setStatus(PaymentStatus.SUCCESS);
        transaction.setPaidAt(LocalDateTime.now());

        GatewayPaymentDetails details = gateway.fetchPayment(request.getRazorpayPaymentId());
        transaction.setUpiVpa(details.upiVpa());

        if (request.isSaveCard() && transaction.getMethod() == PaymentMethod.CARD) {
            try {
                saveCardToken(username, request.getRazorpayPaymentId(), transaction);
            } catch (Exception e) {
                // A failed tokenization shouldn't fail an otherwise-successful payment - the
                // customer already paid; they just won't get a saved card out of it this time.
                log.error("Failed to save card token for user {}: {}", username, e.getMessage());
            }
        }

        PaymentTransaction saved = transactionRepository.saveAndFlush(transaction);
        notifyOrderService(saved, "SUCCESS");
        return toDto(saved);
    }

    private void saveCardToken(String username, String gatewayPaymentId, PaymentTransaction transaction) {
        String gatewayCustomerId = gateway.getOrCreateGatewayCustomer(username);
        GatewaySavedCard card = gateway.tokenizeCard(gatewayCustomerId, gatewayPaymentId);
        if (card == null) {
            return;
        }

        SavedPaymentMethod saved = new SavedPaymentMethod();
        saved.setUsername(username);
        saved.setGatewayCustomerId(gatewayCustomerId);
        saved.setGatewayTokenId(card.tokenId());
        saved.setCardLastFour(card.cardLastFour());
        saved.setCardNetwork(parseCardNetwork(card.cardNetwork()));
        saved.setCardExpiryMonth(card.expiryMonth());
        saved.setCardExpiryYear(card.expiryYear());
        saved.setDefault(savedMethodRepository.findByUsername(username).isEmpty());

        SavedPaymentMethod persisted = savedMethodRepository.saveAndFlush(saved);
        transaction.setSavedPaymentMethod(persisted);
    }

    private CardNetwork parseCardNetwork(String raw) {
        if (raw == null) return CardNetwork.OTHER;
        try {
            return CardNetwork.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CardNetwork.OTHER;
        }
    }

    // Best-effort: the customer already has a definitive SUCCESS/FAILED answer on their screen
    // from this call's own return value, so a failure here (order-service down, etc.) shouldn't
    // surface as a payment error - it's logged for reconciliation instead.
    private void notifyOrderService(PaymentTransaction transaction, String status) {
        try {
            orderStatusClient.notifyPaymentResult(transaction.getOrderId(), status, transaction.getId());
        } catch (Exception e) {
            log.error("Failed to notify order-service of payment {} for order {}: {}",
                    status, transaction.getOrderId(), e.getMessage());
        }
    }

    // Customer chose "Place order anyway" after backing out of / failing the online payment
    // sheet (e.g. dismissed Razorpay's UPI screen). Switches this transaction to COD so the
    // order isn't left stuck - same PENDING state a COD order would already be in, just
    // reached from a different starting method. If they instead retry and pay successfully
    // before ever calling this, verify() already moved the transaction to SUCCESS and this
    // guard below stops a stale "place order anyway" popup click from reverting that.
    @Override
    @Transactional
    public PaymentTransactionDto convertToCod(String id) {
        log.info("Inside @class PaymentServiceImpl @method convertToCod @param id: {}", id);
        String username = getUsername();

        PaymentTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment transaction not found: " + id));

        if (!transaction.getUsername().equals(username)) {
            throw new AccessDeniedException("This payment does not belong to the current user");
        }
        if (transaction.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a PENDING payment can be switched to Cash on Delivery - this one is " + transaction.getStatus());
        }
        if (transaction.getMethod() == PaymentMethod.COD) {
            // Already COD - nothing to do, just hand back the current state.
            return toDto(transaction);
        }

        transaction.setMethod(PaymentMethod.COD);
        PaymentTransaction saved = transactionRepository.saveAndFlush(transaction);
        return toDto(saved);
    }

    @Override
    @Transactional
    public boolean handleWebhook(String rawPayload, String signatureHeader) {
        if (!gateway.verifyWebhookSignature(rawPayload, signatureHeader)) {
            log.warn("Rejected webhook call with invalid signature");
            return false;
        }

        // NOTE: verify this shape against Razorpay's current webhook payload docs before going
        // live - this covers the two events most relevant to reconciliation.
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String event = root.path("event").asText();
            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            String gatewayPaymentId = paymentEntity.path("id").asText(null);
            if (gatewayPaymentId == null) {
                log.warn("Webhook event {} had no payment id, ignoring", event);
                return true;
            }

            transactionRepository.findByGatewayPaymentId(gatewayPaymentId).ifPresentOrElse(transaction -> {
                switch (event) {
                    case "payment.captured" -> {
                        transaction.setStatus(PaymentStatus.SUCCESS);
                        transaction.setPaidAt(LocalDateTime.now());
                        transactionRepository.saveAndFlush(transaction);
                        notifyOrderService(transaction, "SUCCESS");
                    }
                    case "payment.failed" -> {
                        transaction.setStatus(PaymentStatus.FAILED);
                        transaction.setFailureReason(paymentEntity.path("error_description").asText(null));
                        transactionRepository.saveAndFlush(transaction);
                        notifyOrderService(transaction, "FAILED");
                    }
                    case "refund.processed" -> {
                        transaction.setStatus(PaymentStatus.REFUNDED);
                        transactionRepository.saveAndFlush(transaction);
                    }
                    default -> log.debug("Unhandled webhook event type: {}", event);
                }
            }, () -> log.warn("Webhook for unknown payment id {}", gatewayPaymentId));
        } catch (Exception e) {
            // Signature already verified above - a parsing failure here is a bug or an
            // unexpected payload shape, not a security issue. Log and move on rather than
            // returning a 5xx that makes Razorpay retry forever.
            log.error("Failed to process webhook payload: {}", e.getMessage(), e);
        }

        return true;
    }

    @Override
    public List<PaymentTransactionDto> findByOrder(String orderId) {
        return transactionRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(this::toDto).toList();
    }

    @Override
    public List<SavedPaymentMethodDto> getSavedMethods() {
        String username = getUsername();
        return savedMethodRepository.findByUsername(username).stream()
                .map(this::toDto).toList();
    }

    @Override
    @Transactional
    public void deleteSavedMethod(String id) {
        String username = getUsername();
        SavedPaymentMethod method = savedMethodRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Saved payment method not found: " + id));
        if (!method.getUsername().equals(username)) {
            throw new AccessDeniedException("This saved payment method does not belong to the current user");
        }
        savedMethodRepository.deleteById(id);
    }

    @Override
    @Transactional
    public PaymentTransactionDto refund(String id, RefundRequest request) {
        PaymentTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment transaction not found: " + id));

        if (transaction.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Only a successful payment can be refunded");
        }

        GatewayRefund refund = gateway.refund(transaction.getGatewayPaymentId(), request.getAmount());
        log.info("Refund {} created with status {} for payment {}", refund.id(), refund.status(), transaction.getGatewayPaymentId());

        transaction.setStatus(PaymentStatus.REFUNDED);
        PaymentTransaction saved = transactionRepository.saveAndFlush(transaction);
        return toDto(saved);
    }

    @Override
    public List<PaymentTransactionDto> findAll() {
        return transactionRepository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public PaymentTransactionDto markCodCollected(String id) {
        PaymentTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment transaction not found: " + id));

        if (transaction.getMethod() != PaymentMethod.COD) {
            throw new IllegalStateException("Only a Cash on Delivery transaction can be marked collected");
        }
        if (transaction.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only a PENDING COD transaction can be marked collected");
        }

        transaction.setStatus(PaymentStatus.SUCCESS);
        transaction.setPaidAt(LocalDateTime.now());

        PaymentTransaction saved = transactionRepository.saveAndFlush(transaction);
        notifyOrderService(saved, "SUCCESS");
        return toDto(saved);
    }

    private PaymentTransactionDto toDto(PaymentTransaction t) {
        PaymentTransactionDto dto = new PaymentTransactionDto();
        dto.setId(t.getId());
        dto.setOrderId(t.getOrderId());
        dto.setAmount(t.getAmount());
        dto.setCurrency(t.getCurrency());
        dto.setMethod(t.getMethod());
        dto.setStatus(t.getStatus());
        dto.setGatewayPaymentId(t.getGatewayPaymentId());
        dto.setUpiVpa(t.getUpiVpa());
        dto.setFailureReason(t.getFailureReason());
        dto.setPaidAt(t.getPaidAt());
        dto.setCreatedAt(t.getCreatedAt());
        return dto;
    }

    private SavedPaymentMethodDto toDto(SavedPaymentMethod m) {
        SavedPaymentMethodDto dto = new SavedPaymentMethodDto();
        dto.setId(m.getId());
        dto.setCardLastFour(m.getCardLastFour());
        dto.setCardNetwork(m.getCardNetwork());
        dto.setCardExpiryMonth(m.getCardExpiryMonth());
        dto.setCardExpiryYear(m.getCardExpiryYear());
        dto.setDefault(m.isDefault());
        dto.setCreatedAt(m.getCreatedAt());
        return dto;
    }
}