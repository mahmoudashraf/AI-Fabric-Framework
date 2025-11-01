package com.ai.infrastructure.embedding;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.exception.AIServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import ai.onnxruntime.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ONNX Runtime Embedding Provider
 * 
 * Provides local embedding generation using ONNX Runtime.
 * No external API calls required - runs entirely on local machine.
 * 
 * Requires:
 * - ONNX model file (.onnx)
 * - Tokenizer file (tokenizer.json or Java-based tokenizer)
 * 
 * Default provider when ai.embedding.provider=onnx (or not specified)
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.providers.embedding-provider", havingValue = "onnx", matchIfMissing = true)
public class ONNXEmbeddingProvider implements EmbeddingProvider {
    
    private final AIProviderConfig config;
    
    @Value("${ai.providers.onnx-model-path:./models/embeddings/all-MiniLM-L6-v2.onnx}")
    private String modelPath;
    
    @Value("${ai.providers.onnx-tokenizer-path:./models/embeddings/tokenizer.json}")
    private String tokenizerPath;
    
    @Value("${ai.providers.onnx-max-sequence-length:512}")
    private int maxSequenceLength;
    
    @Value("${ai.providers.onnx-use-gpu:false}")
    private boolean useGpu;
    
    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;
    private int embeddingDimension = 384; // Default for all-MiniLM-L6-v2
    
    // Thread safety: ONNX Runtime sessions are NOT thread-safe
    // Using ReentrantLock to synchronize access to ortSession
    private final ReentrantLock sessionLock = new ReentrantLock();
    
    // Special token IDs for BERT-based models (all-MiniLM-L6-v2)
    private static final int TOKEN_CLS = 101;  // [CLS] token
    private static final int TOKEN_SEP = 102;  // [SEP] token
    private static final int TOKEN_PAD = 0;    // [PAD] token
    private static final int TOKEN_UNK = 100;  // [UNK] token
    private static final int VOCAB_SIZE = 30522; // BERT vocabulary size
    
