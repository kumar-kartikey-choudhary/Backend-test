package com.pratikdairy.product.service;

import com.pratikdairy.product.dto.ProductDto;

import java.util.List;

public interface WishlistService {
    List<ProductDto> list();
    List<String> ids();
    void add(String productId);
    void remove(String productId);
}
