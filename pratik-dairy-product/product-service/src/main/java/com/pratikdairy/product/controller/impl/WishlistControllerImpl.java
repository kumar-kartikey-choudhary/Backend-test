package com.pratikdairy.product.controller.impl;

import com.pratikdairy.product.controller.WishlistController;
import com.pratikdairy.product.dto.ProductDto;
import com.pratikdairy.product.service.WishlistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("products")
public class WishlistControllerImpl implements WishlistController {

    private final WishlistService wishlistService;

    @Autowired
    public WishlistControllerImpl(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @Override
    public ResponseEntity<List<ProductDto>> list() {
        return ResponseEntity.ok(wishlistService.list());
    }

    @Override
    public ResponseEntity<List<String>> ids() {
        return ResponseEntity.ok(wishlistService.ids());
    }

    @Override
    public ResponseEntity<Void> add(String productId) {
        wishlistService.add(productId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> remove(String productId) {
        wishlistService.remove(productId);
        return ResponseEntity.ok().build();
    }
}
