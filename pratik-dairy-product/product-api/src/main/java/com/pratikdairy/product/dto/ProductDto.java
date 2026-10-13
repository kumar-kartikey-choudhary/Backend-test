package com.pratikdairy.product.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pratikdairy.parent.base.dto.BaseDto;
import com.pratikdairy.product.enums.Category;
import com.pratikdairy.product.enums.SweetType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Product as it travels over the API.
 *
 * Updates are PATCHes, so a field missing from the request must mean "leave it alone", which is not the
 * same as a field sent as null ("clear it"). A plain DTO can't tell the two apart, so the editable fields
 * below record, in their setters, that they were present: Jackson only calls a setter for a property that
 * is in the JSON. {@link #isProvided(String)} lets the service apply exactly what the caller sent.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ProductDto extends BaseDto {

    @Getter(AccessLevel.NONE)
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final Set<String> provided = new HashSet<>();

    private String productName;
    private BigDecimal price;
    // Boxed so "not sent" (null) is distinguishable; a product is available unless the admin says otherwise.
    private Boolean available;

    // Stock is intentionally not free-form editable on update - it only moves through the
    // decrement/restore/adjust-stock endpoints so every change lands in the inventory ledger.
    // Still present here for create (starting stock) and for reading the current value.
    private BigDecimal stockQuantity;
    private String stockUnit;

    private Category category;
    private SweetType type;
    private String description;
    private LocalDate manufactureDate;
    private LocalDate expiryDate;
    private String status;

    private List<ProductImageDto> images = new ArrayList<>();

    /** True if the JSON this DTO was read from contained the field (even as null). */
    public boolean isProvided(String field) {
        return provided.contains(field);
    }

    public void setProductName(String productName) { this.productName = productName; provided.add("productName"); }
    public void setPrice(BigDecimal price) { this.price = price; provided.add("price"); }
    public void setAvailable(Boolean available) { this.available = available; provided.add("available"); }
    public void setStockUnit(String stockUnit) { this.stockUnit = stockUnit; provided.add("stockUnit"); }
    public void setCategory(Category category) { this.category = category; provided.add("category"); }
    public void setType(SweetType type) { this.type = type; provided.add("type"); }
    public void setDescription(String description) { this.description = description; provided.add("description"); }
    public void setManufactureDate(LocalDate manufactureDate) { this.manufactureDate = manufactureDate; provided.add("manufactureDate"); }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; provided.add("expiryDate"); }
    public void setStatus(String status) { this.status = status; provided.add("status"); }
}
