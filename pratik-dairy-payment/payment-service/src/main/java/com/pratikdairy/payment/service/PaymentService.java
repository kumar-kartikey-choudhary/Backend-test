package com.pratikdairy.payment.service;

import com.pratikdairy.payment.dto.*;

import java.util.List;

public interface PaymentService {

    InitiatePaymentResponse initiate(InitiatePaymentRequest request);

    PaymentTransactionDto verify(VerifyPaymentRequest request);

    /** Returns false if the webhook's signature didn't check out - caller should respond 400. */
    boolean handleWebhook(String rawPayload, String signatureHeader);

    List<PaymentTransactionDto> findByOrder(String orderId);

    List<SavedPaymentMethodDto> getSavedMethods();

    void deleteSavedMethod(String id);

    PaymentTransactionDto refund(String id, RefundRequest request);

    List<PaymentTransactionDto> findAll();

    /** Admin action: confirm a COD transaction's cash was collected at delivery. */
    PaymentTransactionDto markCodCollected(String id);

    // Customer action: they backed out of / failed an online payment (e.g. dismissed the
    // Razorpay UPI sheet) but still want the order - falls back to Cash on Delivery instead
    // of leaving the transaction stuck. Only valid while still PENDING; once it succeeds or
    // is already COD there's nothing to convert. See PaymentServiceImpl.convertToCod().
    PaymentTransactionDto convertToCod(String id);
}