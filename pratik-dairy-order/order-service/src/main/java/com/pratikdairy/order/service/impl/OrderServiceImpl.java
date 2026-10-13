package com.pratikdairy.order.service.impl;

import com.pratikdairy.parent.exception.BadRequestException;
import com.pratikdairy.parent.exception.ExternalServiceException;
import com.pratikdairy.parent.exception.InsufficientStockException;
import com.pratikdairy.parent.exception.InvalidStateException;
import com.pratikdairy.parent.exception.InvalidCouponException;
import com.pratikdairy.parent.exception.ResourceNotFoundException;
import com.pratikdairy.parent.exception.UnauthorizedException;
import com.pratikdairy.parent.utility.WeightPricing;
import com.pratikdairy.cart.controller.CartController;
import com.pratikdairy.cart.dto.CartItemDto;
import com.pratikdairy.order.client.PaymentClient;
import com.pratikdairy.order.client.ProductStockGateway;
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
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import feign.FeignException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CouponRepository couponRepository;
    private final CartController cartController;
    private final ProductController productController;
    private final ProductStockGateway stockGateway;
    private final PaymentClient paymentClient;

    // Customers with a checkout running right now - see create().
    private final Set<String> checkoutsInProgress = ConcurrentHashMap.newKeySet();

    @Autowired
    public OrderServiceImpl(OrderRepository orderRepository,
                            CouponRepository couponRepository,
                            CartController cartController,
                            ProductController productController,
                            ProductStockGateway stockGateway,
                            PaymentClient paymentClient) {
        this.orderRepository = orderRepository;
        this.couponRepository = couponRepository;
        this.cartController = cartController;
        this.productController = productController;
        this.stockGateway = stockGateway;
        this.paymentClient = paymentClient;
    }

    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        return auth.getName();
    }

    private ProductDto fetchProduct(String productId) {
        ResponseEntity<ProductDto> response;
        try {
            // The light lookup: names, prices, units and image IDS - not the image bytes, which this
            // service never needs (it would otherwise pull every picture over HTTP for every order line).
            response = productController.findLight(productId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Product", productId);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ExternalServiceException("Could not fetch product details for id " + productId);
        }
        return response.getBody();
    }

    @Override
    @Transactional
    public OrderResponse create(String couponCode, DeliveryAddressDto deliveryAddressDto) {
        log.info("Inside @class OrderServiceImpl @method create @param couponCode: {}", couponCode);
        String username = getUsername();
        // Checked before any stock is reserved, so a missing address can't leave stock decremented.
        DeliveryAddress deliveryAddress = toEntity(deliveryAddressDto);

        // One checkout per customer at a time. A fast double-click sends two requests that would both
        // read the same cart and both reserve stock, giving two orders. The second is refused instead
        // (the first clears the cart before this lock is released, so a retry afterwards finds it empty).
        if (!checkoutsInProgress.add(username)) {
            throw new InvalidStateException("Your order is already being placed - please wait a moment.");
        }
        try {
            return placeOrder(username, couponCode, deliveryAddress);
        } finally {
            checkoutsInProgress.remove(username);
        }
    }

    private OrderResponse placeOrder(String username, String couponCode, DeliveryAddress deliveryAddress) {
        List<CartItemDto> cartItems = cartController.getCart().getBody();

        if (cartItems == null || cartItems.isEmpty()) {
            throw new BadRequestException("Your cart is empty");
        }

        // A cart line added before pack sizes were validated can still hold a weight that is no longer
        // allowed (the price follows the weight). Re-check every line now, before anything is reserved.
        for (CartItemDto item : cartItems) {
            try {
                WeightPricing.requireValidSelection(item.getWeight(), item.getUnit());
            } catch (BadRequestException e) {
                throw new BadRequestException("The pack size of '" + item.getProductName()
                        + "' in your cart is no longer available. Please remove it and add it again.");
            }
        }

        // Trust the price/quantity the cart already computed - no need to re-fetch every
        // product here, only to decrement stock below.
        BigDecimal subtotal = cartItems.stream()
                .map(CartItemDto::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // The coupon is checked BEFORE any stock is reserved. Stock lives in another service and is
        // committed there immediately, so anything that fails after reserving it has to be undone by
        // hand (see below) - failing early on a bad coupon avoids needing to. (The coupon's own
        // usedCount bump is in this transaction, so it rolls back with it.)
        Coupon coupon = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (couponCode != null && !couponCode.isBlank()) {
            coupon = validateAndApplyCoupon(couponCode, subtotal, username);
            discountAmount = computeDiscount(coupon, subtotal);
        }

        BigDecimal totalAmount = subtotal.subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        List<CartItemDto> reserved = new ArrayList<>();
        try {
            for (CartItemDto item : cartItems) {
                // orderId is null here - the order doesn't exist yet at decrement time, so this
                // sale's ledger entry won't carry a reference id. Acceptable trade-off: the
                // alternative (create the order first, then decrement) risks leaving a saved order
                // with items that can't actually be fulfilled.
                //
                // Uses item.getStockToConsume(), NOT item.getQuantity() - quantity is a package
                // count (e.g. "3" packs of 250g), while Product.stockQuantity is denominated in the
                // product's own stockUnit (e.g. kg). Passing the raw package count here would
                // decrement stock by the wrong amount (and by the wrong unit entirely) any time the
                // selected weight differs from the product's base stockUnit.
                if (!stockGateway.decrement(item.getProductId(), item.getStockToConsume())) {
                    throw new InsufficientStockException(item.getProductId(),
                            "Sorry, '" + item.getProductName() + "' is unavailable or no longer has enough stock. "
                                    + "Please reduce the quantity or remove it from your cart.");
                }
                reserved.add(item);
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
            order.setDeliveryAddress(deliveryAddress);

            List<OrderItems> orderItems = cartItems.stream()
                    .map(item -> new OrderItems(
                            item.getProductId(),
                            BigDecimal.valueOf(item.getQuantity()),
                            item.getPricePerUnit(),
                            item.getWeight(),
                            item.getUnit(),
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
        } catch (RuntimeException e) {
            // Whatever failed - a later item out of stock, product-service going away, the save, the
            // cart clear - the order will not exist, so put back every line that was already taken.
            releaseReserved(reserved);
            throw e;
        }
    }

    // Best effort: a failure to put one line back must not mask the original error or stop the rest.
    private void releaseReserved(List<CartItemDto> reserved) {
        for (CartItemDto done : reserved) {
            try {
                productController.restoreStock(done.getProductId(), done.getStockToConsume(), null);
            } catch (RuntimeException restoreFailure) {
                log.error("Could not restore {} of product {} after a failed checkout - stock needs a manual correction: {}",
                        done.getStockToConsume(), done.getProductId(), restoreFailure.getMessage());
            }
        }
    }

    // Validates the coupon is usable against this subtotal (exists, active, in-window, under its
    // usage cap, subtotal meets the minimum) and bumps its usedCount. Throws on any failure so
    // checkout aborts cleanly rather than silently applying (or silently ignoring) a bad code.
    private Coupon validateAndApplyCoupon(String couponCode, BigDecimal subtotal, String username) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(couponCode)
                .orElseThrow(() -> new InvalidCouponException("Coupon code not found: " + couponCode));

        if (!coupon.isActive()) {
            throw new InvalidCouponException("Coupon '" + couponCode + "' is no longer active");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            throw new InvalidCouponException("Coupon '" + couponCode + "' is not valid yet");
        }
        if (coupon.getValidTo() != null && now.isAfter(coupon.getValidTo())) {
            throw new InvalidCouponException("Coupon '" + couponCode + "' has expired");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new InvalidCouponException("Coupon '" + couponCode + "' has reached its usage limit");
        }
        if (coupon.getMinOrderValue() != null && subtotal.compareTo(coupon.getMinOrderValue()) < 0) {
            throw new InvalidCouponException("Order subtotal does not meet the minimum for coupon '" + couponCode + "'");
        }

        if (coupon.getUsageLimitPerUser() != null) {
            long alreadyUsed = orderRepository.countByUsernameAndCouponAndStatusNot(username, coupon, OrderStatus.CANCELLED);
            if (alreadyUsed >= coupon.getUsageLimitPerUser()) {
                throw new InvalidCouponException("You have already used coupon '" + couponCode + "'"
                        + (coupon.getUsageLimitPerUser() == 1 ? "" : " " + coupon.getUsageLimitPerUser() + " times"));
            }
        }

        // Counted by a single conditional UPDATE, not read-then-write: two checkouts racing for the last
        // redemption can't both get it. Rolls back with the order if checkout fails later.
        if (couponRepository.tryRedeem(coupon.getId()) == 0) {
            throw new InvalidCouponException("Coupon '" + couponCode + "' has reached its usage limit");
        }
        return coupon;
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
                        item.getPrice().multiply(item.getQuantity()),
                        item.getWeight()
                ))
                .toList();

        OrderResponse response = new OrderResponse(
                order.getId(),
                order.getUsername(),
                order.getStatus(),
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getCoupon() != null ? order.getCoupon().getCode() : null,
                order.getTotalAmount(),
                itemDtos
        );
        response.setDeliveryAddress(toDto(order.getDeliveryAddress()));
        return response;
    }

    private OrderResponse mapToOrderResponseWithProductDetails(Order order) {
        List<OrderItemDto> itemDtos = order.getItems()
                .stream()
                .map(this::mapToOrderItemDtoWithProductDetails)
                .toList();

        List<OrderStatusHistoryDto> historyDtos = order.getStatusHistory().stream()
                .map(this::toDto)
                .toList();

        OrderResponse response = new OrderResponse(
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
        response.setDeliveryAddress(toDto(order.getDeliveryAddress()));
        return response;
    }

    private OrderItemDto mapToOrderItemDtoWithProductDetails(OrderItems item) {
        ProductDto product;
        try {
            product = fetchProduct(item.getProductId());
        } catch (ResourceNotFoundException e) {
            // The product was deleted after this order was placed. The order must stay readable
            // (history, cancel, admin views) - one missing product can't be allowed to break it.
            return new OrderItemDto(
                    item.getId(),
                    item.getProductId(),
                    "Product no longer available",
                    null,
                    item.getQuantity(),
                    item.getPrice(),
                    item.getPrice().multiply(item.getQuantity()),
                    item.getWeight()
            );
        }
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
                item.getPrice().multiply(item.getQuantity()),
                item.getWeight()
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
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        if (order.getStatus() == status) {
            // Already there - nothing to record, and it keeps "cancel twice" from restoring stock twice.
            return mapToOrderResponse(order);
        }
        // DELIVERED and CANCELLED are final. Without this a cancelled order (stock already put back,
        // payment refunded) could be flipped to CONFIRMED again, or a delivered one cancelled.
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new InvalidStateException(
                    "A " + order.getStatus() + " order can't be moved to " + status);
        }
        // Stock reserved at checkout goes back on the shelf, and any online payment is refunded, when
        // an order is cancelled. Reaching here means it wasn't already CANCELLED, so it happens once.
        if (status == OrderStatus.CANCELLED) {
            releaseStock(order);
            releaseCoupon(order);
            afterCommit("refund the payment", () -> paymentClient.refundCancelledOrder(id), id);
        }
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
    public OrderResponse findById(String id) {
        Order order = this.orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        if (!isAdmin() && !order.getUsername().equals(getUsername())) {
            throw new AccessDeniedException("This order does not belong to the current user");
        }
        return mapToOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse findDetail(String id) {
        Order order = this.orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        if (!isAdmin() && !order.getUsername().equals(getUsername())) {
            throw new AccessDeniedException("This order does not belong to the current user");
        }
        return mapToOrderResponseWithProductDetails(order);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    // Runs a call to payment-service only once this transaction has committed. After a cancellation
    // payment-service re-reads the order from us and refuses unless it is CANCELLED, which isn't visible
    // to it before then. A failure here must not undo the cancellation (stock is already back): it is
    // logged, and the payment stays as it was for an admin to settle by hand.
    private void afterCommit(String what, Runnable call, String orderId) {
        Runnable guarded = () -> {
            try {
                call.run();
            } catch (RuntimeException e) {
                log.error("Order {} was cancelled but we could not {} automatically - needs a manual look: {}",
                        orderId, what, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    guarded.run();
                }
            });
        } else {
            guarded.run();
        }
    }

    // A cancelled order no longer uses its coupon, so the redemption goes back to the pool.
    private void releaseCoupon(Order order) {
        if (order.getCoupon() != null) {
            couponRepository.giveBack(order.getCoupon().getId());
        }
    }

    @Override
    public List<String> findUnpaidCandidateIds(LocalDateTime olderThan) {
        return orderRepository.findByStatusAndOrderDateTimeBefore(OrderStatus.CONFIRMED, olderThan).stream()
                .map(Order::getId)
                .toList();
    }

    @Override
    @Transactional
    public boolean expireUnpaid(String orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        // Re-checked inside the transaction: it may have been paid, shipped or cancelled since it was listed.
        if (order == null || order.getStatus() != OrderStatus.CONFIRMED) {
            return false;
        }
        log.info("Expiring order {}: payment was not completed in time", orderId);
        releaseStock(order);
        releaseCoupon(order);
        order.setStatus(OrderStatus.CANCELLED);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setStatus(OrderStatus.CANCELLED);
        history.setRemarks("Cancelled automatically: payment was not completed in time");
        history.setChangedBy("system");
        order.getStatusHistory().add(history);
        orderRepository.saveAndFlush(order);

        // Nothing was paid (the sweep checked), but an attempt may still be open - close it so it can't
        // be completed against a cancelled order.
        afterCommit("close the open payment attempt", () -> paymentClient.closeOpenAttempts(orderId), orderId);
        return true;
    }

    @Override
    public OrderResponse findForService(String id) {
        return mapToOrderResponse(orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id)));
    }

    @Override
    @Transactional
    public OrderResponse cancel(String id) {
        log.info("Inside @class OrderServiceImpl @method cancel @param id: {}", id);
        String username = getUsername();
        Order order = this.orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        if (!order.getUsername().equals(username)) {
            throw new AccessDeniedException("This order does not belong to the current user");
        }
        if (order.getStatus() != OrderStatus.PROCESSING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidStateException(
                    "Only an order that has not shipped yet can be cancelled - this one is " + order.getStatus());
        }

        releaseStock(order);
        releaseCoupon(order);
        afterCommit("refund the payment", () -> paymentClient.refundCancelledOrder(id), id);
        order.setStatus(OrderStatus.CANCELLED);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setStatus(OrderStatus.CANCELLED);
        history.setRemarks("Cancelled by customer");
        history.setChangedBy(username);
        order.getStatusHistory().add(history);

        return mapToOrderResponseWithProductDetails(this.orderRepository.saveAndFlush(order));
    }

    // Puts the stock reserved at checkout back. Order lines store a package count + weight, while
    // Product.stockQuantity is in the product's own stockUnit, so the amount to restore is
    // recomputed the same way the cart computed stockToConsume when the stock was taken.
    private void releaseStock(Order order) {
        for (OrderItems item : order.getItems()) {
            try {
                // The unit the stock was taken in when this was bought; lines placed before that was recorded
                // fall back to the product's current unit.
                String unit = item.getUnit() != null ? item.getUnit() : fetchProduct(item.getProductId()).getStockUnit();
                BigDecimal amount = WeightPricing.multiplierFor(item.getWeight(), unit)
                        .multiply(item.getQuantity());
                productController.restoreStock(item.getProductId(), amount, order.getId());
            } catch (ResourceNotFoundException e) {
                // Product was deleted since the order was placed - nothing on the shelf to restore.
                log.warn("Skipping stock release for deleted product {} on order {}", item.getProductId(), order.getId());
            }
        }
    }

    private DeliveryAddress toEntity(DeliveryAddressDto dto) {
        if (dto == null || isBlank(dto.getLine1()) || isBlank(dto.getCity())
                || isBlank(dto.getState()) || isBlank(dto.getPincode())) {
            throw new BadRequestException("A delivery address (address line, city, state and pincode) is required");
        }
        return new DeliveryAddress(
                trimToNull(dto.getLabel()), dto.getLine1().trim(), trimToNull(dto.getLine2()),
                dto.getCity().trim(), dto.getState().trim(), dto.getPincode().trim(),
                trimToNull(dto.getLandmark()), trimToNull(dto.getContactPhone()));
    }

    private DeliveryAddressDto toDto(DeliveryAddress address) {
        if (address == null) {
            return null;
        }
        DeliveryAddressDto dto = new DeliveryAddressDto();
        dto.setLabel(address.getLabel());
        dto.setLine1(address.getLine1());
        dto.setLine2(address.getLine2());
        dto.setCity(address.getCity());
        dto.setState(address.getState());
        dto.setPincode(address.getPincode());
        dto.setLandmark(address.getLandmark());
        dto.setContactPhone(address.getContactPhone());
        return dto;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
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
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

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
}
