package com.pratikdairy.parent.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** There is not enough stock to fulfil the requested quantity (409). */
@Getter
public class InsufficientStockException extends BaseException {

    private final String productId;

    public InsufficientStockException(String productId) {
        this(productId, "Insufficient stock for product: " + productId);
    }

    /** Use when the caller can word it for a shopper, e.g. "Only 2 kg of 'Kaju Katli' left". */
    public InsufficientStockException(String productId, String message) {
        super(message, HttpStatus.CONFLICT, "INSUFFICIENT_STOCK");
        this.productId = productId;
    }
}
