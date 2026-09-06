package com.pratikdairy.order.dto;

import com.pratikdairy.parent.base.dto.BaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderItemDto extends BaseDto {
    private String productId;
    private String productName;   // only populated for findAll()/findByCustomerName()
    // Id of the product's primary image - only populated for findAll()/findByCustomerName().
    // Render with product-service's GET /products/images/{id}, not embedded bytes - keeps
    // order list/detail payloads small.
    private String productImageId;
    // Package count at the given `weight` (e.g. 3 packages of "250g"), not a stock-unit amount.
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal subTotal;
    // What weight/package size was purchased, e.g. "250g", "500g", "1kg".
    private String weight;

    // Lightweight - used by create()/updateStatus(), no product-service call needed
    public OrderItemDto(String id, String productId, BigDecimal quantity, BigDecimal price, BigDecimal subTotal, String weight) {
        this.setId(id);
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
        this.subTotal = subTotal;
        this.weight = weight;
    }

    // Enriched - used by findAll()/findByCustomerName(), includes product name/image for display
    public OrderItemDto(String id, String productId, String productName, String productImageId,
                        BigDecimal quantity, BigDecimal price, BigDecimal subTotal, String weight) {
        this.setId(id);
        this.productId = productId;
        this.productName = productName;
        this.productImageId = productImageId;
        this.quantity = quantity;
        this.price = price;
        this.subTotal = subTotal;
        this.weight = weight;
    }
}