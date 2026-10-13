package com.pratikdairy.order.controller;


import com.pratikdairy.order.dto.DeliveryAddressDto;
import com.pratikdairy.order.dto.OrderResponse;
import com.pratikdairy.order.enums.OrderStatus;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ResponseBody
@FeignClient(name = "PRATIK-DAIRY-ORDER", path = "orders", primary = false)
public interface OrderController {


    // couponCode is optional - checkout with no code just skips the discount step.
    // deliveryAddress is required for new orders (validated in the service); it is a body, not
    // query params, because it has too many fields to be readable as a query string.
    @PostMapping(path = "create")
    ResponseEntity<OrderResponse> create(@RequestParam(name = "couponCode", required = false) String couponCode,
                                         @RequestBody(required = false) DeliveryAddressDto deliveryAddress);

    // Lets a customer cancel their own order while it hasn't shipped yet (PROCESSING/CONFIRMED).
    // Releases the stock that was reserved at checkout.
    @PatchMapping(path = "{id}/cancel")
    ResponseEntity<OrderResponse> cancel(@PathVariable("id") String id);

    @GetMapping(path = "/findByCustomer")
    ResponseEntity<List<OrderResponse>> findByCustomerName();

    // One order, for its owner or an admin. payment-service uses it to price a payment from the
    // order itself instead of trusting an amount sent by the browser.
    @GetMapping(path = "{id}")
    ResponseEntity<OrderResponse> findById(@PathVariable("id") String id);

    // The same order with product names and images and the status history - what an invoice needs. Its owner
    // or an admin only.
    @GetMapping(path = "{id}/detail")
    ResponseEntity<OrderResponse> findDetail(@PathVariable("id") String id);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping(path = "admin/findAll")
    ResponseEntity<List<OrderResponse>> findAll();


    // remarks is optional - lets an admin note *why* a status changed (e.g. "customer requested
    // reschedule"), recorded alongside the transition in ORDER_STATUS_HISTORY.
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping(path = "admin/updateStatus/{id}")
    ResponseEntity<OrderResponse> updateStatus(
            @PathVariable("id") String id,
            @RequestParam("status") OrderStatus status,
            @RequestParam(name = "remarks", required = false) String remarks
    );

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping(path = "admin/delete/{id}")
    void delete(@PathVariable(name = "id") String id);

    // Called by payment-service once a payment attempt for this order resolves (see
    // pratik-dairy-payment's OrderStatusClient). Appends an entry to ORDER_STATUS_HISTORY -
    // does NOT change order.status itself, since fulfillment status (PROCESSING/CONFIRMED/
    // SHIPPED/...) and payment outcome are separate concerns. The actual payment record
    // (transaction id, gateway details) lives in payment-service, not here - this is just an
    // audit-trail note so the order's timeline shows "payment succeeded/failed" inline.
    // Service-to-service: the order as payment-service needs it (status, total, owner) - read with the
    // internal key, so it works from places with no customer token (the Razorpay webhook, scheduled jobs).
    @PreAuthorize("isAuthenticated()")
    @GetMapping(path = "internal/{orderId}/summary")
    ResponseEntity<OrderResponse> internalSummary(@PathVariable("orderId") String orderId);

    @PreAuthorize("isAuthenticated()")
    @PatchMapping(path = "internal/{orderId}/payment-result")
    ResponseEntity<Void> recordPaymentResult(
            @PathVariable("orderId") String orderId,
            @RequestParam("status") String status,
            @RequestParam("transactionId") String transactionId
    );
}