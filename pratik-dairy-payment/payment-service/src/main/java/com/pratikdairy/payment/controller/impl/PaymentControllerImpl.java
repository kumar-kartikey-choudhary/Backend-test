package com.pratikdairy.payment.controller.impl;

import com.pratikdairy.payment.controller.PaymentController;
import com.pratikdairy.payment.dto.*;
import com.pratikdairy.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("payments")
@Primary
public class PaymentControllerImpl implements PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentControllerImpl(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public ResponseEntity<InitiatePaymentResponse> initiate(InitiatePaymentRequest request) {
        return new ResponseEntity<>(paymentService.initiate(request), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<PaymentTransactionDto> verify(VerifyPaymentRequest request) {
        return ResponseEntity.ok(paymentService.verify(request));
    }

    @Override
    public ResponseEntity<Void> razorpayWebhook(String rawPayload, String signature) {
        boolean accepted = paymentService.handleWebhook(rawPayload, signature);
        return accepted ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    @Override
    public ResponseEntity<List<PaymentTransactionDto>> findByOrder(String orderId) {
        return ResponseEntity.ok(paymentService.findByOrder(orderId));
    }

    @Override
    public ResponseEntity<List<SavedPaymentMethodDto>> getSavedMethods() {
        return ResponseEntity.ok(paymentService.getSavedMethods());
    }

    @Override
    public void deleteSavedMethod(String id) {
        paymentService.deleteSavedMethod(id);
    }

    @Override
    public ResponseEntity<PaymentTransactionDto> convertToCod(String id) {
        return ResponseEntity.ok(paymentService.convertToCod(id));
    }

    @Override
    public ResponseEntity<PaymentTransactionDto> refund(String id, RefundRequest request) {
        return ResponseEntity.ok(paymentService.refund(id, request));
    }

    @Override
    public ResponseEntity<List<PaymentTransactionDto>> findAll() {
        return ResponseEntity.ok(paymentService.findAll());
    }

    @Override
    public ResponseEntity<PaymentTransactionDto> markCodCollected(String id) {
        return ResponseEntity.ok(paymentService.markCodCollected(id));
    }
}