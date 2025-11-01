# ONNX Embedding Model Files for Integration Tests

This directory should contain the ONNX model files for integration testing.

## Setup

Copy the model files from the main models directory:

```bash
# From ai-infrastructure-module root
cp models/embeddings/*.onnx integration-tests/models/embeddings/
cp models/embeddings/tokenizer.json integration-tests/models/embeddings/
```

Or use the download script:

```bash
cd ../../..  # Go to ai-infrastructure-module root
./scripts/download-onnx-model.sh
```

See `ai-infrastructure-core/models/embeddings/README.md` for more details on obtaining the model files.



