package com.ai.fabric.vectorization.adapter.source;

import com.ai.fabric.vectorization.model.VectorizationDiscoveryResult;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationSourcePage;

import java.util.List;

public interface VectorizationSourceAdapter {

    boolean supports(String adapterType);

    VectorizationDiscoveryResult discover(VectorizationExecutionBundle bundle, List<String> entityTypes) throws Exception;

    VectorizationSourcePage fetchPage(VectorizationExecutionBundle bundle,
                                      String entityType,
                                      String cursor,
                                      int pageSize) throws Exception;
}
