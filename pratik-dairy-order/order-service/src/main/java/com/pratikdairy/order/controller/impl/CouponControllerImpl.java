package com.pratikdairy.order.controller.impl;

import com.pratikdairy.order.controller.CouponController;
import com.pratikdairy.order.dto.CouponDto;
import com.pratikdairy.order.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Primary
@RequestMapping("coupons")
public class CouponControllerImpl implements CouponController {

    private final CouponService couponService;

    @Autowired
    public CouponControllerImpl(CouponService couponService) {
        this.couponService = couponService;
    }

    @Override
    public ResponseEntity<CouponDto> create(CouponDto couponDto) {
        return new ResponseEntity<>(couponService.create(couponDto), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<List<CouponDto>> findAll() {
        return ResponseEntity.ok(couponService.findAll());
    }

    @Override
    public ResponseEntity<CouponDto> find(String code) {
        return ResponseEntity.ok(couponService.find(code));
    }

    @Override
    public ResponseEntity<CouponDto> update(String id, CouponDto couponDto) {
        return ResponseEntity.ok(couponService.update(couponDto, id));
    }

    @Override
    public void delete(String id) {
        couponService.delete(id);
    }
}