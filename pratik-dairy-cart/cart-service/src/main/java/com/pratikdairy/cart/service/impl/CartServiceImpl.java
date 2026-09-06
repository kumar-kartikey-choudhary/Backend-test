package com.pratikdairy.cart.service.impl;

import com.pratikdairy.cart.dto.AddToCart;
import com.pratikdairy.cart.dto.CartItemDto;
import com.pratikdairy.cart.entity.CartItem;
import com.pratikdairy.cart.repository.CartItemRepository;
import com.pratikdairy.cart.service.CartService;
import com.pratikdairy.cart.util.WeightPricing;
import com.pratikdairy.product.controller.ProductController;
import com.pratikdairy.product.dto.ProductDto;
import com.pratikdairy.product.dto.ProductImageDto;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductController controller;

    @Autowired
    public CartServiceImpl(CartItemRepository cartItemRepository,
                           ProductController controller) {
        this.cartItemRepository = cartItemRepository;
        this.controller = controller;
    }

    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return auth.getName();
    }

    private ProductDto fetchProduct(String productId) {
        ResponseEntity<ProductDto> response = controller.find(productId);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("ProductNotFoundException: Could not fetch product details for id " + productId);
        }
        return response.getBody();
    }

    @Override
    @Transactional
    public CartItemDto addItemToCart(AddToCart request) {
        log.info("Adding product {} to cart", request.getProductId());
        String username = this.getUsername();
        ProductDto productDto = fetchProduct(request.getProductId());

        String defaultWeight = (productDto.getStockUnit() != null && !productDto.getStockUnit().isBlank())
                ? productDto.getStockUnit().trim().toLowerCase()
                : "1kg";
        String weight = (request.getWeight() == null || request.getWeight().isBlank())
                ? defaultWeight
                : request.getWeight().trim().toLowerCase();
        int qtyToAdd = (request.getQuantity() != null && request.getQuantity() > 0) ? request.getQuantity() : 1;

        // One cart line per product per user, period - looked up by productId alone (matching
        // how updateQuantity()/removeFromCart() address a line, by productId only, with no
        // weight parameter). Previously this looked up by (username, productId, weight), which
        // let the SAME product end up as two separate rows if the customer picked a different
        // weight the second time - and then updateQuantity()/removeFromCart(), which only know
        // productId, would hit CartItemRepository's singular-result finder against 2 matching
        // rows and throw IncorrectResultSizeDataAccessException. Changing weight on an existing
        // line now updates that line in place instead of creating a second one.
        CartItem existingItem = cartItemRepository.findByUsernameAndProductId(username, productDto.getId());

        int newLineQuantity;
        String finalWeight;
        if (existingItem == null) {
            newLineQuantity = qtyToAdd;
            finalWeight = weight;
        } else if (existingItem.getWeight() != null && existingItem.getWeight().equalsIgnoreCase(weight)) {
            // Same weight as what's already in the cart - just add to the existing count.
            newLineQuantity = existingItem.getQuantity() + qtyToAdd;
            finalWeight = weight;
        } else {
            // Different weight selected for a product already in the cart - summing package
            // counts across two different weights doesn't mean anything physically, so this
            // resets the line to the newly requested weight/quantity rather than adding them.
            newLineQuantity = qtyToAdd;
            finalWeight = weight;
        }

        BigDecimal stockNeeded = WeightPricing.stockToConsume(finalWeight, productDto.getStockUnit(), newLineQuantity);
        if (productDto.getStockQuantity() == null || productDto.getStockQuantity().compareTo(stockNeeded) < 0) {
            return null;
        }

        CartItem savedItem;
        if (existingItem != null) {
            existingItem.setQuantity(newLineQuantity);
            existingItem.setWeight(finalWeight);
            savedItem = cartItemRepository.saveAndFlush(existingItem);
        } else {
            CartItem cartItem = new CartItem();
            cartItem.setUsername(username);
            cartItem.setProductId(productDto.getId());
            cartItem.setCreatedBy(username);
            cartItem.setCreatedAt(ZonedDateTime.now());
            cartItem.setQuantity(newLineQuantity);
            cartItem.setWeight(finalWeight);
            savedItem = cartItemRepository.saveAndFlush(cartItem);
        }

        return toDto(savedItem, productDto);
    }

    @Override
    @Transactional
    public CartItemDto updateQuantity(String productId, int quantity) {
        String username = this.getUsername();

        if (quantity <= 0) {
            cartItemRepository.deleteByUsernameAndProductId(username, productId);
            return null;
        }

        CartItem cartItem = cartItemRepository.findByUsernameAndProductId(username, productId);
        if (cartItem == null) {
            return null;
        }

        ProductDto productDto = fetchProduct(productId);

        // Was previously unchecked here (only checked on add) - a customer could bump quantity
        // up past available stock and only find out at checkout. Same check as addItemToCart,
        // using the line's existing weight selection.
        BigDecimal stockNeeded = WeightPricing.stockToConsume(cartItem.getWeight(), productDto.getStockUnit(), quantity);
        if (productDto.getStockQuantity() == null || productDto.getStockQuantity().compareTo(stockNeeded) < 0) {
            return null;
        }

        cartItem.setQuantity(quantity);
        cartItem = cartItemRepository.saveAndFlush(cartItem);

        return toDto(cartItem, productDto);
    }

    @Transactional
    @Override
    public List<CartItemDto> getCart() {
        String username = this.getUsername();
        return cartItemRepository.findByUsername(username).stream()
                .map(item -> {
                    ProductDto productDto = fetchProduct(item.getProductId());
                    return toDto(item, productDto);
                })
                .toList();
    }

    private CartItemDto toDto(CartItem item, ProductDto productDto) {
        CartItemDto dto = new CartItemDto();
        dto.setId(item.getId());
        dto.setUsername(item.getUsername());
        dto.setProductId(item.getProductId());
        dto.setQuantity(item.getQuantity());
        dto.setProductName(productDto.getProductName());
        dto.setUnit(productDto.getStockUnit());
        dto.setWeight(item.getWeight());

        // ProductDto no longer carries raw image bytes (getImageData() was removed when images
        // became a one-to-many ProductImageDto list) - resolve the primary image's id instead,
        // same pattern order-service's OrderItemDto already uses.
        String primaryImageId = productDto.getImages() == null ? null : productDto.getImages().stream()
                .filter(ProductImageDto::isPrimary)
                .findFirst()
                .or(() -> productDto.getImages().stream().findFirst())
                .map(ProductImageDto::getId)
                .orElse(null);
        dto.setProductImageId(primaryImageId);

        // Price per unit based on selected weight vs base unit
        BigDecimal pricePerUnit = WeightPricing.priceFor(productDto.getPrice(), item.getWeight(), productDto.getStockUnit());
        dto.setPricePerUnit(pricePerUnit);

        // Subtotal = Unit Price * Quantity
        BigDecimal subtotal = pricePerUnit.multiply(BigDecimal.valueOf(item.getQuantity()));
        dto.setSubtotal(subtotal);

        // The actual amount to take out of Product.stockQuantity - order-service must decrement
        // by THIS, not by `quantity` (which is a package count, not a stock-unit amount).
        dto.setStockToConsume(WeightPricing.stockToConsume(item.getWeight(), productDto.getStockUnit(), item.getQuantity()));

        return dto;
    }

    @Transactional
    @Override
    public void clearCart() {
        cartItemRepository.deleteByUsername(this.getUsername());
    }

    @Transactional
    @Override
    public boolean deleteItemFromCart(String productId) {
        String username = this.getUsername();
        if (productId != null && !productId.isBlank()) {
            cartItemRepository.deleteByUsernameAndProductId(username, productId);
            return true;
        }
        return false;
    }
}