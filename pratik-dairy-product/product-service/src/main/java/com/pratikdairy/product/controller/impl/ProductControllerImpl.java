package com.pratikdairy.product.controller.impl;

import com.pratikdairy.product.controller.ProductController;
import com.pratikdairy.product.dto.InventoryLedgerDto;
import com.pratikdairy.product.dto.ProductDto;
import com.pratikdairy.product.enums.Category;
import com.pratikdairy.product.enums.InventoryReason;
import com.pratikdairy.product.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@Primary
@RequestMapping("products")
public class ProductControllerImpl implements ProductController {

    private final ProductService productService;

    @Autowired
    public ProductControllerImpl(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public ResponseEntity<ProductDto> create(ProductDto productDto) {
        return new ResponseEntity<>(productService.create(productDto), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<ProductDto> find(String id) {
        return ResponseEntity.ok(productService.find(id));
    }

    @Override
    public ResponseEntity<List<ProductDto>> findAll() {
        return ResponseEntity.ok(productService.findAll());
    }

    @Override
    public ResponseEntity<List<ProductDto>> findByCategory(Category category) {
        return ResponseEntity.ok(productService.findByCategory(category));
    }

    @Override
    public ResponseEntity<List<ProductDto>> searchProduct(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(productService.searchProduct(name));
    }

    @Override
    public ResponseEntity<ProductDto> update(String id, ProductDto productDto) {
        return ResponseEntity.ok(productService.update(productDto, id));
    }

    @Override
    public void delete(String id) {
        productService.delete(id);
    }

    @Override
    public ResponseEntity<ProductDto> addImage(String productId, MultipartFile imageFile, boolean primary) {
        return new ResponseEntity<>(productService.addImage(productId, imageFile, primary), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<byte[]> getImage(String imageId) {
        byte[] bytes = productService.getImageBytes(imageId);
        String contentType = productService.getImageContentType(imageId);
        return ResponseEntity.ok()
                .contentType(contentType != null ? MediaType.valueOf(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    @Override
    public void deleteImage(String imageId) {
        productService.deleteImage(imageId);
    }

    @Override
    public ResponseEntity<Boolean> decrementStock(String id, BigDecimal quantity, String orderId) {
        return ResponseEntity.ok(productService.decrementStock(id, quantity, orderId));
    }

    @Override
    public ResponseEntity<Void> restoreStock(String id, BigDecimal quantity, String orderId) {
        productService.restoreStock(id, quantity, orderId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<InventoryLedgerDto> adjustStock(String id, BigDecimal changeQty, InventoryReason reason) {
        return ResponseEntity.ok(productService.adjustStock(id, changeQty, reason));
    }

    @Override
    public ResponseEntity<List<InventoryLedgerDto>> getLedger(String id) {
        return ResponseEntity.ok(productService.getLedger(id));
    }
}