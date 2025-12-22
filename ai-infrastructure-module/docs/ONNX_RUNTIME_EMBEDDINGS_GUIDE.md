# ONNX Runtime for Local Embedding Generation

## What is ONNX Runtime?

**ONNX Runtime** is a cross-platform inference engine for running machine learning models. It's developed by Microsoft and optimized for production use.

### Key Features

- ✅ **Cross-platform**: Windows, Linux, macOS, iOS, Android
- ✅ **Multiple languages**: C++, C#, Python, Java, JavaScript
- ✅ **Optimized**: Uses hardware acceleration (CPU, GPU, NPU)
- ✅ **No Python dependency**: Pure Java/C++ execution
- ✅ **Production-ready**: Used by Microsoft, NVIDIA, Intel
- ✅ **Fast inference**: Optimized for real-time performance

---

> **Context:** Pair this runtime guide with [`ONNX_IMPLEMENTATION_APPROACH.md`](./ONNX_IMPLEMENTATION_APPROACH.md), [`ONNX_PRODUCTION_READINESS_ASSESSMENT.md`](./ONNX_PRODUCTION_READINESS_ASSESSMENT.md), and [`ONNX_OPTIONAL_STARTER_PLAN.md`](../../docs/ONNX_OPTIONAL_STARTER_PLAN.md) for architecture details, production hardening guidance, and packaging strategy.

## ONNX Format Explained

### What is ONNX?

**ONNX (Open Neural Network Exchange)** is an open format for machine learning models. It allows you to:

1. **Train model** in one framework (PyTorch, TensorFlow, etc.)
2. **Convert to ONNX format** (.onnx file)
3. **Run model** in any runtime (ONNX Runtime, TensorRT, etc.)

### Why ONNX?

**Problem**: Different ML frameworks have different formats
- PyTorch: `.pth` files
- TensorFlow: `.pb` files  
- Keras: `.h5` files
- Each requires different runtime libraries

**Solution**: ONNX is a **universal format**
- Train in PyTorch → Convert to ONNX → Run in Java
- Train in TensorFlow → Convert to ONNX → Run in C++
- One model, many runtimes

---

## ONNX Runtime for Embeddings

### Architecture

```
Your Java Application
    ↓
ONNX Runtime Java API
    ↓
ONNX Runtime Engine (Native C++)
    ↓
Hardware Acceleration
    ├─ CPU: Intel MKL, AVX2, AVX512
    ├─ GPU: CUDA, TensorRT
    └─ NPU: Apple Neural Engine, Qualcomm DSP
    ↓
Embedding Model (ONNX format)
    ↓
Output: Embedding Vector
```

### How It Works

1. **Model file** (`.onnx`) contains the neural network
2. **ONNX Runtime** loads the model into memory
3. **Input**: Text (converted to tokens/embeddings)
4. **Inference**: Neural network processes input
5. **Output**: Embedding vector (e.g., 384 or 768 dimensions)

---

## Getting Started with ONNX Runtime for Embeddings

### Step 1: Convert Sentence Transformer to ONNX

#### Option A: Use Hugging Face's Built-in Conversion

```python
from sentence_transformers import SentenceTransformer
import torch

# Load model
model = SentenceTransformer('all-MiniLM-L6-v2')

# Convert to ONNX
dummy_sentence = ["This is a dummy sentence"]
model.save_as_onnx("all-MiniLM-L6-v2.onnx", 
                   input_examples=dummy_sentence)
```

#### Option B: Manual Conversion with `optimum`

```bash
pip install optimum[onnxruntime]

optimum-cli export onnx \
    --model sentence-transformers/all-MiniLM-L6-v2 \
    --task feature-extraction \
    all-MiniLM-L6-v2-onnx
```

#### Option C: Download Pre-converted Models

Many models are already converted:
- **Hugging Face Model Hub**: Search for "onnx" + model name
- **ONNX Model Zoo**: Pre-converted models
- Example: `sentence-transformers/all-MiniLM-L6-v2` → ONNX version available

### Step 2: Add ONNX Runtime Dependency

#### Maven Dependency

```xml
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime</artifactId>
    <version>1.16.3</version>
</dependency>

<!-- For GPU support (optional) -->
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime_gpu</artifactId>
    <version>1.16.3</version>
</dependency>
```

