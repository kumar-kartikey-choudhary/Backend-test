package com.pratikdairy.order.service.impl;

import com.pratikdairy.cart.controller.CartController;
import com.pratikdairy.cart.dto.CartItemDto;
import com.pratikdairy.order.dto.*;
import com.pratikdairy.order.enums.DiscountType;
import com.pratikdairy.order.enums.OrderStatus;
import com.pratikdairy.order.model.*;
import com.pratikdairy.order.repository.CouponRepository;
import com.pratikdairy.order.repository.OrderRepository;
import com.pratikdairy.order.service.OrderService;
import com.pratikdairy.product.controller.ProductController;
import com.pratikdairy.product.dto.ProductDto;
import com.pratikdairy.product.dto.ProductImageDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CouponRepository couponRepository;
    private final CartController cartController;
    private final ProductController productController;

    @Autowired
    public OrderServiceImpl(OrderRepository orderRepository,
                            CouponRepository couponRepository,
                            CartController cartController,
                            ProductController productController) {
        this.orderRepository = orderRepository;
        this.couponRepository = couponRepository;
        this.cartController = cartController;
        this.productController = productController;
    }

    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return auth.getName();
    }

    private ProductDto fetchProduct(String productId) {
        ResponseEntity<ProductDto> response = productController.find(productId);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Could not fetch product details for id " + productId);
        }
        return response.getBody();
    }

    @Override
    @Transactional
    public OrderResponse create(String couponCode) {
        log.info("Inside @class OrderServiceImpl @method create @param couponCode: {}", couponCode);
        String username = getUsername();
        List<CartItemDto> cartItems = cartController.getCart().getBody();

        if (cartItems == null || cartItems.isEmpty()) {
            throw new NullPointerException("Cart item is empty");
        }

        // Trust the price/quantity the cart already computed - no need to re-fetch every
        // product here, only to decrement stock below.
        List<CartItemDto> decrementedSoFar = new ArrayList<>();
        for (CartItemDto item : cartItems) {
            // orderId is null here - the order doesn't exist yet at decrement time, so this
            // sale's ledger entry won't carry a reference id. Acceptable trade-off: the
            // alternative (create the order first, then decrement) risks leaving a saved order
            // with items that can't actually be fulfilled.
            ResponseEntity<Boolean> response = safeDecrementStock(item.getProductId(), item.getQuantity());
            boolean success = response.getBody() != null && response.getBody();
            if (!success) {
                for (CartItemDto done : decrementedSoFar) {
                    productController.restoreStock(done.getProductId(), done.getQuantity(), null);
                }
                throw new RuntimeException("Insufficient stock for product: " + item.getProductId());
            }
            decrementedSoFar.add(item);
        }

        BigDecimal subtotal = cartItems.stream()
                .map(CartItemDto::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Coupon coupon = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (couponCode != null && !couponCode.isBlank()) {
            coupon = validateAndApplyCoupon(couponCode, subtotal);
            discountAmount = computeDiscount(coupon, subtotal);
        }

        BigDecimal totalAmount = subtotal.subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        // Order creation and payment are decoupled: this always produces a CONFIRMED order with
        // stock already reserved, regardless of how the customer intends to pay. If they're
        // paying online (UPI/card), the frontend calls payment-service's /payments/initiate
        // with this order's id right after this returns - see pratik-dairy-payment. COD orders
        // need nothing further. A failed online payment does NOT auto-cancel this order today;
        // that's a reasonable next improvement (release stock + cancel) rather than something
        // baked in here.
        Order order = new Order();
        order.setUsername(username);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setSubtotalAmount(subtotal);
        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(totalAmount);
        order.setCoupon(coupon);

        List<OrderItems> orderItems = cartItems.stream()
                .map(item -> new OrderItems(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPricePerUnit(),
                        order
                ))
                .toList();
        order.setItems(orderItems);

        OrderStatusHistory initialStatus = new OrderStatusHistory();
        initialStatus.setOrder(order);
        initialStatus.setStatus(OrderStatus.CONFIRMED);
        initialStatus.setRemarks("Order placed");
        initialStatus.setChangedBy(username);
        order.getStatusHistory().add(initialStatus);

        Order savedOrder = orderRepository.saveAndFlush(order);

        cartController.clearCart();

        return mapToOrderResponse(savedOrder);
    }

    // Validates the coupon is usable against this subtotal (exists, active, in-window, under its
    // usage cap, subtotal meets the minimum) and bumps its usedCount. Throws on any failure so
    // checkout aborts cleanly rather than silently applying (or silently ignoring) a bad code.
    private Coupon validateAndApplyCoupon(String couponCode, BigDecimal subtotal) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(couponCode)
                .orElseThrow(() -> new IllegalArgumentException("Coupon code not found: " + couponCode));

        if (!coupon.isActive()) {
            throw new IllegalArgumentException("Coupon '" + couponCode + "' is no longer active");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            throw new IllegalArgumentException("Coupon '" + couponCode + "' is not valid yet");
        }
        if (coupon.getValidTo() != null && now.isAfter(coupon.getValidTo())) {
            throw new IllegalArgumentException("Coupon '" + couponCode + "' has expired");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new IllegalArgumentException("Coupon '" + couponCode + "' has reached its usage limit");
        }
        if (coupon.getMinOrderValue() != null && subtotal.compareTo(coupon.getMinOrderValue()) < 0) {
            throw new IllegalArgumentException("Order subtotal does not meet the minimum for coupon '" + couponCode + "'");
        }

        coupon.setUsedCount(coupon.getUsedCount() + 1);
        return couponRepository.saveAndFlush(coupon);
    }

    private BigDecimal computeDiscount(Coupon coupon, BigDecimal subtotal) {
        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.PERCENT) {
            discount = subtotal.multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
                discount = coupon.getMaxDiscount();
            }
        } else {
            discount = coupon.getDiscountValue();
        }
        // A discount can never exceed the subtotal it's being applied to.
        return discount.compareTo(subtotal) > 0 ? subtotal : discount;
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemDto> itemDtos = order.getItems()
                .stream()
                .map(item -> new OrderItemDto(
                        item.getId(),
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getPrice().multiply(item.getQuantity())
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUsername(),
                order.getStatus(),
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getCoupon() != null ? order.getCoupon().getCode() : null,
                order.getTotalAmount(),
                itemDtos
        );
    }

    private OrderResponse mapToOrderResponseWithProductDetails(Order order) {
        List<OrderItemDto> itemDtos = order.getItems()
                .stream()
                .map(this::mapToOrderItemDtoWithProductDetails)
                .toList();

        List<OrderStatusHistoryDto> historyDtos = order.getStatusHistory().stream()
                .map(this::toDto)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUsername(),
                order.getOrderDateTime(),
                order.getStatus(),
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getCoupon() != null ? order.getCoupon().getCode() : null,
                order.getTotalAmount(),
                itemDtos,
                historyDtos
        );
    }

    private OrderItemDto mapToOrderItemDtoWithProductDetails(OrderItems item) {
        ProductDto product = fetchProduct(item.getProductId());
        String primaryImageId = product.getImages().stream()
                .filter(ProductImageDto::isPrimary)
                .findFirst()
                .or(() -> product.getImages().stream().findFirst())
                .map(ProductImageDto::getId)
                .orElse(null);

        return new OrderItemDto(
                item.getId(),
                item.getProductId(),
                product.getProductName(),
                primaryImageId,
                item.getQuantity(),
                item.getPrice(),
                item.getPrice().multiply(item.getQuantity())
        );
    }

    @Override
    public List<OrderResponse> findAll() {
        return this.orderRepository.findAll().stream()
                .map(this::mapToOrderResponseWithProductDetails)
                .toList();
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(String id, OrderStatus status, String remarks) {
        Order order = this.orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with id: " + id));
        order.setStatus(status);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setStatus(status);
        history.setRemarks(remarks);
        history.setChangedBy(getUsername());
        order.getStatusHistory().add(history);

        order = this.orderRepository.saveAndFlush(order);
        return mapToOrderResponse(order);
    }

    @Override
    @Transactional
    public void delete(String id) {
        this.orderRepository.deleteById(id);
    }

    @Transactional
    @Override
    public List<OrderResponse> findByCustomerName() {
        log.info("Inside @OrderServiceImpl class @findByCustomerName method");
        String username = this.getUsername();
        List<Order> orders = this.orderRepository.findByUsername(username);
        return orders.stream()
                .map(this::mapToOrderResponseWithProductDetails)
                .toList();
    }

    @Override
    @Transactional
    public void recordPaymentResult(String orderId, String paymentStatus, String transactionId) {
        log.info("Inside @class OrderServiceImpl @method recordPaymentResult @param orderId: {}, paymentStatus: {}", orderId, paymentStatus);
        Order order = this.orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with id: " + orderId));

        // Deliberately does NOT touch order.status - fulfillment status and payment outcome are
        // separate concerns now that payment-service is the source of truth for payments. This
        // just leaves a note in the order's own timeline so admins/customers see it without
        // needing to cross-reference payment-service directly.
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setStatus(order.getStatus());
        history.setRemarks("Payment " + paymentStatus + " (transaction " + transactionId + ")");
        history.setChangedBy("payment-service");
        order.getStatusHistory().add(history);

        orderRepository.saveAndFlush(order);
    }

    private OrderStatusHistoryDto toDto(OrderStatusHistory entry) {
        OrderStatusHistoryDto dto = new OrderStatusHistoryDto();
        dto.setId(entry.getId());
        dto.setStatus(entry.getStatus());
        dto.setRemarks(entry.getRemarks());
        dto.setChangedBy(entry.getChangedBy());
        dto.setCreatedAt(entry.getCreatedAt());
        return dto;
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "decrementStockFallback")
    public ResponseEntity<Boolean> safeDecrementStock(String productId, BigDecimal qty) {
        return productController.decrementStock(productId, qty, null);
    }

    private ResponseEntity<Boolean> decrementStockFallback(String productId, BigDecimal qty, Throwable t) {
        log.error("Product service unavailable, aborting checkout for {}: {}", productId, t.getMessage());
        return ResponseEntity.ok(false);
    }
}