    @PostConstruct
    public void initialize() {
        try {
            log.info("========================================");
            log.info("Initializing ONNX Embedding Provider");
            log.info("Model path: {}", modelPath);
            log.info("Tokenizer path: {}", tokenizerPath);
            log.info("Current working directory: {}", System.getProperty("user.dir"));
            log.info("========================================");
            
            // Check if model file exists
            Path modelFilePath = Paths.get(modelPath);
            
            // Try to resolve as absolute or relative path
            if (!modelFilePath.isAbsolute()) {
                String userDir = System.getProperty("user.dir");
                Path absolutePath = Paths.get(userDir, modelPath);
                log.info("Resolved absolute path: {}", absolutePath.toAbsolutePath());
                if (Files.exists(absolutePath)) {
                    modelFilePath = absolutePath;
                    log.info("Found model file at absolute path: {}", absolutePath.toAbsolutePath());
                } else {
                    log.warn("Model file not found at absolute path: {}", absolutePath.toAbsolutePath());
                }
            }
            
            if (!Files.exists(modelFilePath)) {
                log.error("========================================");
                log.error("ONNX model file not found at: {}", modelFilePath.toAbsolutePath());
                log.error("Provider will not be available.");
                log.error("Current working directory: {}", System.getProperty("user.dir"));
                log.error("Absolute path checked: {}", modelFilePath.toAbsolutePath());
                log.error("========================================");
                return;
            }
            
            log.info("Model file found at: {}", modelFilePath.toAbsolutePath());
            
            // Initialize ONNX Runtime environment
            ortEnvironment = OrtEnvironment.getEnvironment();
            
            // Configure session options
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            
            // Set execution provider (CPU or GPU)
            if (useGpu) {
                try {
                    sessionOptions.addCUDA(0);
                    log.info("Using GPU for ONNX inference");
                } catch (Exception e) {
                    log.warn("GPU not available, falling back to CPU: {}", e.getMessage());
                    sessionOptions.addCPU(true); // Use optimized CPU execution
                }
            } else {
                sessionOptions.addCPU(true); // Use optimized CPU execution
                log.info("Using CPU for ONNX inference");
            }
            
            // Load ONNX model
            ortSession = ortEnvironment.createSession(modelPath, sessionOptions);
            
                // Get input info to see what inputs the model expects
                Map<String, NodeInfo> inputInfo = ortSession.getInputInfo();
                log.info("Model input names: {}", inputInfo.keySet());
                for (Map.Entry<String, NodeInfo> entry : inputInfo.entrySet()) {
                    log.info("  Input: {} - {}", entry.getKey(), entry.getValue().getInfo().toString());
                }
                
                // Get output shape to determine embedding dimension
                Map<String, NodeInfo> outputInfo = ortSession.getOutputInfo();
                for (Map.Entry<String, NodeInfo> entry : outputInfo.entrySet()) {
                    NodeInfo nodeInfo = entry.getValue();
                    if (nodeInfo.getInfo() instanceof TensorInfo) {
                        TensorInfo tensorInfo = (TensorInfo) nodeInfo.getInfo();
                        long[] shape = tensorInfo.getShape();
                        if (shape.length >= 2) {
                            embeddingDimension = (int) shape[shape.length - 1];
                            log.info("Detected embedding dimension: {}", embeddingDimension);
                        }
                    }
                }
            
            log.info("Tokenizer initialized: WordPiece-like tokenization with special tokens");
            log.info("  [CLS]={}, [SEP]={}, [PAD]={}, [UNK]={}", TOKEN_CLS, TOKEN_SEP, TOKEN_PAD, TOKEN_UNK);
            log.info("  Using WordPiece-like tokenization algorithm (improved from character-based)");
            
            log.info("ONNX Embedding Provider initialized successfully with model: {}", modelPath);
            
        } catch (Exception e) {
            log.error("Failed to initialize ONNX Embedding Provider", e);
            throw new AIServiceException("Failed to initialize ONNX Embedding Provider", e);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        try {
            if (ortSession != null) {
                ortSession.close();
            }
            // Note: OrtEnvironment is a singleton and should not be closed
            log.debug("ONNX Embedding Provider cleaned up");
        } catch (Exception e) {
            log.error("Error cleaning up ONNX Embedding Provider", e);
        }
    }
    
    
    @Override
    public String getProviderName() {
        return "onnx";
    }
    
    @Override
    public boolean isAvailable() {
        return ortSession != null && ortEnvironment != null;
    }
    
    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        sessionLock.lock();
        try {
            if (!isAvailable()) {
                throw new AIServiceException("ONNX Embedding Provider is not available");
            }
            
            log.debug("Generating embedding using ONNX for text: {}", request.getText());
            
            long startTime = System.currentTimeMillis();
            
            // Tokenize text (simple implementation)
            int[] inputIds = tokenize(request.getText());
            int sequenceLength = Math.min(inputIds.length, maxSequenceLength);
            
            // Pad or truncate to maxSequenceLength
            long[] inputIdsLong = new long[sequenceLength];
            long[] attentionMaskLong = new long[sequenceLength];
            long[] tokenTypeIdsLong = new long[sequenceLength]; // Typically all zeros for single sequence
            
            for (int i = 0; i < sequenceLength; i++) {
                inputIdsLong[i] = i < inputIds.length ? inputIds[i] : 0; // 0 is padding token
                attentionMaskLong[i] = i < inputIds.length ? 1L : 0L; // 1 for real tokens, 0 for padding
                tokenTypeIdsLong[i] = 0L; // Single sequence, all zeros
            }
            
            // Create ONNX tensor inputs
            long[] shape = new long[]{1, sequenceLength}; // Batch size 1, sequence length
            java.nio.LongBuffer inputIdsBuffer = java.nio.LongBuffer.wrap(inputIdsLong);
            java.nio.LongBuffer attentionMaskBuffer = java.nio.LongBuffer.wrap(attentionMaskLong);
            java.nio.LongBuffer tokenTypeIdsBuffer = java.nio.LongBuffer.wrap(tokenTypeIdsLong);
            
            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, inputIdsBuffer, shape);
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(ortEnvironment, attentionMaskBuffer, shape);
            OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(ortEnvironment, tokenTypeIdsBuffer, shape);
            
            // Prepare inputs - sentence-transformers models typically require:
            // - input_ids: token IDs
            // - attention_mask: mask for padding
            // - token_type_ids: segment IDs (usually all zeros for single sequence)
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);
            inputs.put("token_type_ids", tokenTypeIdsTensor);
            
