package com.pratikdairy.payment.repository;

import com.pratikdairy.payment.model.SavedPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedPaymentMethodRepository extends JpaRepository<SavedPaymentMethod, String> {

    List<SavedPaymentMethod> findByUsername(String username);

    Optional<SavedPaymentMethod> findByGatewayTokenId(String gatewayTokenId);
}