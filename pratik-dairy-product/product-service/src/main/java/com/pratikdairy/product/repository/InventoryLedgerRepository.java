package com.pratikdairy.product.repository;

import com.pratikdairy.product.model.InventoryLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, String> {

    List<InventoryLedger> findByProduct_IdOrderByCreatedAtDesc(String productId);
}