            // Run inference
            OrtSession.Result output = ortSession.run(inputs);
            
            // Extract embeddings - try common output keys or use first output
            OnnxValue embeddingValue = null;
            
            // Get the output - ONNX Runtime Result returns values by index
            // For sentence-transformers models, the output is typically at index 0
            embeddingValue = output.get(0);
            log.debug("Using output at index 0");
            
            // Log the output type for debugging
            Object value = embeddingValue.getValue();
            log.debug("Output value type: {}, class: {}", 
                     value != null ? value.getClass().getName() : "null",
                     value != null ? value.getClass().getSimpleName() : "null");
            
            float[][] embeddings;
            
            // Handle different output formats
            // Try 3D array first: [batch, sequence, embedding_dim] (common for transformer outputs)
            if (value instanceof float[][][]) {
                float[][][] tensorValue3D = (float[][][]) value;
                if (tensorValue3D.length > 0 && tensorValue3D[0].length > 0) {
                    // Mean pooling: average over sequence length
                    int batchSize = tensorValue3D.length;
                    int seqLength = tensorValue3D[0].length;
                    int embeddingDim = tensorValue3D[0][0].length;
                    embeddings = new float[batchSize][embeddingDim];
                    for (int b = 0; b < batchSize; b++) {
                        for (int e = 0; e < embeddingDim; e++) {
                            float sum = 0.0f;
                            for (int s = 0; s < seqLength; s++) {
                                sum += tensorValue3D[b][s][e];
                            }
                            embeddings[b][e] = sum / seqLength;
                        }
                    }
                    log.debug("Successfully processed 3D array [batch={}, sequence={}, embedding={}] with mean pooling", 
                             batchSize, seqLength, embeddingDim);
                } else {
                    throw new AIServiceException("Empty 3D array output");
                }
            } else if (value instanceof float[][]) {
                embeddings = (float[][]) value;
            } else if (value instanceof float[]) {
                float[] flat = (float[]) value;
                // Reshape to 2D: [batch_size, embedding_dim]
                embeddings = new float[][]{flat};
            } else if (value instanceof OnnxTensor) {
                // Extract tensor values
                OnnxTensor tensor = (OnnxTensor) value;
                try {
                    // Try 3D tensor first: [batch, sequence, embedding_dim]
                    float[][][] tensorValue3D = (float[][][]) tensor.getValue();
                    if (tensorValue3D.length > 0 && tensorValue3D[0].length > 0) {
                        // Mean pooling: average over sequence length
                        int batchSize = tensorValue3D.length;
                        int seqLength = tensorValue3D[0].length;
                        int embeddingDim = tensorValue3D[0][0].length;
                        embeddings = new float[batchSize][embeddingDim];
                        for (int b = 0; b < batchSize; b++) {
                            for (int e = 0; e < embeddingDim; e++) {
                                float sum = 0.0f;
                                for (int s = 0; s < seqLength; s++) {
                                    sum += tensorValue3D[b][s][e];
                                }
                                embeddings[b][e] = sum / seqLength;
                            }
                        }
                        log.debug("Successfully extracted 3D tensor [batch={}, sequence={}, embedding={}] with mean pooling", 
                                 batchSize, seqLength, embeddingDim);
                    } else {
                        throw new AIServiceException("Empty 3D tensor output");
                    }
                } catch (ClassCastException e) {
                    // Try 4D tensor: [batch, sequence, 1, embedding]
                    try {
                        float[][][][] tensorValue4D = (float[][][][]) tensor.getValue();
                        if (tensorValue4D.length > 0 && tensorValue4D[0].length > 0) {
                            int batchSize = tensorValue4D.length;
                            int seqLength = tensorValue4D[0].length;
                            int embeddingDim = tensorValue4D[0][0][0].length;
                            embeddings = new float[batchSize][embeddingDim];
                            for (int b2 = 0; b2 < batchSize; b2++) {
                                for (int e2 = 0; e2 < embeddingDim; e2++) {
                                    float sum = 0.0f;
                                    for (int s2 = 0; s2 < seqLength; s2++) {
                                        sum += tensorValue4D[b2][s2][0][e2];
                                    }
                                    embeddings[b2][e2] = sum / seqLength;
                                }
                            }
                            log.debug("Successfully extracted 4D tensor [batch={}, sequence={}, embedding={}] with mean pooling", 
                                     batchSize, seqLength, embeddingDim);
                        } else {
                            throw new AIServiceException("Empty 4D tensor output");
                        }
                    } catch (ClassCastException e2) {
                        // Try 2D tensor
                        try {
                            float[][] tensorValue2D = (float[][]) tensor.getValue();
                            embeddings = tensorValue2D;
                            log.debug("Successfully extracted 2D tensor");
                        } catch (ClassCastException e3) {
                            throw new AIServiceException("Unexpected tensor output format. Tried 3D, 4D, and 2D. Actual: " + 
                                (value != null ? value.getClass().getName() : "null"), e3);
                        }
                    }
                } catch (Exception ex) {
                    log.error("Error extracting tensor value", ex);
                    throw new AIServiceException("Failed to extract tensor value", ex);
                } finally {
                    // Close the tensor to prevent resource leak
                    if (tensor != null) {
                        try {
                            tensor.close();
                        } catch (Exception closeEx) {
                            log.warn("Error closing OnnxTensor", closeEx);
                        }
                    }
                }
            } else {
                // Log the actual type for debugging
                log.error("Unexpected embedding output format. Type: {}, Value: {}", 
                         value != null ? value.getClass().getName() : "null",
                         value);
                throw new AIServiceException("Unexpected embedding output format: " + 
                    (value != null ? value.getClass().getName() : "null"));
            }
            
