package com.pratikdairy.order.service;

import com.pratikdairy.order.dto.CouponDto;

import java.util.List;

public interface CouponService {

    CouponDto create(CouponDto couponDto);

    List<CouponDto> findAll();

    CouponDto find(String code);

    CouponDto update(CouponDto couponDto, String id);

    void delete(String id);
}