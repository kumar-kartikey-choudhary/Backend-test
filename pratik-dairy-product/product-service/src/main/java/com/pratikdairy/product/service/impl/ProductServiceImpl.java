package com.pratikdairy.product.service.impl;

import com.pratikdairy.product.dto.InventoryLedgerDto;
import com.pratikdairy.product.dto.ProductDto;
import com.pratikdairy.product.dto.ProductImageDto;
import com.pratikdairy.product.enums.Category;
import com.pratikdairy.product.enums.InventoryReason;
import com.pratikdairy.product.model.InventoryLedger;
import com.pratikdairy.product.model.Product;
import com.pratikdairy.product.model.ProductImage;
import com.pratikdairy.product.repository.InventoryLedgerRepository;
import com.pratikdairy.product.repository.ProductImageRepository;
import com.pratikdairy.product.repository.ProductRepository;
import com.pratikdairy.product.service.ProductService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final InventoryLedgerRepository ledgerRepository;

    @Autowired
    public ProductServiceImpl(ProductRepository productRepository,
                              ProductImageRepository imageRepository,
                              InventoryLedgerRepository ledgerRepository) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    @Transactional
    public ProductDto create(ProductDto productDto) {
        log.info("Inside @class ProductServiceImpl @method create @param productDto: {}", productDto);
        if (productDto == null) {
            throw new IllegalArgumentException("Product object can not be null");
        }
        if (productDto.getCategory() == null) {
            throw new IllegalArgumentException("category is required");
        }
        if (productDto.getPrice() == null || productDto.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be greater than 0");
        }

        Product product = new Product();
        product.setId(null);
        product.setProductName(productDto.getProductName());
        product.setPrice(productDto.getPrice());
        product.setAvailable(productDto.isAvailable());
        product.setStockUnit(productDto.getStockUnit());
        product.setStockQuantity(productDto.getStockQuantity() != null ? productDto.getStockQuantity() : BigDecimal.ZERO);
        product.setCategory(productDto.getCategory());
        product.setType(productDto.getType());
        product.setDescription(productDto.getDescription());
        product.setManufactureDate(productDto.getManufactureDate());
        product.setExpiryDate(productDto.getExpiryDate());
        product.setStatus(productDto.getStatus());

        Product saved = productRepository.saveAndFlush(product);
        log.info("Product saved to the db with id {}", saved.getId());

        // Starting stock is logged as a RESTOCK, so the ledger is complete from the moment the
        // product exists rather than starting from an unexplained number.
        if (saved.getStockQuantity() != null && saved.getStockQuantity().compareTo(BigDecimal.ZERO) > 0) {
            recordLedgerEntry(saved, saved.getStockQuantity(), InventoryReason.RESTOCK, null);
        }

        return toDto(saved, true);
    }

    @Override
    public ProductDto find(String id) {
        Product product = getProductOrThrow(id);
        return toDto(product, true);
    }

    @Override
    public List<ProductDto> findAll() {
        return productRepository.findAll().stream().map(p -> toDto(p, false)).toList();
    }

    @Override
    public List<ProductDto> findByCategory(Category category) {
        return productRepository.findByCategory(category).stream().map(p -> toDto(p, false)).toList();
    }

    @Override
    @Transactional
    public ProductDto update(ProductDto productDto, String id) {
        log.info("Inside @class ProductServiceImpl @method update @param id: {}, productDto: {}", id, productDto);
        Product product = getProductOrThrow(id);

        if (productDto.getProductName() != null) {
            product.setProductName(productDto.getProductName());
        }
        if (productDto.getPrice() != null) {
            product.setPrice(productDto.getPrice());
        }
        product.setAvailable(productDto.isAvailable());
        product.setStockUnit(productDto.getStockUnit());
        product.setDescription(productDto.getDescription());
        product.setType(productDto.getType());
        product.setManufactureDate(productDto.getManufactureDate());
        product.setExpiryDate(productDto.getExpiryDate());
        product.setStatus(productDto.getStatus());
        // Note: stockQuantity is deliberately NOT settable here - it only moves through
        // decrementStock/restoreStock/adjustStock so every change is logged in the ledger.

        if (productDto.getCategory() != null) {
            product.setCategory(productDto.getCategory());
        }

        Product updated = productRepository.saveAndFlush(product);
        log.info("Product {} updated", id);
        return toDto(updated, true);
    }

    @Override
    @Transactional
    public void delete(String id) {
        log.info("Inside @class ProductServiceImpl @method delete @param id: {}", id);
        if (!productRepository.existsById(id)) {
            throw new EntityNotFoundException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
        log.info("Product {} deleted", id);
    }

    @Override
    public List<ProductDto> searchProduct(String name) {
        if (name == null || name.isBlank()) {
            return findAll();
        }
        return productRepository.findByProductNameContainingIgnoreCase(name).stream()
                .map(p -> toDto(p, false)).toList();
    }

    @Override
    @Transactional
    public ProductDto addImage(String productId, MultipartFile imageFile, boolean primary) {
        Product product = getProductOrThrow(productId);
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        try {
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setImageName(imageFile.getOriginalFilename());
            image.setImageType(imageFile.getContentType());
            image.setImageData(imageFile.getBytes());
            image.setSortOrder(product.getImages().size());
            image.setPrimary(primary || product.getImages().isEmpty());

            if (image.isPrimary()) {
                product.getImages().forEach(existing -> existing.setPrimary(false));
            }

            product.getImages().add(image);
            Product saved = productRepository.saveAndFlush(product);
            return toDto(saved, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store product image", e);
        }
    }

    @Override
    public byte[] getImageBytes(String imageId) {
        return imageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("Image not found with id: " + imageId))
                .getImageData();
    }

    @Override
    public String getImageContentType(String imageId) {
        return imageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("Image not found with id: " + imageId))
                .getImageType();
    }

    @Override
    @Transactional
    public void deleteImage(String imageId) {
        if (!imageRepository.existsById(imageId)) {
            throw new EntityNotFoundException("Image not found with id: " + imageId);
        }
        imageRepository.deleteById(imageId);
    }

    @Override
    @Transactional
    public boolean decrementStock(String id, BigDecimal quantity, String orderId) {
        log.info("Inside @class ProductServiceImpl @method decrementStock @param id: {}, quantity: {}", id, quantity);
        if (id == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid id or quantity");
        }
        int updatedRows = productRepository.decrementStock(id, quantity);
        // updatedRows == 0 means WHERE stockQuantity >= quantity failed -> not enough stock,
        // no ledger entry is written since nothing actually changed.
        if (updatedRows > 0) {
            productRepository.findById(id)
                    .ifPresent(p -> recordLedgerEntry(p, quantity.negate(), InventoryReason.SALE, orderId));
        }
        return updatedRows > 0;
    }

    @Override
    @Transactional
    public void restoreStock(String id, BigDecimal quantity, String orderId) {
        log.info("Inside @class ProductServiceImpl @method restoreStock @param id: {}, quantity: {}", id, quantity);
        if (id == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid id or quantity");
        }
        productRepository.restoreStock(id, quantity);
        productRepository.findById(id)
                .ifPresent(p -> recordLedgerEntry(p, quantity, InventoryReason.RETURN, orderId));
    }

    @Override
    @Transactional
    public InventoryLedgerDto adjustStock(String id, BigDecimal changeQty, InventoryReason reason) {
        if (changeQty == null || changeQty.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("changeQty must be non-zero");
        }
        Product product = getProductOrThrow(id);

        BigDecimal newQty = product.getStockQuantity().add(changeQty);
        if (newQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Adjustment would take stock below zero");
        }
        product.setStockQuantity(newQty);
        productRepository.saveAndFlush(product);

        InventoryLedger entry = recordLedgerEntry(product, changeQty, reason, null);
        return toDto(entry);
    }

    @Override
    public List<InventoryLedgerDto> getLedger(String id) {
        return ledgerRepository.findByProduct_IdOrderByCreatedAtDesc(id).stream()
                .map(this::toDto).toList();
    }

    // ---- helpers ----

    private Product getProductOrThrow(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Product id can not be null");
        }
        return productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + id));
    }

    private InventoryLedger recordLedgerEntry(Product product, BigDecimal changeQty, InventoryReason reason, String referenceId) {
        InventoryLedger entry = new InventoryLedger();
        entry.setProduct(product);
        entry.setChangeQty(changeQty);
        entry.setReason(reason);
        entry.setReferenceId(referenceId);
        return ledgerRepository.saveAndFlush(entry);
    }

    private ProductDto toDto(Product product, boolean includeImageBytes) {
        ProductDto dto = new ProductDto();
        dto.setId(product.getId());
        dto.setProductName(product.getProductName());
        dto.setPrice(product.getPrice());
        dto.setAvailable(product.isAvailable());
        dto.setStockQuantity(product.getStockQuantity());
        dto.setStockUnit(product.getStockUnit());
        dto.setType(product.getType());
        dto.setDescription(product.getDescription());
        dto.setManufactureDate(product.getManufactureDate());
        dto.setExpiryDate(product.getExpiryDate());
        dto.setStatus(product.getStatus());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setModifiedAt(product.getModifiedAt());

        dto.setCategory(product.getCategory());

        List<ProductImageDto> imageDtos = new ArrayList<>();
        for (ProductImage image : product.getImages()) {
            ProductImageDto iDto = new ProductImageDto();
            iDto.setId(image.getId());
            iDto.setImageName(image.getImageName());
            iDto.setImageType(image.getImageType());
            iDto.setSortOrder(image.getSortOrder());
            iDto.setPrimary(image.isPrimary());
            // Full image bytes are only included on a single-product fetch, not on list/search
            // responses - fetching every image's bytes for a whole catalogue page would be huge.
            // List views should render <img src="/products/images/{id}"> instead.
            if (includeImageBytes) {
                iDto.setImageData(image.getImageData());
            }
            imageDtos.add(iDto);
        }
        imageDtos.sort(Comparator.comparingInt(ProductImageDto::getSortOrder));
        dto.setImages(imageDtos);

        return dto;
    }

    private InventoryLedgerDto toDto(InventoryLedger entry) {
        InventoryLedgerDto dto = new InventoryLedgerDto();
        dto.setId(entry.getId());
        dto.setProductId(entry.getProduct().getId());
        dto.setChangeQty(entry.getChangeQty());
        dto.setReason(entry.getReason());
        dto.setReferenceId(entry.getReferenceId());
        dto.setCreatedAt(entry.getCreatedAt());
        return dto;
    }
}