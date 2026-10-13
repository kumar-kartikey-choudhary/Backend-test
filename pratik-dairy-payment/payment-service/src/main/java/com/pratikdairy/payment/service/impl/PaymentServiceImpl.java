package com.pratikdairy.payment.service.impl;

import com.pratikdairy.parent.exception.BadRequestException;
import com.pratikdairy.parent.exception.ExternalServiceException;
import com.pratikdairy.parent.exception.InvalidStateException;
import com.pratikdairy.parent.exception.ResourceNotFoundException;
import com.pratikdairy.parent.exception.UnauthorizedException;
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
import feign.FeignException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
            throw new UnauthorizedException("User not authenticated");
        }
        return auth.getName();
    }

    private OrderSummaryDto fetchOrder(String orderId) {
        try {
            return orderStatusClient.findOrderSummary(orderId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Order", orderId);
        } catch (FeignException e) {
            log.error("Could not load order {} from order-service: {}", orderId, e.getMessage());
            throw new ExternalServiceException("Could not load order " + orderId);
        }
    }

    private static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    /** The order's owner, an admin, or another service (the internal key) may act on an order's payments. */
    private void assertCanActOn(OrderSummaryDto order) {
        String username = getUsername();
        if (!username.equals(order.getUsername()) && !hasAuthority("ROLE_ADMIN") && !hasAuthority("ROLE_SERVICE")) {
            throw new AccessDeniedException("This order does not belong to the current user");
        }
    }

    /**
     * The gateway-side customer a card is saved under or reused from. A saved card brings its own customer;
     * otherwise the user keeps the customer from their previous card payment, and only gets a new one the
     * first time - so a person has one customer on the gateway, not one per payment.
     */
    private String resolveGatewayCustomer(String username, String savedPaymentMethodId) {
        if (savedPaymentMethodId != null && !savedPaymentMethodId.isBlank()) {
            SavedPaymentMethod method = savedMethodRepository.findById(savedPaymentMethodId)
                    .orElseThrow(() -> new ResourceNotFoundException("Saved payment method", savedPaymentMethodId));
            if (!method.getUsername().equals(username)) {
                throw new AccessDeniedException("This saved payment method does not belong to the current user");
            }
            return method.getGatewayCustomerId();
        }
        return savedMethodRepository.findByUsername(username).stream()
                .map(SavedPaymentMethod::getGatewayCustomerId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .or(() -> transactionRepository
                        .findFirstByUsernameAndGatewayCustomerIdIsNotNullOrderByCreatedAtDesc(username)
                        .map(PaymentTransaction::getGatewayCustomerId))
                .orElseGet(() -> gateway.getOrCreateGatewayCustomer(username));
    }

    @Override
    @Transactional
    public InitiatePaymentResponse initiate(InitiatePaymentRequest request) {
        log.info("Inside @class PaymentServiceImpl @method initiate @param request: {}", request);
        String username = getUsername();

        // What gets charged is the order's own total, read from the order service - never the amount
        // in the request, which a customer could set to anything (even 1) for a large order.
        OrderSummaryDto order = fetchOrder(request.getOrderId());
        if (!username.equals(order.getUsername())) {
            throw new AccessDeniedException("This order does not belong to the current user");
        }
        if ("CANCELLED".equals(order.getStatus())) {
            throw new InvalidStateException("This order has been cancelled, so it can't be paid for");
        }
        BigDecimal amount = order.getTotalAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("This order has nothing to pay");
        }
        if (request.getAmount() != null && request.getAmount().compareTo(amount) != 0) {
            log.warn("Payment amount {} sent for order {} does not match its total {} - charging the order total",
                    request.getAmount(), order.getId(), amount);
        }
        boolean alreadyPaid = transactionRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .anyMatch(t -> t.getStatus() == PaymentStatus.SUCCESS);
        if (alreadyPaid) {
            throw new InvalidStateException("This order has already been paid for");
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(order.getId());
        transaction.setUsername(username);
        transaction.setAmount(amount);
        transaction.setCurrency("INR");
        transaction.setMethod(request.getMethod());
        transaction.setStatus(PaymentStatus.CREATED);

        // COD needs no gateway interaction at all - record it for the audit trail (every order
        // has SOME payment record, even "pay on delivery") and tell the frontend there's no
        // Checkout widget to open.
        if (request.getMethod() == PaymentMethod.COD) {
            transaction.setStatus(PaymentStatus.PENDING); // becomes SUCCESS when admin records collection at delivery
            PaymentTransaction saved = transactionRepository.saveAndFlush(transaction);
            return new InitiatePaymentResponse(saved.getId(), null, null, amount, "INR");
        }

        // Saving a card, or paying with a saved one, needs Checkout opened under the customer the cards live on.
        String gatewayCustomerId = null;
        if (request.getMethod() == PaymentMethod.CARD
                && (request.isSaveCard() || (request.getSavedPaymentMethodId() != null && !request.getSavedPaymentMethodId().isBlank()))) {
            gatewayCustomerId = resolveGatewayCustomer(username, request.getSavedPaymentMethodId());
            transaction.setGatewayCustomerId(gatewayCustomerId);
        }

        GatewayOrder gatewayOrder = gateway.createOrder(amount, "INR", null);
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
                amount,
                "INR",
                gatewayCustomerId
        );
    }

    @Override
    @Transactional
    public PaymentTransactionDto verify(VerifyPaymentRequest request) {
        log.info("Inside @class PaymentServiceImpl @method verify @param request: {}", request);
        String username = getUsername();

        PaymentTransaction transaction = transactionRepository.findById(request.getPaymentTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction", request.getPaymentTransactionId()));

        if (!transaction.getUsername().equals(username)) {
            throw new AccessDeniedException("This payment does not belong to the current user");
        }
        // Only a payment still waiting for its result can be verified. Without this, replaying a
        // valid signature after a refund would flip REFUNDED back to SUCCESS (and allow a second
        // refund), and a junk signature would flip a settled SUCCESS to FAILED.
        if (transaction.getStatus() == PaymentStatus.SUCCESS
                && request.getRazorpayPaymentId() != null
                && request.getRazorpayPaymentId().equals(transaction.getGatewayPaymentId())) {
            return toDto(transaction); // the browser retried a verify that already went through
        }
        if (transaction.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidStateException(
                    "This payment is already " + transaction.getStatus() + " and can no longer be verified");
        }
        if (transaction.getGatewayOrderId() == null
                || !transaction.getGatewayOrderId().equals(request.getRazorpayOrderId())) {
            throw new BadRequestException("Order id mismatch - possible tampering");
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
        // The order may have been cancelled (by the customer, or by the unpaid-order sweep) while the
        // payment window was open. Money for a cancelled order goes straight back.
        saved = refundIfOrderCancelled(saved);
        return toDto(saved);
    }

    // Best effort: the payment itself is recorded either way, and a failure here is logged for a manual refund.
    private PaymentTransaction refundIfOrderCancelled(PaymentTransaction transaction) {
        try {
            OrderSummaryDto order = fetchOrder(transaction.getOrderId());
            if ("CANCELLED".equals(order.getStatus())) {
                log.warn("Payment {} arrived for order {}, which was already cancelled - refunding it",
                        transaction.getId(), transaction.getOrderId());
                PaymentTransaction refunded = refundAtGateway(transaction, null);
                notifyOrderService(refunded, "REFUNDED");
                return refunded;
            }
        } catch (RuntimeException e) {
            log.error("Payment {} for order {} may belong to a cancelled order and could not be checked or refunded - "
                    + "needs a manual look: {}", transaction.getId(), transaction.getOrderId(), e.getMessage());
        }
        return transaction;
    }

    private void saveCardToken(String username, String gatewayPaymentId, PaymentTransaction transaction) {
        // The card was saved under the customer Checkout was opened with at initiate().
        String gatewayCustomerId = transaction.getGatewayCustomerId() != null
                ? transaction.getGatewayCustomerId()
                : resolveGatewayCustomer(username, null);
        GatewaySavedCard card = gateway.tokenizeCard(gatewayCustomerId, gatewayPaymentId);
        if (card == null) {
            return;
        }
        if (savedMethodRepository.findByGatewayTokenId(card.tokenId()).isPresent()) {
            return; // the same card again (or a retried verify) - don't list it twice
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
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction", id));

        if (!transaction.getUsername().equals(username)) {
            throw new AccessDeniedException("This payment does not belong to the current user");
        }
        if (transaction.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidStateException(
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
        // live - this covers the events most relevant to reconciliation.
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String event = root.path("event").asText();
            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            String gatewayPaymentId = paymentEntity.path("id").asText(null);
            String gatewayOrderId = paymentEntity.path("order_id").asText(null);
            if (gatewayPaymentId == null) {
                log.warn("Webhook event {} had no payment id, ignoring", event);
                return true;
            }

            // A payment that was never verified by the browser (tab closed right after paying) has no
            // gateway payment id on our side yet - find it by the Razorpay order it was made against.
            PaymentTransaction byPaymentId = transactionRepository.findByGatewayPaymentId(gatewayPaymentId).orElse(null);
            PaymentTransaction transaction = byPaymentId != null ? byPaymentId
                    : (gatewayOrderId == null ? null : transactionRepository.findByGatewayOrderId(gatewayOrderId).orElse(null));
            if (transaction == null) {
                log.warn("Webhook for unknown payment {} (order {})", gatewayPaymentId, gatewayOrderId);
                return true;
            }

            switch (event) {
                // Money was captured. This is the gateway's own word, so it also settles a payment we had
                // marked failed or closed (the customer paid after we gave up on it). An already settled
                // or refunded payment is left alone.
                case "payment.captured" -> {
                    PaymentStatus status = transaction.getStatus();
                    if (status != PaymentStatus.CREATED && status != PaymentStatus.PENDING && status != PaymentStatus.FAILED) {
                        log.info("Ignoring payment.captured for {} - already {}", gatewayPaymentId, status);
                        return true;
                    }
                    transaction.setGatewayPaymentId(gatewayPaymentId);
                    transaction.setFailureReason(null);
                    transaction.setStatus(PaymentStatus.SUCCESS);
                    transaction.setPaidAt(LocalDateTime.now());
                    transactionRepository.saveAndFlush(transaction);
                    notifyOrderService(transaction, "SUCCESS");
                    refundIfOrderCancelled(transaction);
                }
                // One Razorpay order can see several attempts, so a failure only counts if it is THIS
                // transaction's own payment - a failed earlier try must not undo a later success.
                case "payment.failed" -> {
                    if (byPaymentId == null || transaction.getStatus() != PaymentStatus.PENDING) {
                        log.info("Ignoring payment.failed for {} - not this transaction's payment, or already {}",
                                gatewayPaymentId, transaction.getStatus());
                        return true;
                    }
                    transaction.setStatus(PaymentStatus.FAILED);
                    transaction.setFailureReason(paymentEntity.path("error_description").asText(null));
                    transactionRepository.saveAndFlush(transaction);
                    notifyOrderService(transaction, "FAILED");
                }
                // A refund made outside this app (e.g. from the Razorpay dashboard): mirror what the gateway says.
                case "refund.processed" -> {
                    if (transaction.getStatus() != PaymentStatus.SUCCESS) {
                        return true;
                    }
                    long refundedPaise = paymentEntity.path("amount_refunded").asLong(-1);
                    if (refundedPaise >= 0) {
                        BigDecimal refunded = BigDecimal.valueOf(refundedPaise).movePointLeft(2);
                        transaction.setRefundedAmount(refunded);
                        if (refunded.compareTo(transaction.getAmount()) >= 0) {
                            transaction.setStatus(PaymentStatus.REFUNDED);
                        }
                        transactionRepository.saveAndFlush(transaction);
                    }
                }
                default -> log.debug("Unhandled webhook event type: {}", event);
            }
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
        String username = getUsername();
        // A customer may only see payments on their own orders; admins see everything.
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return transactionRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(t -> isAdmin || username.equals(t.getUsername()))
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
                .orElseThrow(() -> new ResourceNotFoundException("Saved payment method", id));
        if (!method.getUsername().equals(username)) {
            throw new AccessDeniedException("This saved payment method does not belong to the current user");
        }
        savedMethodRepository.deleteById(id);
        // So Checkout stops offering it. Best effort - it is already gone from the customer's list.
        if (method.getGatewayCustomerId() != null && method.getGatewayTokenId() != null) {
            gateway.deleteToken(method.getGatewayCustomerId(), method.getGatewayTokenId());
        }
    }

    @Override
    @Transactional
    public PaymentTransactionDto refund(String id, RefundRequest request) {
        PaymentTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction", id));
        return toDto(refundAtGateway(transaction, request.getAmount()));
    }

    // Shared by the admin refund and the automatic refunds (cancellation, late payment).
    // amount == null means "everything that is left".
    private PaymentTransaction refundAtGateway(PaymentTransaction transaction, BigDecimal amount) {
        if (transaction.getStatus() != PaymentStatus.SUCCESS) {
            throw new InvalidStateException("Only a successful payment can be refunded");
        }
        if (transaction.getMethod() == PaymentMethod.COD || transaction.getGatewayPaymentId() == null) {
            throw new InvalidStateException(
                    "This payment didn't go through the payment gateway (Cash on Delivery), so there is nothing to refund online");
        }
        BigDecimal alreadyRefunded = transaction.getRefundedAmount() == null ? BigDecimal.ZERO : transaction.getRefundedAmount();
        BigDecimal remaining = transaction.getAmount().subtract(alreadyRefunded);
        if (remaining.signum() <= 0) {
            throw new InvalidStateException("This payment has already been refunded in full");
        }
        BigDecimal toRefund = amount == null ? remaining : amount;
        if (toRefund.signum() <= 0) {
            throw new BadRequestException("The refund amount must be greater than 0");
        }
        if (toRefund.compareTo(remaining) > 0) {
            throw new BadRequestException("Only ₹" + remaining.stripTrailingZeros().toPlainString()
                    + " is left to refund on this payment");
        }

        GatewayRefund refund = gateway.refund(transaction.getGatewayPaymentId(), toRefund);
        log.info("Refund {} of {} created with status {} for payment {}", refund.id(), toRefund, refund.status(), transaction.getGatewayPaymentId());

        transaction.setRefundedAmount(alreadyRefunded.add(toRefund));
        // Only a refund of everything makes it REFUNDED; until then it is still a successful payment,
        // with part of the money returned.
        if (transaction.getRefundedAmount().compareTo(transaction.getAmount()) >= 0) {
            transaction.setStatus(PaymentStatus.REFUNDED);
        }
        return transactionRepository.saveAndFlush(transaction);
    }

    // Deliberately not @Transactional: each refund is committed as soon as the gateway has accepted
    // it. If a later one fails, the earlier ones must not be rolled back locally while the money has
    // already gone back at the gateway.
    @Override
    public List<PaymentTransactionDto> refundCancelledOrder(String orderId) {
        log.info("Inside @class PaymentServiceImpl @method refundCancelledOrder @param orderId: {}", orderId);
        OrderSummaryDto order = fetchOrder(orderId);
        assertCanActOn(order);
        if (!"CANCELLED".equals(order.getStatus())) {
            throw new InvalidStateException(
                    "Only a cancelled order can be refunded this way - this one is " + order.getStatus());
        }

        List<PaymentTransactionDto> changed = new ArrayList<>();
        for (PaymentTransaction transaction : transactionRepository.findByOrderIdOrderByCreatedAtDesc(orderId)) {
            PaymentStatus status = transaction.getStatus();
            if (status == PaymentStatus.SUCCESS && transaction.getMethod() != PaymentMethod.COD
                    && transaction.getGatewayPaymentId() != null) {
                PaymentTransaction refunded = refundAtGateway(transaction, null);
                notifyOrderService(refunded, "REFUNDED");
                changed.add(toDto(refunded));
            } else if (status == PaymentStatus.CREATED || status == PaymentStatus.PENDING) {
                // Nothing was paid - close it so it can't be completed against a cancelled order.
                transaction.setStatus(PaymentStatus.FAILED);
                transaction.setFailureReason("Order cancelled");
                changed.add(toDto(transactionRepository.saveAndFlush(transaction)));
            }
        }
        return changed;
    }

    @Override
    public PaymentSummaryDto summarize(String orderId) {
        List<PaymentTransaction> transactions = transactionRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        boolean settledOrCod = transactions.stream().anyMatch(t ->
                t.getStatus() == PaymentStatus.SUCCESS || t.getStatus() == PaymentStatus.REFUNDED
                        || (t.getMethod() == PaymentMethod.COD && t.getStatus() == PaymentStatus.PENDING));
        ZonedDateTime lastActivity = transactions.stream()
                .map(t -> t.getModifiedAt() != null ? t.getModifiedAt() : t.getCreatedAt())
                .filter(java.util.Objects::nonNull)
                .max(ZonedDateTime::compareTo)
                .orElse(null);
        return new PaymentSummaryDto(settledOrCod, lastActivity);
    }

    @Override
    @Transactional
    public void closeOpenAttempts(String orderId) {
        for (PaymentTransaction transaction : transactionRepository.findByOrderIdOrderByCreatedAtDesc(orderId)) {
            if (transaction.getStatus() == PaymentStatus.CREATED || transaction.getStatus() == PaymentStatus.PENDING) {
                transaction.setStatus(PaymentStatus.FAILED);
                transaction.setFailureReason("Order cancelled");
                transactionRepository.saveAndFlush(transaction);
            }
        }
    }

    @Override
    public List<PaymentTransactionDto> findAll() {
        return transactionRepository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public PaymentTransactionDto markCodCollected(String id) {
        PaymentTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction", id));

        if (transaction.getMethod() != PaymentMethod.COD) {
            throw new InvalidStateException("Only a Cash on Delivery transaction can be marked collected");
        }
        if (transaction.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidStateException("Only a PENDING COD transaction can be marked collected");
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
        dto.setRefundedAmount(t.getRefundedAmount());
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