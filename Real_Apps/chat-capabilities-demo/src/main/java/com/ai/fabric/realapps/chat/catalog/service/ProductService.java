package com.ai.fabric.realapps.chat.catalog.service;

import com.ai.fabric.realapps.chat.catalog.domain.Product;
import com.ai.fabric.realapps.chat.catalog.repo.ProductRepository;
import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final AICoreService aiCoreService;

    @Transactional(readOnly = true)
    public List<Product> list(int limit) {
        int effectiveLimit = limit <= 0 ? 50 : Math.min(limit, 500);
        return productRepository.findAll().stream().limit(effectiveLimit).toList();
    }

    @Transactional(readOnly = true)
    public Product get(long id) {
        return productRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<Product> findBySku(String sku) {
        String normalized = SkuNormalizer.normalizeForLookup(sku);
        if (!StringUtils.hasText(normalized)) {
            return Optional.empty();
        }

        // Try exact match first (fast path), then case-insensitive.
        return productRepository.findBySku(normalized)
            .or(() -> productRepository.findBySkuIgnoreCase(normalized));
    }

    @Transactional
    @AIProcess(entityType = "product", processType = "create", generateEmbedding = true, indexForSearch = true)
    public Product createProduct(String sku,
                                 String name,
                                 String description,
                                 String category,
                                 String tags,
                                 String imageUrl,
                                 BigDecimal price,
                                 String currency,
                                 Integer inStockQty) {
        if (!StringUtils.hasText(sku)) {
            throw new IllegalArgumentException("sku is required");
        }
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("name is required");
        }
        if (!StringUtils.hasText(description)) {
            throw new IllegalArgumentException("description is required");
        }

        String normalizedSku = SkuNormalizer.normalize(sku);
        if (!StringUtils.hasText(normalizedSku)) {
            throw new IllegalArgumentException("sku is required");
        }
        if (productRepository.existsBySkuIgnoreCase(normalizedSku)) {
            throw new IllegalArgumentException("sku already exists: " + normalizedSku);
        }

        Product product = new Product();
        product.setSku(normalizedSku);
        product.setName(name.trim());
        product.setDescription(description.trim());
        product.setCategory(StringUtils.hasText(category) ? category.trim() : null);
        product.setTags(StringUtils.hasText(tags) ? tags.trim() : null);
        product.setImageUrl(StringUtils.hasText(imageUrl) ? imageUrl.trim() : null);
        product.setPrice(price);
        product.setCurrency(StringUtils.hasText(currency) ? currency.trim().toUpperCase() : "USD");
        product.setInStockQty(inStockQty != null ? Math.max(0, inStockQty) : 0);

        return productRepository.save(product);
    }

    @Transactional
    @AIProcess(entityType = "product", processType = "update", generateEmbedding = true, indexForSearch = true)
    public Product updateProduct(long id,
                                 String sku,
                                 String name,
                                 String description,
                                 String category,
                                 String tags,
                                 String imageUrl,
                                 BigDecimal price,
                                 String currency,
                                 Integer inStockQty) {
        Product product = get(id);

        if (StringUtils.hasText(sku)) {
            String normalizedSku = SkuNormalizer.normalize(sku);
            if (!StringUtils.hasText(normalizedSku)) {
                throw new IllegalArgumentException("sku is required");
            }
            if (!equalsIgnoreCase(normalizedSku, product.getSku()) && productRepository.existsBySkuIgnoreCase(normalizedSku)) {
                throw new IllegalArgumentException("sku already exists: " + normalizedSku);
            }
            product.setSku(normalizedSku);
        }
        if (StringUtils.hasText(name)) {
            product.setName(name.trim());
        }
        if (StringUtils.hasText(description)) {
            product.setDescription(description.trim());
        }
        if (category != null) {
            product.setCategory(StringUtils.hasText(category) ? category.trim() : null);
        }
        if (tags != null) {
            product.setTags(StringUtils.hasText(tags) ? tags.trim() : null);
        }
        if (imageUrl != null) {
            product.setImageUrl(StringUtils.hasText(imageUrl) ? imageUrl.trim() : null);
        }
        if (price != null) {
            product.setPrice(price);
        }
        if (StringUtils.hasText(currency)) {
            product.setCurrency(currency.trim().toUpperCase());
        }
        if (inStockQty != null) {
            product.setInStockQty(Math.max(0, inStockQty));
        }

        return productRepository.save(product);
    }

    @Transactional
    @AIProcess(entityType = "product", processType = "update", generateEmbedding = true, indexForSearch = true)
    public Product updateProductStock(String sku, int newInStockQty) {
        String normalizedSku = SkuNormalizer.normalizeForLookup(sku);
        if (!StringUtils.hasText(normalizedSku)) {
            throw new IllegalArgumentException("sku is required");
        }
        Product product = findBySku(normalizedSku)
            .orElseThrow(() -> new EntityNotFoundException("Product not found for sku: " + normalizedSku));
        product.setInStockQty(Math.max(0, newInStockQty));
        return productRepository.save(product);
    }

    @Transactional
    @AIProcess(entityType = "product", processType = "delete", generateEmbedding = false, indexForSearch = false)
    public Product deleteProduct(long id) {
        Product product = get(id);
        productRepository.delete(product);
        return product;
    }

    @Transactional(readOnly = true)
    public long count() {
        return productRepository.count();
    }

    @Transactional(readOnly = true)
    public List<Product> search(String query, int limit, double threshold) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        int effectiveLimit = limit <= 0 ? 10 : Math.min(limit, 50);
        double effectiveThreshold = threshold <= 0.0 ? 0.3 : threshold;

        AISearchResponse response = aiCoreService.performSearch(AISearchRequest.builder()
            .query(query)
            .entityType("product")
            .limit(effectiveLimit)
            .threshold(effectiveThreshold)
            .build());

        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return List.of();
        }

        return response.getResults().stream()
            .map(this::extractEntityId)
            .filter(Objects::nonNull)
            .map(id -> {
                try {
                    return productRepository.findById(Long.parseLong(id)).orElse(null);
                } catch (NumberFormatException ex) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .limit(effectiveLimit)
            .toList();
    }

    private String extractEntityId(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        Object id = firstNonNull(row.get("entityId"), row.get("id"));
        return id != null ? Objects.toString(id, null) : null;
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.equalsIgnoreCase(right);
    }
}
