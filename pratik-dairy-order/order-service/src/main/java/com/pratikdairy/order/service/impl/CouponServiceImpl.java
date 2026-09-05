package com.pratikdairy.order.service.impl;

import com.pratikdairy.order.dto.CouponDto;
import com.pratikdairy.order.model.Coupon;
import com.pratikdairy.order.repository.CouponRepository;
import com.pratikdairy.order.service.CouponService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;

    @Autowired
    public CouponServiceImpl(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Override
    @Transactional
    public CouponDto create(CouponDto couponDto) {
        log.info("Inside @class CouponServiceImpl @method create @param couponDto: {}", couponDto);
        if (couponDto == null || couponDto.getCode() == null || couponDto.getCode().isBlank()) {
            throw new IllegalArgumentException("Coupon code is required");
        }
        if (couponDto.getDiscountType() == null || couponDto.getDiscountValue() == null) {
            throw new IllegalArgumentException("discountType and discountValue are required");
        }
        if (couponRepository.existsByCodeIgnoreCase(couponDto.getCode())) {
            throw new IllegalArgumentException("Coupon code '" + couponDto.getCode() + "' already exists");
        }

        Coupon coupon = new Coupon();
        coupon.setCode(couponDto.getCode().trim().toUpperCase());
        coupon.setDiscountType(couponDto.getDiscountType());
        coupon.setDiscountValue(couponDto.getDiscountValue());
        coupon.setMinOrderValue(couponDto.getMinOrderValue());
        coupon.setMaxDiscount(couponDto.getMaxDiscount());
        coupon.setValidFrom(couponDto.getValidFrom());
        coupon.setValidTo(couponDto.getValidTo());
        coupon.setUsageLimit(couponDto.getUsageLimit());
        coupon.setActive(true);

        return toDto(couponRepository.saveAndFlush(coupon));
    }

    @Override
    public List<CouponDto> findAll() {
        return couponRepository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    public CouponDto find(String code) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new EntityNotFoundException("Coupon not found with code: " + code));
        return toDto(coupon);
    }

    @Override
    @Transactional
    public CouponDto update(CouponDto couponDto, String id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Coupon not found with id: " + id));

        if (couponDto.getDiscountType() != null) {
            coupon.setDiscountType(couponDto.getDiscountType());
        }
        if (couponDto.getDiscountValue() != null) {
            coupon.setDiscountValue(couponDto.getDiscountValue());
        }
        coupon.setMinOrderValue(couponDto.getMinOrderValue());
        coupon.setMaxDiscount(couponDto.getMaxDiscount());
        coupon.setValidFrom(couponDto.getValidFrom());
        coupon.setValidTo(couponDto.getValidTo());
        coupon.setUsageLimit(couponDto.getUsageLimit());
        coupon.setActive(couponDto.isActive());

        return toDto(couponRepository.saveAndFlush(coupon));
    }

    @Override
    @Transactional
    public void delete(String id) {
        if (!couponRepository.existsById(id)) {
            throw new EntityNotFoundException("Coupon not found with id: " + id);
        }
        couponRepository.deleteById(id);
    }

    private CouponDto toDto(Coupon coupon) {
        CouponDto dto = new CouponDto();
        dto.setId(coupon.getId());
        dto.setCode(coupon.getCode());
        dto.setDiscountType(coupon.getDiscountType());
        dto.setDiscountValue(coupon.getDiscountValue());
        dto.setMinOrderValue(coupon.getMinOrderValue());
        dto.setMaxDiscount(coupon.getMaxDiscount());
        dto.setValidFrom(coupon.getValidFrom());
        dto.setValidTo(coupon.getValidTo());
        dto.setUsageLimit(coupon.getUsageLimit());
        dto.setUsedCount(coupon.getUsedCount());
        dto.setActive(coupon.isActive());
        dto.setCreatedAt(coupon.getCreatedAt());
        dto.setModifiedAt(coupon.getModifiedAt());
        return dto;
    }
}