#### Gradle Dependency

```gradle
implementation 'com.microsoft.onnxruntime:onnxruntime:1.16.3'
// For GPU:
implementation 'com.microsoft.onnxruntime:onnxruntime_gpu:1.16.3'
```

### Step 3: Download ONNX Model Files

You need two files:

1. **Model file** (`.onnx`): The neural network
   - Size: 80-500MB depending on model
   - Location: Bundled inside `providers/ai-infrastructure-onnx-starter/src/main/resources/models/embeddings/`

2. **Tokenizer files**: For text preprocessing
   - `tokenizer.json` or `vocab.txt`
   - Included alongside the model in the ONNX starter

**Download example**:
```bash
mkdir -p providers/ai-infrastructure-onnx-starter/src/main/resources/models/embeddings
cd providers/ai-infrastructure-onnx-starter/src/main/resources/models/embeddings

# Option 1: Download from Hugging Face
wget https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/model.onnx -O all-MiniLM-L6-v2.onnx

# Option 2: Use Hugging Face library
python -c "from huggingface_hub import hf_hub_download; hf_hub_download('sentence-transformers/all-MiniLM-L6-v2', 'model.onnx', local_dir='.', local_dir_use_symlinks=False)"
```

### Step 4: Adopt the Optional ONNX Starter

The optional starter module packages ONNX Runtime, the default MiniLM model, tokenizer assets, and Spring auto-configuration so teams can enable ONNX with a single dependency:

- Add the starter dependency to your Maven/Gradle build.
- Remove manually downloaded model/tokenizer files and rely on the starter's bundled resources.
- Continue overriding `ai.providers.onnx-*` properties when you need custom models or GPU execution.
- Follow the migration and release notes in [`ONNX_OPTIONAL_STARTER_PLAN.md`](../../docs/ONNX_OPTIONAL_STARTER_PLAN.md).

---

## Java Implementation

### Basic Example

```java
import ai.onnxruntime.*;
import java.nio.file.Paths;
import java.util.*;

public class ONNXEmbeddingService {
    
    private OrtEnvironment env;
    private OrtSession session;
    private OrtSession.SessionOptions opts;
    
    public ONNXEmbeddingService(String modelPath) throws OrtException {
        // Initialize ONNX Runtime environment
        env = OrtEnvironment.getEnvironment();
        opts = new OrtSession.SessionOptions();
        
        // Optional: Configure session
        // opts.setIntraOpNumThreads(4);  // CPU threads
        // opts.setExecutionMode(ExecutionMode.SEQUENTIAL);
        
        // Load model
        session = env.createSession(modelPath, opts);
        
        // Get model input/output info
        Map<String, NodeInfo> inputInfo = session.getInputInfo();
        Map<String, NodeInfo> outputInfo = session.getOutputInfo();
        
        System.out.println("Model loaded: " + modelPath);
        System.out.println("Inputs: " + inputInfo.keySet());
        System.out.println("Outputs: " + outputInfo.keySet());
    }
    
    public List<Double> generateEmbedding(String text) throws OrtException {
        // Step 1: Preprocess text (tokenize, convert to IDs)
        long[] inputIds = tokenizeText(text);
        long[] attentionMask = createAttentionMask(inputIds);
        
        // Step 2: Create ONNX tensors
        long[] shape = {1, inputIds.length};  // Batch size = 1
        
        OnnxTensor inputIdsTensor = OnnxTensor.createTensor(
            env, 
            LongBuffer.wrap(inputIds), 
            shape
        );
        
        OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(
            env, 
            LongBuffer.wrap(attentionMask), 
            shape
        );
        
        // Step 3: Prepare inputs (model-specific)
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", inputIdsTensor);
        inputs.put("attention_mask", attentionMaskTensor);
        
        // Step 4: Run inference
        OrtSession.Result outputs = session.run(inputs);
        
        // Step 5: Extract embedding
        float[][] embeddingArray = (float[][]) outputs.get(0).getValue();
        float[] embedding = embeddingArray[0];  // First (and only) item in batch
        
        // Step 6: Normalize (typical for embeddings)
        float[] normalizedEmbedding = normalizeVector(embedding);
        
        // Step 7: Convert to List<Double>
        List<Double> result = new ArrayList<>();
        for (float value : normalizedEmbedding) {
            result.add((double) value);
        }
        
        // Step 8: Clean up
        inputIdsTensor.close();
        attentionMaskTensor.close();
        outputs.close();
        
        return result;
    }
    
    private long[] tokenizeText(String text) {
        // Simple tokenization (you should use proper tokenizer)
        // In production, use the same tokenizer as the model was trained with
        
        String[] words = text.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", "")
            .split("\\s+");
        
        // Convert words to token IDs (simplified - use actual tokenizer)
        // Each sentence transformer model has its own tokenizer
        long[] tokenIds = new long[Math.min(words.length, 512)];  // Max 512 tokens
        for (int i = 0; i < tokenIds.length; i++) {
            tokenIds[i] = hashWordToId(words[i]);  // Simplified
        }
        
        return tokenIds;
    }
    
    private long[] createAttentionMask(long[] inputIds) {
        // Create attention mask (1 for real tokens, 0 for padding)
        long[] mask = new long[inputIds.length];
        Arrays.fill(mask, 1);
        return mask;
    }
    
    private float[] normalizeVector(float[] vector) {
        // L2 normalization (common for embeddings)
        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }
    
    private long hashWordToId(String word) {
        // Simplified - use actual tokenizer vocabulary
        return Math.abs(word.hashCode()) % 30000;  // Example vocab size
    }
    
    public void close() throws OrtException {
        if (session != null) {
            session.close();
        }
        if (opts != null) {
            opts.close();
        }
    }
}
```

