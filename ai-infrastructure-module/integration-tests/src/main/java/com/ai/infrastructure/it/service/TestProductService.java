package com.ai.infrastructure.it.service;

import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.it.entity.TestProduct;
import com.ai.infrastructure.it.repository.TestProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Test-facing service exposing methods annotated with {@link AIProcess} so that
 * integration scenarios can exercise the method-level AI processing aspect.
 */
@Service
@RequiredArgsConstructor
public class TestProductService {

    private final TestProductRepository productRepository;

    @AIProcess(entityType = "product", processType = "create")
    @Transactional
    public TestProduct createProduct(TestProduct product) {
        return productRepository.save(product);
    }

    @AIProcess(entityType = "product", processType = "update")
    @Transactional
    public TestProduct updateProduct(Long id, String name, String description, BigDecimal price) {
        TestProduct existing = productRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

        if (name != null) {
            existing.setName(name);
        }
        if (description != null) {
            existing.setDescription(description);
        }
        if (price != null) {
            existing.setPrice(price);
        }

        return productRepository.save(existing);
    }

    @AIProcess(entityType = "product", processType = "delete", generateEmbedding = false, indexForSearch = false)
    @Transactional
    public TestProduct deleteProduct(Long id) {
        TestProduct existing = productRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        productRepository.delete(existing);
        return existing;
    }

    public TestProduct getProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }
}

