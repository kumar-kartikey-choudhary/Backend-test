package com.pratikdairy.product.model;

import com.pratikdairy.parent.base.entity.BaseEntity;
import com.pratikdairy.product.enums.InventoryReason;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

// Previously, Product.stockQuantity was just a number that got mutated in place with
// UPDATE ... SET stockQuantity = stockQuantity - :qty. There was no way to answer "why is stock
// wrong" - every change overwrote the last. This ledger is an append-only log: every restock,
// sale, return, manual adjustment or spoilage write-off gets its own row, so stockQuantity on
// the product is always reconstructible/explainable from history.
@Entity
@Table(name = "INVENTORY_LEDGER")
@Data
@EqualsAndHashCode(callSuper = false)
public class InventoryLedger extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    // Positive for stock coming in (restock, return), negative for stock going out (sale,
    // spoilage). Sum of changeQty for a product should always equal its current stockQuantity.
    @Column(name = "CHANGE_QTY", nullable = false, precision = 12, scale = 3)
    private BigDecimal changeQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "REASON", columnDefinition = "VARCHAR(20) NOT NULL", nullable = false)
    private InventoryReason reason;

    // Free-form pointer back to whatever caused this change, e.g. an order id for a SALE row.
    // Deliberately a plain string, not a FK - the referenced entity (an Order) lives in a
    // different microservice's database.
    @Column(name = "REFERENCE_ID")
    private String referenceId;
}