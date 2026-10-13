package com.pratikdairy.product.service.impl;

import com.pratikdairy.parent.exception.ResourceNotFoundException;
import com.pratikdairy.parent.exception.UnauthorizedException;
import com.pratikdairy.product.dto.ProductDto;
import com.pratikdairy.product.model.WishlistItem;
import com.pratikdairy.product.repository.ProductRepository;
import com.pratikdairy.product.repository.WishlistItemRepository;
import com.pratikdairy.product.service.ProductService;
import com.pratikdairy.product.service.WishlistService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WishlistServiceImpl implements WishlistService {

    private final WishlistItemRepository wishlist;
    private final ProductRepository products;
    private final ProductService productService;

    @Autowired
    public WishlistServiceImpl(WishlistItemRepository wishlist, ProductRepository products, ProductService productService) {
        this.wishlist = wishlist;
        this.products = products;
        this.productService = productService;
    }

    private String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new UnauthorizedException("User not authenticated");
        }
        return auth.getName();
    }

    @Override
    public List<ProductDto> list() {
        List<ProductDto> result = new ArrayList<>();
        for (WishlistItem item : wishlist.findByUsernameOrderByCreatedAtDesc(username())) {
            try {
                result.add(productService.findLight(item.getProductId()));
            } catch (ResourceNotFoundException deleted) {
                // The product has since been removed; it just doesn't show.
            }
        }
        return result;
    }

    @Override
    public List<String> ids() {
        return wishlist.findByUsernameOrderByCreatedAtDesc(username()).stream().map(WishlistItem::getProductId).toList();
    }

    @Override
    @Transactional
    public void add(String productId) {
        String username = username();
        if (!products.existsById(productId)) {
            throw new ResourceNotFoundException("Product", productId);
        }
        if (wishlist.existsByUsernameAndProductId(username, productId)) {
            return; // already saved
        }
        WishlistItem item = new WishlistItem();
        item.setUsername(username);
        item.setProductId(productId);
        wishlist.saveAndFlush(item);
    }

    @Override
    @Transactional
    public void remove(String productId) {
        wishlist.deleteByUsernameAndProductId(username(), productId);
    }
}
