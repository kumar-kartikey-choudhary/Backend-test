package com.pratikdairy.order.controller;

import com.pratikdairy.order.dto.CouponDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ResponseBody
@FeignClient(name = "PRATIK-DAIRY-ORDER", path = "coupons", primary = false)
public interface CouponController {

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    ResponseEntity<CouponDto> create(@RequestBody CouponDto couponDto);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping
    ResponseEntity<List<CouponDto>> findAll();

    // Any signed-in customer can look up a code to see what it does before checkout - actually
    // applying it (and enforcing validity/usage limits) happens in OrderController.create().
    @GetMapping(path = "{code}")
    ResponseEntity<CouponDto> find(@PathVariable(name = "code") String code);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PatchMapping(path = "{id}")
    ResponseEntity<CouponDto> update(@PathVariable(name = "id") String id, @RequestBody CouponDto couponDto);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping(path = "{id}")
    void delete(@PathVariable(name = "id") String id);
}