package com.pratikdairy.product.service;

import com.pratikdairy.product.dto.InventoryLedgerDto;
import com.pratikdairy.product.dto.ProductDto;
import com.pratikdairy.product.enums.Category;
import com.pratikdairy.product.enums.InventoryReason;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {

    ProductDto create(ProductDto productDto);

    ProductDto find(String id);

    List<ProductDto> findAll();

    List<ProductDto> findByCategory(Category category);

    ProductDto update(ProductDto productDto, String id);

    void delete(String id);

    List<ProductDto> searchProduct(String name);

    ProductDto addImage(String productId, MultipartFile imageFile, boolean primary);

    byte[] getImageBytes(String imageId);

    String getImageContentType(String imageId);

    void deleteImage(String imageId);

    // Returns true if stock was successfully decremented (enough stock was available), false
    // otherwise. quantity is in stockUnit-multiples.
    boolean decrementStock(String id, BigDecimal quantity, String orderId);

    // Adds stock back - used to roll back a decrement when an order fails partway through.
    void restoreStock(String id, BigDecimal quantity, String orderId);

    InventoryLedgerDto adjustStock(String id, BigDecimal changeQty, InventoryReason reason);

    List<InventoryLedgerDto> getLedger(String id);
}