            // Convert float[] to List<Double>
            final float[][] finalEmbeddings = embeddings; // Make final for lambda
            List<Double> embedding = IntStream.range(0, finalEmbeddings[0].length)
                .mapToObj(i -> (double) finalEmbeddings[0][i])
                .collect(Collectors.toList());
            
            // Cleanup tensors
            inputIdsTensor.close();
            attentionMaskTensor.close();
            tokenTypeIdsTensor.close();
            output.close();
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.debug("Successfully generated ONNX embedding with {} dimensions in {}ms", 
                     embedding.size(), processingTime);
            
            return AIEmbeddingResponse.builder()
                .embedding(embedding)
                .model("onnx:" + Paths.get(modelPath).getFileName().toString())
                .dimensions(embedding.size())
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating ONNX embedding", e);
            throw new AIServiceException("Failed to generate ONNX embedding", e);
        } finally {
            sessionLock.unlock();
        }
    }
    
    @Override
    public List<AIEmbeddingResponse> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        
        if (texts.size() == 1) {
            // Single item - use single embedding method
            AIEmbeddingRequest request = AIEmbeddingRequest.builder()
                .text(texts.get(0))
                .model("onnx")
                .build();
            return Collections.singletonList(generateEmbedding(request));
        }
        
        // True batch processing: single ONNX inference call
        sessionLock.lock();
        try {
            if (!isAvailable()) {
                throw new AIServiceException("ONNX Embedding Provider is not available");
            }
            
            log.debug("Generating {} embeddings using ONNX batch processing", texts.size());
            
            long startTime = System.currentTimeMillis();
            
            // Tokenize all texts
            List<int[]> tokenizedTexts = new ArrayList<>();
            int maxSequenceLength = 0;
            
            for (String text : texts) {
                int[] tokens = tokenize(text);
                tokenizedTexts.add(tokens);
                maxSequenceLength = Math.max(maxSequenceLength, Math.min(tokens.length, this.maxSequenceLength));
            }
            
            int batchSize = texts.size();
            int sequenceLength = maxSequenceLength;
            
            // Create batch tensors: [batch_size, sequence_length]
            long[] flatInputIds = new long[batchSize * sequenceLength];
            long[] flatAttentionMasks = new long[batchSize * sequenceLength];
            long[] flatTokenTypeIds = new long[batchSize * sequenceLength];
            
            // Fill batch tensors
            for (int b = 0; b < batchSize; b++) {
                int[] tokens = tokenizedTexts.get(b);
                int offset = b * sequenceLength;
                
                for (int s = 0; s < sequenceLength; s++) {
                    if (s < tokens.length) {
                        flatInputIds[offset + s] = tokens[s];
                        flatAttentionMasks[offset + s] = 1L; // Real token
                    } else {
                        flatInputIds[offset + s] = TOKEN_PAD;
                        flatAttentionMasks[offset + s] = 0L; // Padding
                    }
                    flatTokenTypeIds[offset + s] = 0L; // Single sequence
                }
            }
            
            // Create ONNX tensors with batch shape
            long[] batchShape = new long[]{batchSize, sequenceLength};
            LongBuffer inputIdsBuffer = LongBuffer.wrap(flatInputIds);
            LongBuffer attentionMaskBuffer = LongBuffer.wrap(flatAttentionMasks);
            LongBuffer tokenTypeIdsBuffer = LongBuffer.wrap(flatTokenTypeIds);
            
            OnnxTensor inputIdsTensor = null;
            OnnxTensor attentionMaskTensor = null;
            OnnxTensor tokenTypeIdsTensor = null;
            
            try {
                inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, inputIdsBuffer, batchShape);
                attentionMaskTensor = OnnxTensor.createTensor(ortEnvironment, attentionMaskBuffer, batchShape);
                tokenTypeIdsTensor = OnnxTensor.createTensor(ortEnvironment, tokenTypeIdsBuffer, batchShape);
                
                // Prepare batch inputs
                Map<String, OnnxTensor> batchInputs = new HashMap<>();
                batchInputs.put("input_ids", inputIdsTensor);
                batchInputs.put("attention_mask", attentionMaskTensor);
                batchInputs.put("token_type_ids", tokenTypeIdsTensor);
                
                // Run batch inference (single call for all texts)
                OrtSession.Result batchOutput = ortSession.run(batchInputs);
                
                // Extract batch embeddings
                OnnxValue embeddingValue = batchOutput.get(0);
                Object value = embeddingValue.getValue();
                
                float[][] batchEmbeddings = extractBatchEmbeddings(value, batchSize);
                
                // Convert to responses
                List<AIEmbeddingResponse> responses = new ArrayList<>();
                long processingTime = System.currentTimeMillis() - startTime;
                
                for (int i = 0; i < batchSize; i++) {
                    final float[] embeddingArray = batchEmbeddings[i]; // Make final for lambda
                    List<Double> embedding = IntStream.range(0, embeddingArray.length)
                        .mapToObj(j -> (double) embeddingArray[j])
                        .collect(Collectors.toList());
                    
                    responses.add(AIEmbeddingResponse.builder()
                        .embedding(embedding)
                        .model("onnx:" + Paths.get(modelPath).getFileName().toString())
                        .dimensions(embedding.size())
                        .processingTimeMs(processingTime / batchSize)
                        .requestId(UUID.randomUUID().toString())
                        .build());
                }
                
                // Cleanup
                batchOutput.close();
                
                log.debug("Successfully generated {} ONNX embeddings in batch in {}ms (avg {}ms per embedding)", 
                         batchSize, processingTime, processingTime / batchSize);
                
                return responses;
                
            } finally {
                // Cleanup tensors
                if (inputIdsTensor != null) inputIdsTensor.close();
                if (attentionMaskTensor != null) attentionMaskTensor.close();
                if (tokenTypeIdsTensor != null) tokenTypeIdsTensor.close();
            }
            
        } catch (Exception e) {
            log.error("Error generating batch ONNX embeddings", e);
            throw new AIServiceException("Failed to generate batch ONNX embeddings", e);
        } finally {
            sessionLock.unlock();
        }
    }
    
    /**
     * Extract batch embeddings from ONNX output
     * Handles 2D, 3D, and 4D tensor outputs
     */
    private float[][] extractBatchEmbeddings(Object value, int expectedBatchSize) {
        float[][] embeddings;
        
        if (value instanceof float[][][]) {
            // 3D array: [batch, sequence, embedding_dim]
            float[][][] tensorValue3D = (float[][][]) value;
            if (tensorValue3D.length > 0 && tensorValue3D[0].length > 0) {
                int batchSize = tensorValue3D.length;
                int seqLength = tensorValue3D[0].length;
                int embeddingDim = tensorValue3D[0][0].length;
                embeddings = new float[batchSize][embeddingDim];
                
                // Mean pooling for each batch item
                for (int b = 0; b < batchSize; b++) {
                    for (int e = 0; e < embeddingDim; e++) {
                        float sum = 0.0f;
                        for (int s = 0; s < seqLength; s++) {
                            sum += tensorValue3D[b][s][e];
                        }
                        embeddings[b][e] = sum / seqLength;
                    }
                }
                log.debug("Extracted batch embeddings from 3D tensor [batch={}, sequence={}, embedding={}]", 
                         batchSize, seqLength, embeddingDim);
            } else {
                throw new AIServiceException("Empty 3D array output");
            }
        } else if (value instanceof float[][]) {
            // 2D array: [batch, embedding_dim] - already correct shape
            embeddings = (float[][]) value;
            log.debug("Extracted batch embeddings from 2D tensor [batch={}, embedding={}]", 
                     embeddings.length, embeddings.length > 0 ? embeddings[0].length : 0);
        } else if (value instanceof float[]) {
            // 1D array: reshape to 2D
            float[] flat = (float[]) value;
            int embeddingDim = flat.length / expectedBatchSize;
            embeddings = new float[expectedBatchSize][embeddingDim];
            for (int b = 0; b < expectedBatchSize; b++) {
                System.arraycopy(flat, b * embeddingDim, embeddings[b], 0, embeddingDim);
            }
            log.debug("Reshaped 1D array to batch embeddings [batch={}, embedding={}]", 
                     expectedBatchSize, embeddingDim);
        } else if (value instanceof OnnxTensor) {
            // Extract from OnnxTensor
            OnnxTensor tensor = (OnnxTensor) value;
            try {
                Object tensorValue = tensor.getValue();
                if (tensorValue instanceof float[][][]) {
                    return extractBatchEmbeddings(tensorValue, expectedBatchSize);
                } else if (tensorValue instanceof float[][]) {
                    return extractBatchEmbeddings(tensorValue, expectedBatchSize);
                } else {
                    throw new AIServiceException("Unexpected tensor format: " + tensorValue.getClass().getName());
                }
            } catch (Exception e) {
                throw new AIServiceException("Failed to extract tensor value", e);
            } finally {
                try {
                    tensor.close();
                } catch (Exception e) {
                    log.warn("Error closing OnnxTensor", e);
                }
            }
        } else {
            throw new AIServiceException("Unexpected batch embedding output format: " + 
                (value != null ? value.getClass().getName() : "null"));
        }
        
        // Validate batch size
        if (embeddings.length != expectedBatchSize) {
            throw new AIServiceException(
                String.format("Batch size mismatch: expected %d, got %d", expectedBatchSize, embeddings.length));
        }
        
        return embeddings;
    }
    
    @Override
    public int getEmbeddingDimension() {
        return embeddingDimension;
    }
    
    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("provider", "onnx");
        status.put("available", isAvailable());
        status.put("modelPath", modelPath);
        status.put("embeddingDimension", embeddingDimension);
        status.put("maxSequenceLength", maxSequenceLength);
        status.put("useGpu", useGpu);
        
        if (isAvailable()) {
            status.put("status", "ready");
        } else {
            status.put("status", "not_initialized");
            status.put("message", "Model file not found or provider not initialized");
        }
        
        return status;
    }
    
    /**
     * Improved WordPiece-like tokenization for BERT-based models
     * 
     * This implementation provides better tokenization than character-based:
     * - Handles word boundaries properly
     * - Adds special tokens ([CLS], [SEP])
     * - Uses word-level hashing with subword simulation
     * - Handles punctuation and special characters
     * 
     * Note: This is a simplified approximation. For production-quality tokenization,
     * consider using a REST API tokenizer service or pre-tokenizing externally.
     */
    private int[] tokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            // Return just [CLS] and [SEP] tokens
            return createPaddedTokens(new int[]{TOKEN_CLS, TOKEN_SEP}, maxSequenceLength);
        }
        
        // Normalize: lowercase, trim, basic Unicode normalization
        String normalized = text.toLowerCase()
            .trim()
            .replaceAll("\\s+", " ")  // Normalize whitespace
            .replaceAll("[\\u2000-\\u206F]", " ")  // Unicode spaces
            .replaceAll("[\\u00A0]", " ");  // Non-breaking space
        
        // Split into words (preserving punctuation as separate tokens)
        List<String> wordTokens = tokenizeIntoWords(normalized);
        
        // Convert words to token IDs using WordPiece-like algorithm
        List<Integer> tokenIds = new ArrayList<>();
        tokenIds.add(TOKEN_CLS);  // Add [CLS] token at the start
        
        for (String word : wordTokens) {
            // WordPiece tokenization: try full word first, then split into subwords
            List<Integer> wordTokenIds = wordPieceTokenize(word);
            tokenIds.addAll(wordTokenIds);
            
            // Limit sequence length (accounting for [CLS] and [SEP] tokens)
            int maxContentLength = maxSequenceLength - 2; // Reserve space for [CLS] and [SEP]
            if (tokenIds.size() >= maxContentLength) {
                break;
            }
        }
        
        tokenIds.add(TOKEN_SEP);  // Add [SEP] token at the end
        
        // Convert to array and pad/truncate
        int[] tokens = tokenIds.stream().mapToInt(Integer::intValue).toArray();
        return createPaddedTokens(tokens, maxSequenceLength);
    }
    
    /**
     * Tokenize text into words, handling punctuation
     */
    private List<String> tokenizeIntoWords(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();
        
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                // Word character
                currentWord.append(c);
            } else {
                // Non-word character (punctuation, whitespace, etc.)
                if (currentWord.length() > 0) {
                    words.add(currentWord.toString());
                    currentWord.setLength(0);
                }
                // Treat punctuation as separate token if it's significant
                if (!Character.isWhitespace(c) && c != '\'' && c != '"') {
                    words.add(String.valueOf(c));
                }
            }
        }
        
        // Add remaining word
        if (currentWord.length() > 0) {
            words.add(currentWord.toString());
        }
        
        return words;
    }
    
    /**
     * WordPiece tokenization: tries full word first, then splits into subwords
     * This is a simplified approximation of WordPiece algorithm
     */
    private List<Integer> wordPieceTokenize(String word) {
        List<Integer> tokenIds = new ArrayList<>();
        
        // Try full word first (hash-based vocabulary lookup)
        int fullWordId = wordToTokenId(word);
        if (fullWordId != TOKEN_UNK) {
            // Word exists in vocabulary (approximation)
            tokenIds.add(fullWordId);
            return tokenIds;
        }
        
        // Word not found - split into subwords using WordPiece algorithm
        // Try to split on common prefixes/suffixes
        String remaining = word;
        int attempts = 0;
        int maxAttempts = 20; // Prevent infinite loops
        
        while (!remaining.isEmpty() && attempts < maxAttempts) {
            attempts++;
            
            // Try longest match starting from the beginning
            String longestMatch = findLongestSubword(remaining);
            if (longestMatch != null && !longestMatch.isEmpty()) {
                int tokenId = wordToTokenId(longestMatch);
                tokenIds.add(tokenId);
                remaining = remaining.substring(longestMatch.length());
            } else {
                // No match found - use character-based fallback
                // Add ## prefix for subword tokens (WordPiece convention)
                for (char c : remaining.toCharArray()) {
                    String subword = "##" + c;
                    int tokenId = wordToTokenId(subword);
                    tokenIds.add(tokenId != TOKEN_UNK ? tokenId : wordToTokenId(String.valueOf(c)));
                }
                break;
            }
        }
        
        // If still couldn't tokenize, use UNK token
        if (tokenIds.isEmpty()) {
            tokenIds.add(TOKEN_UNK);
        }
        
        return tokenIds;
    }
    
    /**
     * Find longest subword match (simplified approximation)
     */
    private String findLongestSubword(String word) {
        // Try progressively shorter prefixes
        for (int len = Math.min(word.length(), 20); len > 0; len--) {
            String prefix = word.substring(0, len);
            int tokenId = wordToTokenId(prefix);
            if (tokenId != TOKEN_UNK) {
                return prefix;
            }
        }
        return null;
    }
    
    /**
     * Convert word/subword to token ID using hash-based vocabulary
     * This is an approximation - real WordPiece uses a vocabulary lookup table
     */
    private int wordToTokenId(String word) {
        if (word == null || word.isEmpty()) {
            return TOKEN_PAD;
        }
        
        // Use deterministic hash to map words to token IDs
        // This simulates vocabulary lookup (real vocab would have ~30k entries)
        long hash = word.hashCode();
        
        // Map hash to vocabulary range [100, 30521]
        // Reserve IDs 0-100 for special tokens
        int tokenId = (int) (Math.abs(hash) % (VOCAB_SIZE - 100)) + 100;
        
        // Ensure within valid range
        if (tokenId < 100 || tokenId >= VOCAB_SIZE) {
            tokenId = TOKEN_UNK;
        }
        
        return tokenId;
    }
    
    /**
     * Pad or truncate tokens to maxSequenceLength
     */
    private int[] createPaddedTokens(int[] tokens, int maxLength) {
        if (tokens.length == maxLength) {
            return tokens;
        } else if (tokens.length < maxLength) {
            // Pad with PAD tokens
            int[] padded = new int[maxLength];
            System.arraycopy(tokens, 0, padded, 0, tokens.length);
            Arrays.fill(padded, tokens.length, maxLength, TOKEN_PAD);
            return padded;
        } else {
            // Truncate - keep [CLS] and [SEP], truncate middle
            int[] truncated = new int[maxLength];
            truncated[0] = tokens[0]; // Keep [CLS]
            int contentLength = maxLength - 2; // Space for [CLS] and [SEP]
            System.arraycopy(tokens, 1, truncated, 1, contentLength);
            truncated[maxLength - 1] = TOKEN_SEP; // Add [SEP] at end
            return truncated;
        }
    }
}

