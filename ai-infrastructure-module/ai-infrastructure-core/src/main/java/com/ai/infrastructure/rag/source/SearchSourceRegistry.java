package com.ai.infrastructure.rag.source;

import com.ai.infrastructure.dto.RAGRequest;

import java.util.List;

public interface SearchSourceRegistry {

    String contractVersion();

    List<String> supportedAdapterTypes();

    List<SearchSource> resolveSearchSources(RAGRequest request);
}
