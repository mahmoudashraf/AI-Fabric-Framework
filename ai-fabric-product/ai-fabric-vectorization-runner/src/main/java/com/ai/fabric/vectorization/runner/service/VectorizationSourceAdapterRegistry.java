package com.ai.fabric.vectorization.runner.service;

import com.ai.fabric.vectorization.adapter.source.VectorizationSourceAdapter;

import java.util.List;

public class VectorizationSourceAdapterRegistry {

    private final List<VectorizationSourceAdapter> adapters;

    public VectorizationSourceAdapterRegistry(List<VectorizationSourceAdapter> adapters) {
        this.adapters = adapters;
    }

    public VectorizationSourceAdapter resolve(String adapterType) {
        return adapters.stream()
            .filter(adapter -> adapter.supports(adapterType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported vectorization adapter type: " + adapterType));
    }
}
