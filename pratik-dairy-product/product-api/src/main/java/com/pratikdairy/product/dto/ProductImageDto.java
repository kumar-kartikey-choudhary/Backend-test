package com.pratikdairy.product.dto;

import com.pratikdairy.parent.base.dto.BaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ProductImageDto extends BaseDto {

    private String imageName;
    private String imageType;
    // Not populated on list/search responses (see ProductServiceImpl) to avoid dragging
    // every image's full bytes into the catalogue payload - fetch via the dedicated
    // /products/images/{imageId} endpoint instead.
    private byte[] imageData;
    private int sortOrder;
    private boolean primary;
}