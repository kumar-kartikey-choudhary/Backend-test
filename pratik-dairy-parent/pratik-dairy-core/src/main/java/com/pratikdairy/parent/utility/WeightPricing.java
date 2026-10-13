package com.pratikdairy.parent.utility;

import com.pratikdairy.parent.exception.BadRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

public final class WeightPricing {

    private static final Set<String> SELECTABLE_WEIGHTS = Set.of("250g", "500g", "1kg");

    private WeightPricing() {}

    /** "500 G" -> "500g"; null/blank -> null. */
    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim().toLowerCase().replaceAll("\\s+", "");
    }

    /** True for kg/g units such as "kg", "500g", "0.5kg"; false for counted units like "pcs" or "litre". */
    public static boolean isWeightUnit(String unit) {
        String u = normalize(unit);
        return u != null && u.matches("^(\\d+(\\.\\d+)?)?(kg|g|gms)$");
    }

    /**
     * Rejects a pack size the shop doesn't sell. The weight arrives from the client as free text and
     * drives both the price and the stock taken, so anything outside the known pack sizes (e.g. "1g")
     * would let a caller buy at an arbitrary fraction of the price.
     *
     * Allowed: the product's own unit, or one of {@link #SELECTABLE_WEIGHTS}. A product sold by a
     * counted unit (pcs, litre ...) can't be split, so for those the pack size must not change the
     * price at all.
     */
    public static void requireValidSelection(String selectedWeight, String productUnit) {
        String weight = normalize(selectedWeight);
        String unit = normalize(productUnit);
        if (weight == null) {
            throw new BadRequestException("A pack size is required");
        }
        if (weight.equals(unit)) {
            return;
        }
        if (!SELECTABLE_WEIGHTS.contains(weight)) {
            throw new BadRequestException("'" + selectedWeight.trim() + "' is not an available pack size. Choose one of "
                    + String.join(", ", SELECTABLE_WEIGHTS.stream().sorted().toList()) + ".");
        }
        if (unit != null && !isWeightUnit(unit) && multiplierFor(weight, unit).compareTo(BigDecimal.ONE) != 0) {
            throw new BadRequestException("This product is sold per " + productUnit.trim() + " and can't be bought in a "
                    + weight + " pack.");
        }
    }

    public static double toGrams(String unit) {
        if (unit == null || unit.isBlank()) {
            return 1000.0; // Default to 1kg if blank
        }

        String u = unit.trim().toLowerCase().replaceAll("\\s+", "");

        // Fix for database having just "kg" or "g" without numbers
        if (u.equals("kg")) return 1000.0;
        if (u.equals("g") || u.equals("gms")) return 1.0;

        try {
            if (u.endsWith("kg")) {
                return Double.parseDouble(u.substring(0, u.length() - 2)) * 1000.0;
            }
            if (u.endsWith("gms")) {
                return Double.parseDouble(u.substring(0, u.length() - 3));
            }
            if (u.endsWith("g")) {
                return Double.parseDouble(u.substring(0, u.length() - 1));
            }
        } catch (NumberFormatException e) {
            return 1000.0; // Fallback to 1kg base if parsing fails
        }
        return 1000.0;
    }

    public static BigDecimal multiplierFor(String selectedWeight, String productBaseUnit) {
        double selectedGrams = toGrams(selectedWeight);
        double baseGrams = (productBaseUnit != null && !productBaseUnit.isBlank()) ? toGrams(productBaseUnit) : 1000.0;

        if (selectedGrams <= 0 || baseGrams <= 0) {
            return BigDecimal.ONE;
        }
        return BigDecimal.valueOf(selectedGrams)
                .divide(BigDecimal.valueOf(baseGrams), 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal priceFor(BigDecimal basePrice, String selectedWeight, String productBaseUnit) {
        if (basePrice == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal multiplier = multiplierFor(selectedWeight, productBaseUnit);
        return basePrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal stockToConsume(String selectedWeight, String productBaseUnit, int quantity) {
        return multiplierFor(selectedWeight, productBaseUnit).multiply(BigDecimal.valueOf(quantity));
    }
}