> **Production reminder:** Stress-test concurrent access, add true batch execution, and surface metrics as outlined in [`ONNX_IMPLEMENTATION_APPROACH.md`](./ONNX_IMPLEMENTATION_APPROACH.md#production-hardening-roadmap) and [`ONNX_PRODUCTION_READINESS_ASSESSMENT.md`](./ONNX_PRODUCTION_READINESS_ASSESSMENT.md).

---

## Proper Tokenization

The above example uses simplified tokenization. For production, you need the actual tokenizer. Track the rollout plan and acceptance criteria in [`TOKENIZATION_IMPROVEMENT.md`](./TOKENIZATION_IMPROVEMENT.md).

### Option 1: Use Java Tokenizer Library

```xml
<dependency>
    <groupId>com.huggingface</groupId>
    <artifactId>tokenizers</artifactId>
    <version>0.13.3</version>
</dependency>
```

```java
import com.huggingface.tokenizers.Encoding;
import com.huggingface.tokenizers.Tokenizer;

public class ONNXEmbeddingService {
    private Tokenizer tokenizer;
    
    public ONNXEmbeddingService(String modelPath, String tokenizerPath) {
        // Load tokenizer
        tokenizer = Tokenizer.fromFile(tokenizerPath);
        
        // Load ONNX model
        session = env.createSession(modelPath, opts);
    }
    
    private long[] tokenizeText(String text) {
        Encoding encoding = tokenizer.encode(text);
        long[] tokenIds = encoding.getIds();  // Proper token IDs
        return tokenIds;
    }
}
```

### Option 2: Pre-tokenize in Python, Pass Token IDs

If you have Python available:

```python
from transformers import AutoTokenizer
tokenizer = AutoTokenizer.from_pretrained('sentence-transformers/all-MiniLM-L6-v2')

def tokenize(text):
    tokens = tokenizer(text, return_tensors='np', padding=True, truncation=True)
    return tokens['input_ids'], tokens['attention_mask']
```

Then pass token IDs to Java (via REST API or shared data).

---

## Complete Production Implementation

### ONNXEmbeddingProvider

```java
package com.ai.infrastructure.embedding;

import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.provider.AIProvider;
import ai.onnxruntime.*;
import com.huggingface.tokenizers.Tokenizer;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;
import java.util.*;

@Slf4j
public class ONNXEmbeddingProvider implements AIProvider {
    
    private static final String MODEL_PATH = "classpath:/models/embeddings/all-MiniLM-L6-v2.onnx";
    private static final String TOKENIZER_PATH = "classpath:/models/embeddings/tokenizer.json";
    
    private OrtEnvironment env;
    private OrtSession session;
    private Tokenizer tokenizer;
    private final int embeddingDimension;
    
    public ONNXEmbeddingProvider() throws Exception {
        log.info("Initializing ONNX Embedding Provider");
        
        // Initialize ONNX Runtime
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        
        // Configure for optimal performance
        opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
        opts.setInterOpNumThreads(1);
        opts.setOptimizationLevel(GraphOptimizationLevel.ALL);
        
        // Load model
        session = env.createSession(MODEL_PATH, opts);
        
        // Load tokenizer
        tokenizer = Tokenizer.fromFile(TOKENIZER_PATH);
        
        // Get embedding dimension from model output shape
        Map<String, NodeInfo> outputInfo = session.getOutputInfo();
        NodeInfo outputNodeInfo = outputInfo.values().iterator().next();
        long[] shape = ((TensorInfo) outputNodeInfo.getInfo()).getShape();
        embeddingDimension = (int) shape[shape.length - 1];
        
        log.info("ONNX Embedding Provider initialized. Dimension: {}", embeddingDimension);
    }
    
    @Override
    public String getProviderName() {
        return "onnx-local";
    }
    
    @Override
    public boolean isAvailable() {
        return session != null && tokenizer != null;
    }
    
    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        try {
            long startTime = System.currentTimeMillis();
            
            log.debug("Generating embedding with ONNX for text: {}", 
                     request.getText().substring(0, Math.min(50, request.getText().length())));
            
            // Step 1: Tokenize
            com.huggingface.tokenizers.Encoding encoding = tokenizer.encode(
                request.getText(),
                true,  // add_special_tokens
                512    // max_length
            );
            
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            
            // Pad to fixed length if needed
            int maxLength = 512;
            inputIds = padArray(inputIds, maxLength, 0);
            attentionMask = padArray(attentionMask, maxLength, 0);
            
            // Step 2: Create tensors
            long[] shape = {1, maxLength};  // Batch size = 1
            
            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(inputIds),
                shape
            );
            
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(attentionMask),
                shape
            );
            
            // Step 3: Prepare inputs
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);
            
            // Step 4: Run inference
            OrtSession.Result outputs = session.run(inputs);
            
            // Step 5: Extract embedding
            OnnxValue outputValue = outputs.get(0);
            float[][] embeddingArray = (float[][]) outputValue.getValue();
            float[] embedding = embeddingArray[0];  // First item in batch
            
            // Step 6: Pool embeddings (mean pooling for sentence transformers)
            float[] pooledEmbedding = meanPoolEmbeddings(embedding, attentionMask, embeddingDimension);
            
            // Step 7: Normalize
            float[] normalizedEmbedding = normalizeVector(pooledEmbedding);
            
            // Step 8: Convert to List<Double>
            List<Double> embeddingList = new ArrayList<>();
            for (float value : normalizedEmbedding) {
                embeddingList.add((double) value);
            }
            
            // Step 9: Clean up
            inputIdsTensor.close();
            attentionMaskTensor.close();
            outputs.close();
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.debug("Generated ONNX embedding in {}ms", processingTime);
            
            return AIEmbeddingResponse.builder()
                .embedding(embeddingList)
                .model("all-MiniLM-L6-v2-onnx")
                .dimensions(embeddingDimension)
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating ONNX embedding", e);
            throw new RuntimeException("Failed to generate ONNX embedding", e);
        }
    }
    
    private float[] meanPoolEmbeddings(float[] embeddings, long[] attentionMask, int dimension) {
        // Mean pooling: average embeddings of all tokens (weighted by attention mask)
        float[] pooled = new float[dimension];
        
        int tokenCount = embeddings.length / dimension;
        int activeTokens = 0;
        
        for (int i = 0; i < tokenCount; i++) {
            if (attentionMask[i] == 1) {
                activeTokens++;
                int startIdx = i * dimension;
                for (int j = 0; j < dimension; j++) {
                    pooled[j] += embeddings[startIdx + j];
                }
            }
        }
        
        // Average
        if (activeTokens > 0) {
            for (int i = 0; i < dimension; i++) {
                pooled[i] /= activeTokens;
            }
        }
        
        return pooled;
    }
    
    private float[] normalizeVector(float[] vector) {
        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        
        if (norm == 0.0) {
            return vector;
        }
        
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }
    
    private long[] padArray(long[] array, int targetLength, long padValue) {
        if (array.length >= targetLength) {
            long[] truncated = new long[targetLength];
            System.arraycopy(array, 0, truncated, 0, targetLength);
            return truncated;
        }
        
        long[] padded = new long[targetLength];
        System.arraycopy(array, 0, padded, 0, array.length);
        Arrays.fill(padded, array.length, targetLength, padValue);
        return padded;
    }
    
    @Override
    public void close() throws Exception {
        if (session != null) {
            session.close();
        }
        if (tokenizer != null) {
            tokenizer.close();
        }
    }
}
```

---

## Model Downloads & Setup

### Download Pre-converted Models

```bash
# Create models directory
mkdir -p models/embeddings
cd models/embeddings

# Download model (using Hugging Face Hub)
# Option 1: Use Python
python3 << EOF
from huggingface_hub import hf_hub_download
import os

model_name = "sentence-transformers/all-MiniLM-L6-v2"
os.makedirs("all-MiniLM-L6-v2", exist_ok=True)

# Download ONNX model
hf_hub_download(
    repo_id=model_name,
    filename="model.onnx",
    local_dir="all-MiniLM-L6-v2"
)

# Download tokenizer
hf_hub_download(
    repo_id=model_name,
    filename="tokenizer.json",
    local_dir="all-MiniLM-L6-v2"
)
EOF

# Option 2: Direct download
wget https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/model.onnx -O all-MiniLM-L6-v2.onnx
wget https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json -O tokenizer.json
```

### Convert Your Own Model

```python
from sentence_transformers import SentenceTransformer
import torch

# Load model
model_name = "all-MiniLM-L6-v2"
model = SentenceTransformer(model_name)

# Convert to ONNX
dummy_text = "This is a test sentence"
output_path = f"./models/{model_name}.onnx"

# Use Optimum for better conversion
from optimum.onnxruntime import ORTModelForFeatureExtraction
from optimum.onnxruntime.configuration import AutoOptimizationConfig
from optimum.onnxruntime import ORTQuantizer

# Convert
ort_model = ORTModelForFeatureExtraction.from_pretrained(
    model_name,
    export=True,
    provider="CPUExecutionProvider"
)

ort_model.save_pretrained(f"./models/{model_name}-onnx")
```

---

## Performance & Optimization

### CPU Optimization

```java
OrtSession.SessionOptions opts = new OrtSession.SessionOptions();

// Use all CPU cores
opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());

// Optimize graph
opts.setOptimizationLevel(GraphOptimizationLevel.ALL);

// Use optimized execution providers
opts.addCPU(true);  // Intel MKL optimizations
```

### GPU Support (Optional)

```java
// Use CUDA if available
if (OrtEnvironment.getAvailableProviders().contains("CUDAExecutionProvider")) {
    opts.addCUDA(0);  // Use first GPU
    log.info("Using GPU acceleration");
} else {
    log.info("Using CPU (GPU not available)");
}
```

### Batch Processing

```java
public List<List<Double>> generateEmbeddingsBatch(List<String> texts) {
    // Process multiple texts at once (much faster)
    // Batch size = texts.size()
    
    // Prepare batch inputs
    long[][] batchInputIds = texts.stream()
        .map(this::tokenizeText)
        .toArray(long[][]::new);
    
    // Create batch tensor
    long[] batchShape = {texts.size(), maxLength};
    OnnxTensor batchTensor = OnnxTensor.createTensor(env, batchData, batchShape);
    
    // Run inference once for entire batch
    OrtSession.Result outputs = session.run(Collections.singletonMap("input_ids", batchTensor));
    
    // Extract all embeddings
    return extractBatchEmbeddings(outputs);
}
```

**Performance**:
- **Single embedding**: 20-50ms (CPU), 5-10ms (GPU)
- **Batch (32 texts)**: 100-200ms (CPU), 20-40ms (GPU)
- **Throughput**: ~20-50 embeddings/second (CPU), ~200-500/second (GPU)

---

## Comparison: ONNX vs Other Options

| Feature | ONNX Runtime | Python Bridge | Docker REST |
|---------|--------------|---------------|-------------|
| **Setup Complexity** | Medium | Low | Low |
| **Performance** | Fastest | Fast | Fast |
| **Dependencies** | Native library | Python | Docker |
| **Memory Usage** | Low | Medium | Medium |
| **CPU/GPU Support** | ✅ Yes | ✅ Yes | ✅ Yes |
| **Pure Java** | ✅ Yes | ❌ No | ❌ No |
| **Production Ready** | ✅ Yes | ✅ Yes | ✅ Yes |

---

## Configuration

### application.yml

```yaml
  ai:
    embedding:
      provider: onnx  # onnx, openai, huggingface, local
      onnx:
        model-path: classpath:/models/embeddings/all-MiniLM-L6-v2.onnx
        tokenizer-path: classpath:/models/embeddings/tokenizer.json
        use-gpu: false  # Set to true if GPU available
        batch-size: 32
        max-sequence-length: 512
```

---

## Advantages of ONNX Runtime

1. **✅ Pure Java**: No Python dependency
2. **✅ Fast**: Optimized C++ engine
3. **✅ Cross-platform**: Works everywhere
4. **✅ Production-ready**: Used by major companies
5. **✅ GPU support**: Automatic acceleration
6. **✅ Memory efficient**: Lower overhead than Python
7. **✅ Thread-safe**: Can run in multi-threaded environment

---

## Disadvantages

1. **⚠️ Model conversion**: Need to convert models to ONNX
2. **⚠️ Tokenization**: Need proper tokenizer (can be complex)
3. **⚠️ Model files**: Need to download/store model files (~80-500MB)
4. **⚠️ Setup complexity**: More setup than Python bridge

---

## Recommendation

**Use ONNX Runtime if**:
- ✅ You want pure Java (no Python dependency)
- ✅ You need best performance
- ✅ You're deploying in production
- ✅ You want GPU acceleration

**Use Python Bridge/Docker if**:
- ✅ Quick prototyping needed
- ✅ You already have Python environment
- ✅ Less setup complexity is priority

---

## Production Readiness Checklist

Use this checklist to align runtime adoption with the production target described in the companion documentation.

| Focus | Action | Reference |
|-------|--------|-----------|
| Tokenization parity | Integrate Hugging Face tokenizers (or equivalent) and add regression tests that verify vocabulary alignment. | [`TOKENIZATION_IMPROVEMENT.md`](./TOKENIZATION_IMPROVEMENT.md) |
| Thread safety & batching | Run concurrent load tests, pool or synchronize ONNX sessions, and execute batch tensors in a single inference call. | [`ONNX_IMPLEMENTATION_APPROACH.md`](./ONNX_IMPLEMENTATION_APPROACH.md#production-hardening-roadmap) |
| Observability & resilience | Emit Micrometer metrics, expose health indicators, enforce rate limiting, and configure timeout/retry policies. | [`ONNX_PRODUCTION_READINESS_ASSESSMENT.md`](./ONNX_PRODUCTION_READINESS_ASSESSMENT.md) |
| Optional starter packaging | Migrate to the `ai-infrastructure-onnx-starter` module and update integration tests and docs to consume it. | [`ONNX_OPTIONAL_STARTER_PLAN.md`](../../docs/ONNX_OPTIONAL_STARTER_PLAN.md) |
| Performance extensions | Add GPU and INT8 execution paths, support lazy model loading, and normalize embeddings before persistence. | [`ONNX_IMPLEMENTATION_APPROACH.md`](./ONNX_IMPLEMENTATION_APPROACH.md#production-hardening-roadmap) |

---

## Quick Start Checklist

1. ✅ Add ONNX Runtime dependency to `pom.xml`
2. ✅ Download/convert model to ONNX format
3. ✅ Download tokenizer files
4. ✅ Create `ONNXEmbeddingProvider` class
5. ✅ Update configuration to use ONNX provider
6. ✅ Test with sample text
7. ✅ Integrate into `AIEmbeddingService`

---

## Example Usage

```java
// Initialize
ONNXEmbeddingProvider provider = new ONNXEmbeddingProvider();

// Generate embedding
AIEmbeddingRequest request = AIEmbeddingRequest.builder()
    .text("Hello world, this is a test")
    .model("all-MiniLM-L6-v2")
    .build();

AIEmbeddingResponse response = provider.generateEmbedding(request);
List<Double> embedding = response.getEmbedding();  // 384 dimensions

System.out.println("Embedding dimension: " + response.getDimensions());
System.out.println("Processing time: " + response.getProcessingTimeMs() + "ms");
```

This gives you **local embedding generation** with **no API calls** and **no Python dependency**! 🚀


