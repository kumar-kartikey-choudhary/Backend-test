package com.pratikdairy.payment.repository;

import com.pratikdairy.payment.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {

    List<PaymentTransaction> findByOrderIdOrderByCreatedAtDesc(String orderId);

    List<PaymentTransaction> findByUsernameOrderByCreatedAtDesc(String username);

    Optional<PaymentTransaction> findByGatewayOrderId(String gatewayOrderId);

    Optional<PaymentTransaction> findByGatewayPaymentId(String gatewayPaymentId);

    /** The customer record on the gateway this user last paid under, if any - reused so they keep one. */
    Optional<PaymentTransaction> findFirstByUsernameAndGatewayCustomerIdIsNotNullOrderByCreatedAtDesc(String username);
}