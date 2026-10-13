package com.pratikdairy.order.client;

import com.pratikdairy.parent.exception.ExternalServiceException;
import com.pratikdairy.product.controller.ProductController;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Stock reservation calls to product-service, behind a circuit breaker.
 *
 * This lives in its own bean on purpose: {@code @CircuitBreaker} is applied by a Spring proxy, so it
 * does nothing when the annotated method is called from another method of the same class (which is
 * how it used to be wired inside OrderServiceImpl).
 */
@Component
@Slf4j
public class ProductStockGateway {

    private final ProductController productController;

    @Autowired
    public ProductStockGateway(ProductController productController) {
        this.productController = productController;
    }

    /** @return true if the stock was reserved, false if there isn't enough (or the product is unavailable). */
    @CircuitBreaker(name = "productService", fallbackMethod = "decrementFallback")
    public boolean decrement(String productId, BigDecimal quantity) {
        ResponseEntity<Boolean> response = productController.decrementStock(productId, quantity, null);
        return Boolean.TRUE.equals(response.getBody());
    }

    // Product-service being down is not "out of stock" - say so, instead of telling the customer
    // to reduce a quantity that was never the problem.
    @SuppressWarnings("unused")
    private boolean decrementFallback(String productId, BigDecimal quantity, Throwable t) {
        log.error("Product service unavailable, aborting checkout for {}: {}", productId, t.getMessage());
        throw new ExternalServiceException("We couldn't reserve your items right now. Please try again in a moment.");
    }
}
