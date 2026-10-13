package com.pratikdairy.cart.service.impl;

import com.pratikdairy.parent.exception.BadRequestException;
import com.pratikdairy.parent.exception.ExternalServiceException;
import com.pratikdairy.parent.exception.InsufficientStockException;
import com.pratikdairy.parent.exception.ResourceNotFoundException;
import com.pratikdairy.parent.exception.UnauthorizedException;
import com.pratikdairy.cart.dto.AddToCart;
import com.pratikdairy.cart.dto.CartItemDto;
import com.pratikdairy.cart.entity.CartItem;
import com.pratikdairy.cart.repository.CartItemRepository;
import com.pratikdairy.cart.service.CartService;
import com.pratikdairy.parent.utility.WeightPricing;
import com.pratikdairy.product.controller.ProductController;
import com.pratikdairy.product.dto.ProductDto;
import com.pratikdairy.product.dto.ProductImageDto;
import jakarta.transaction.Transactional;
import feign.FeignException;
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
            throw new UnauthorizedException("User not authenticated");
        }
        return auth.getName();
    }

    private ProductDto fetchProduct(String productId) {
        ResponseEntity<ProductDto> response;
        try {
            response = controller.findLight(productId); // no image bytes: the cart shows an image id, not the picture
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Product", productId);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ExternalServiceException("Could not fetch product details for id " + productId);
        }
        return response.getBody();
    }

    /**
     * A cart line is one product at one pack size, so the same product can sit in the cart several
     * times - e.g. 2 x 250g and 1 x 1kg of the same sweet. Adding a pack size that is already there
     * adds to that line; a new pack size starts a new line.
     */
    @Override
    @Transactional
    public CartItemDto addItemToCart(AddToCart request) {
        log.info("Adding product {} to cart", request.getProductId());
        String username = this.getUsername();
        ProductDto productDto = fetchProduct(request.getProductId());
        if (!Boolean.TRUE.equals(productDto.getAvailable())) {
            throw new BadRequestException("'" + productDto.getProductName() + "' is currently unavailable.");
        }

        String defaultWeight = (productDto.getStockUnit() != null && !productDto.getStockUnit().isBlank())
                ? productDto.getStockUnit().trim().toLowerCase()
                : "1kg";
        String weight = (request.getWeight() == null || request.getWeight().isBlank())
                ? defaultWeight
                : request.getWeight().trim().toLowerCase();
        // The weight is client-supplied free text that drives price and stock - only known pack sizes pass.
        WeightPricing.requireValidSelection(weight, productDto.getStockUnit());
        int qtyToAdd = (request.getQuantity() != null && request.getQuantity() > 0) ? request.getQuantity() : 1;

        List<CartItem> lines = cartItemRepository.findByUsernameAndProductId(username, productDto.getId());
        CartItem existingItem = lines.stream().filter(line -> sameWeight(line, weight)).findFirst().orElse(null);

        int newLineQuantity = (existingItem == null ? 0 : existingItem.getQuantity()) + qtyToAdd;
        // Every line of this product draws on the same stock, so the check has to cover all of them.
        ensureEnoughStock(productDto, stockNeeded(productDto, lines, existingItem, weight, newLineQuantity));

        CartItem savedItem;
        if (existingItem != null) {
            existingItem.setQuantity(newLineQuantity);
            savedItem = cartItemRepository.saveAndFlush(existingItem);
        } else {
            CartItem cartItem = new CartItem();
            cartItem.setUsername(username);
            cartItem.setProductId(productDto.getId());
            cartItem.setCreatedBy(username);
            cartItem.setCreatedAt(ZonedDateTime.now());
            cartItem.setQuantity(newLineQuantity);
            cartItem.setWeight(weight);
            savedItem = cartItemRepository.saveAndFlush(cartItem);
        }

        return toDto(savedItem, productDto);
    }

    @Override
    @Transactional
    public CartItemDto updateQuantity(String productId, String weight, int quantity) {
        String username = this.getUsername();
        List<CartItem> lines = cartItemRepository.findByUsernameAndProductId(username, productId);
        CartItem cartItem = pickLine(lines, productId, weight);

        if (quantity <= 0) {
            cartItemRepository.delete(cartItem);
            return null;
        }

        ProductDto productDto = fetchProduct(productId);

        // Was previously unchecked here (only checked on add) - a customer could bump quantity
        // up past available stock and only find out at checkout. Same check as addItemToCart.
        ensureEnoughStock(productDto, stockNeeded(productDto, lines, cartItem, cartItem.getWeight(), quantity));

        cartItem.setQuantity(quantity);
        cartItem = cartItemRepository.saveAndFlush(cartItem);

        return toDto(cartItem, productDto);
    }

    @Transactional
    @Override
    public List<CartItemDto> getCart() {
        String username = this.getUsername();
        // A product is looked up once however many pack sizes of it are in the cart.
        java.util.Map<String, ProductDto> products = new java.util.HashMap<>();
        return cartItemRepository.findByUsername(username).stream()
                .sorted(java.util.Comparator
                        .comparing(CartItem::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(item -> item.getWeight() == null ? "" : item.getWeight()))
                .map(item -> toDto(item, products.computeIfAbsent(item.getProductId(), this::fetchProduct)))
                .toList();
    }

    private static boolean sameWeight(CartItem line, String weight) {
        return line.getWeight() != null && line.getWeight().equalsIgnoreCase(weight);
    }

    /**
     * Which line a request means. With a weight it is that pack size; without one it is only
     * unambiguous when the product has a single line.
     */
    private static CartItem pickLine(List<CartItem> lines, String productId, String weight) {
        if (weight != null && !weight.isBlank()) {
            return lines.stream().filter(line -> sameWeight(line, weight.trim())).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Cart item for product", productId));
        }
        if (lines.isEmpty()) {
            throw new ResourceNotFoundException("Cart item for product", productId);
        }
        if (lines.size() > 1) {
            throw new BadRequestException("This product is in your cart in more than one pack size - say which one.");
        }
        return lines.get(0);
    }

    /**
     * Stock this product would need if {@code changed} (the line being added to / edited, or null for a
     * new one) held {@code newQuantity} packs: that line, plus every other line of the same product.
     */
    private static BigDecimal stockNeeded(ProductDto product, List<CartItem> lines, CartItem changed,
                                          String weight, int newQuantity) {
        BigDecimal needed = WeightPricing.stockToConsume(weight, product.getStockUnit(), newQuantity);
        for (CartItem other : lines) {
            if (other != changed) {
                needed = needed.add(WeightPricing.stockToConsume(other.getWeight(), product.getStockUnit(), other.getQuantity()));
            }
        }
        return needed;
    }

    /**
     * Used to return null here, which the controller turned into an empty 200 - the UI then
     * showed the item as added when it wasn't. Now an explicit 409 with a shopper-friendly message.
     */
    private void ensureEnoughStock(ProductDto productDto, BigDecimal stockNeeded) {
        BigDecimal available = productDto.getStockQuantity();
        if (available == null || available.compareTo(stockNeeded) < 0) {
            String unit = productDto.getStockUnit() == null ? "" : " " + productDto.getStockUnit();
            String message = (available == null || available.signum() <= 0)
                    ? "'" + productDto.getProductName() + "' is out of stock."
                    : "Only " + available.stripTrailingZeros().toPlainString() + unit + " of '"
                        + productDto.getProductName() + "' is available.";
            throw new InsufficientStockException(productDto.getId(), message);
        }
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
    public boolean deleteItemFromCart(String productId, String weight) {
        String username = this.getUsername();
        if (productId == null || productId.isBlank()) {
            return false;
        }
        if (weight == null || weight.isBlank()) {
            cartItemRepository.deleteByUsernameAndProductId(username, productId);
        } else {
            cartItemRepository.deleteAll(cartItemRepository.findByUsernameAndProductId(username, productId).stream()
                    .filter(line -> sameWeight(line, weight.trim()))
                    .toList());
        }
        return true;
    }
}
