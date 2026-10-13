package com.pratikdairy.cart.service;

import com.pratikdairy.cart.dto.AddToCart;
import com.pratikdairy.cart.dto.CartItemDto;

import java.util.List;

public interface CartService {


    CartItemDto addItemToCart( AddToCart request);

    /**
     * Sets the pack count of one cart line. A product can have several lines (one per pack size), so
     * {@code weight} says which; it may be left out only when the product has a single line.
     */
    CartItemDto updateQuantity(String productId, String weight, int quantity);

    List<CartItemDto> getCart();

    void clearCart();

    /** Removes one line ({@code weight} given) or, with no weight, every line of the product. */
    boolean deleteItemFromCart(String productId, String weight);
}
