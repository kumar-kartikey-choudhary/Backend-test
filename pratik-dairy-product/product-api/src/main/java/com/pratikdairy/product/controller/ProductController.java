package com.pratikdairy.product.controller;

import com.pratikdairy.product.dto.InventoryLedgerDto;
import com.pratikdairy.product.dto.ProductDto;
import com.pratikdairy.product.enums.Category;
import com.pratikdairy.product.enums.InventoryReason;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@ResponseBody
@FeignClient(name = "PRATIK-DAIRY-PRODUCT", primary = false, path = "products", url = "${product.url}")
public interface ProductController {

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping(path = "addProduct")
    ResponseEntity<ProductDto> create(@RequestBody ProductDto productDto);

    @GetMapping(path = "product/{id}")
    ResponseEntity<ProductDto> find(@PathVariable(name = "id") String id);

    @GetMapping(path = "all")
    ResponseEntity<List<ProductDto>> findAll();

    @GetMapping(path = "category/{category}")
    ResponseEntity<List<ProductDto>> findByCategory(@PathVariable(name = "category") Category category);

    @GetMapping(path = "search")
    ResponseEntity<List<ProductDto>> searchProduct(@RequestParam String name);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PatchMapping(path = "admin/updateProduct/{id}")
    ResponseEntity<ProductDto> update(@PathVariable(name = "id") String id, @RequestBody ProductDto productDto);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping(path = "admin/deleteProduct/{id}")
    void delete(@PathVariable(name = "id") String id);

    // --- Images (one-to-many; was a single blob on Product) ---

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping(path = "admin/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ProductDto> addImage(@PathVariable(name = "productId") String productId,
                                        @RequestPart("imageFile") MultipartFile imageFile,
                                        @RequestParam(name = "primary", defaultValue = "false") boolean primary);

    @GetMapping(path = "images/{imageId}")
    ResponseEntity<byte[]> getImage(@PathVariable(name = "imageId") String imageId);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping(path = "admin/images/{imageId}")
    void deleteImage(@PathVariable(name = "imageId") String imageId);

    // --- Stock ---

    // Internal, service-to-service only. Called by order-service at checkout time.
    // `quantity` is in stockUnit-multiples, same convention as before.
    @PreAuthorize("isAuthenticated()")
    @PatchMapping(path = "internal/{id}/decrement-stock")
    ResponseEntity<Boolean> decrementStock(@PathVariable(name = "id") String id,
                                           @RequestParam(name = "quantity") BigDecimal quantity,
                                           @RequestParam(name = "orderId", required = false) String orderId);

    // Rolls back a decrement if a later item in the same order fails.
    @PreAuthorize("isAuthenticated()")
    @PatchMapping(path = "internal/{id}/restore-stock")
    ResponseEntity<Void> restoreStock(@PathVariable(name = "id") String id,
                                      @RequestParam(name = "quantity") BigDecimal quantity,
                                      @RequestParam(name = "orderId", required = false) String orderId);

    // Manual stock movement from the admin panel (restock, spoilage write-off, correction).
    // Every call here, and every decrement/restore above, appends one row to the inventory
    // ledger instead of just overwriting the stock number - see InventoryLedger.
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping(path = "admin/{id}/adjust-stock")
    ResponseEntity<InventoryLedgerDto> adjustStock(@PathVariable(name = "id") String id,
                                                   @RequestParam(name = "changeQty") BigDecimal changeQty,
                                                   @RequestParam(name = "reason") InventoryReason reason);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping(path = "admin/{id}/ledger")
    ResponseEntity<List<InventoryLedgerDto>> getLedger(@PathVariable(name = "id") String id);
}