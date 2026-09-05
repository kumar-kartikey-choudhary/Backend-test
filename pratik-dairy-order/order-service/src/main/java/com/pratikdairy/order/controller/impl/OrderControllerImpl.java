package com.pratikdairy.order.controller.impl;

import com.pratikdairy.order.controller.OrderController;
import com.pratikdairy.order.dto.OrderResponse;
import com.pratikdairy.order.enums.OrderStatus;
import com.pratikdairy.order.service.OrderService;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("orders")
@Primary
public class OrderControllerImpl implements OrderController {

    private final OrderService orderService;

    public OrderControllerImpl(OrderService orderService)
    {
        this.orderService = orderService;
    }


    @Override
    public ResponseEntity<OrderResponse> create(String couponCode) {
        return new ResponseEntity<>(this.orderService.create(couponCode), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<List<OrderResponse>> findByCustomerName() {
        return ResponseEntity.ok(this.orderService.findByCustomerName());
    }

    @Override
    public ResponseEntity<List<OrderResponse>> findAll() {
        return ResponseEntity.ok(this.orderService.findAll());
    }


    @Override
    public ResponseEntity<OrderResponse> updateStatus(String id, OrderStatus status, String remarks) {
        return ResponseEntity.ok(this.orderService.updateStatus(id, status, remarks));
    }

    @Override
    public void delete(String id) {
        this.orderService.delete(id);
    }

    @Override
    public ResponseEntity<Void> recordPaymentResult(String orderId, String status, String transactionId) {
        this.orderService.recordPaymentResult(orderId, status, transactionId);
        return ResponseEntity.ok().build();
    }
}