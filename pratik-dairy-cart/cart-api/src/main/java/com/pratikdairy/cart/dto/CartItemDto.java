package com.pratikdairy.cart.dto;

import com.pratikdairy.parent.base.dto.BaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class CartItemDto extends BaseDto {
    private String username;
    private String productId;
    private String productName;
    // Id of the product's primary image - render with product-service's GET
    // /products/images/{id}, not embedded bytes. Was `productImageUrl` (byte[]) via
    // ProductDto.getImageData(), which no longer exists on ProductDto (images are now a
    // one-to-many ProductImageDto list) - this was a compile-breaking bug.
    private String productImageId;
    // The product's own stockUnit (e.g. "kg") - what `weight` is measured relative to.
    private String unit;
    // Selected package size for this cart line, e.g. "250g", "500g", "1kg".
    private String weight;
    // Number of packages of the selected weight (e.g. 3 packs of "250g") - NOT the physical
    // stock-unit amount. Use stockToConsume below for anything touching Product.stockQuantity.
    private int quantity;
    // Price per single package of the selected weight (base price scaled by weight ratio).
    private BigDecimal pricePerUnit;
    // pricePerUnit * quantity.
    private BigDecimal subtotal;
    // The actual amount to decrement from Product.stockQuantity, in the product's own
    // stockUnit terms (e.g. 0.75 if stockUnit="kg", weight="250g", quantity=3). Computed via
    // WeightPricing.stockToConsume() - order-service must use THIS field, not `quantity`, when
    // calling ProductController.decrementStock()/restoreStock(), since `quantity` is a package
    // count in a different unit entirely.
    private BigDecimal stockToConsume;
}