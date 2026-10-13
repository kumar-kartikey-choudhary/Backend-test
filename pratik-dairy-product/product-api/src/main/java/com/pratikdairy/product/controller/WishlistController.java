package com.pratikdairy.product.controller;

import com.pratikdairy.product.dto.ProductDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Every endpoint needs a login (the product service's default rule): a wishlist belongs to one customer.
public interface WishlistController {

    /** The signed-in customer's saved products, newest first (image ids but no image bytes). */
    @GetMapping(path = "wishlist")
    ResponseEntity<List<ProductDto>> list();

    /** Just the ids - cheap enough to ask on every page so the heart buttons show the right state. */
    @GetMapping(path = "wishlist/ids")
    ResponseEntity<List<String>> ids();

    /** Saves a product. Saving one that is already saved is fine. */
    @PostMapping(path = "wishlist/{productId}")
    ResponseEntity<Void> add(@PathVariable("productId") String productId);

    /** Removes a product. Removing one that isn't saved is fine. */
    @DeleteMapping(path = "wishlist/{productId}")
    ResponseEntity<Void> remove(@PathVariable("productId") String productId);
}
