package com.ai.fabric.vectorization.adapter.target;

import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationMappedRecord;
import com.ai.fabric.vectorization.model.VectorizationTargetWriteResult;

import java.util.List;

public interface VectorizationTargetWriter {

    VectorizationTargetWriteResult upsertBatch(VectorizationExecutionBundle bundle,
                                              String entityType,
                                              List<VectorizationMappedRecord> records) throws Exception;
}
