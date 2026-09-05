package com.pratikdairy.product.model;

import com.pratikdairy.parent.base.entity.BaseEntity;
import com.pratikdairy.product.enums.Category;
import com.pratikdairy.product.enums.SweetType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "PRODUCT")
@Data
@EqualsAndHashCode(callSuper = false)
public class Product extends BaseEntity {

    @Column(name = "PRODUCT_NAME", columnDefinition = "VARCHAR(100) NOT NULL", nullable = false)
    private String productName;

    @Column(name = "PRICE", nullable = false)
    private BigDecimal price;

    @Column(name = "IS_AVAILABLE", columnDefinition = "TINYINT(1) DEFAULT '1'")
    private boolean available = true;

    // Stock, expressed in `stockUnit`-multiples (e.g. if stockUnit = "kg" and stockQuantity =
    // 10.5, there is 10.5kg in stock). Kept as a decimal so partial-unit stock still works.
    @Column(name = "STOCK_QUANTITY", columnDefinition = "DECIMAL(12,3) DEFAULT '0'", precision = 12, scale = 3)
    private BigDecimal stockQuantity = BigDecimal.ZERO;

    @Column(name = "STOCK_UNIT", columnDefinition = "VARCHAR(20)")
    private String stockUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY", columnDefinition = "VARCHAR(20) NOT NULL", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "SWEET_TYPE", columnDefinition = "VARCHAR(50)")
    private SweetType type;

    @Column(name = "DESCRIPTION", columnDefinition = "VARCHAR(1000)")
    private String description;

    @Column(name = "MANUFACTURE_DATE")
    private LocalDate manufactureDate;

    @Column(name = "EXPIRY_DATE")
    private LocalDate expiryDate;

    @Column(name = "STATUS")
    private String status;

    // Was: a single imageName/imageType/imageData triple, so a product could only ever have one
    // photo. Now a proper one-to-many - see ProductImage.
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();
}