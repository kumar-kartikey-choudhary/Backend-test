package com.pratikdairy.product.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pratikdairy.parent.base.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

// Product used to carry exactly one imageName/imageType/imageData triple, so a product could
// only ever have a single photo and the admin form had no way to add more. This makes images a
// real one-to-many: multiple photos per product, ordered, with one marked primary for listing
// thumbnails.
@Entity
@Table(name = "PRODUCT_IMAGE")
@Data
@EqualsAndHashCode(callSuper = false)
public class ProductImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    @JsonIgnore
    private Product product;

    @Column(name = "IMAGE_NAME")
    private String imageName;

    @Column(name = "IMAGE_TYPE")
    private String imageType;

    @Lob
    @Column(name = "IMAGE_DATA", columnDefinition = "LONGBLOB")
    private byte[] imageData;

    @Column(name = "SORT_ORDER", columnDefinition = "INT DEFAULT '0'")
    private int sortOrder = 0;

    @Column(name = "IS_PRIMARY", columnDefinition = "TINYINT(1) DEFAULT '0'")
    private boolean primary = false;
}