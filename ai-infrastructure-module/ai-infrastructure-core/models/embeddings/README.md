# ONNX Embedding Model Files

This directory should contain the ONNX model files for local embedding generation.

## Required Files

1. **all-MiniLM-L6-v2.onnx** - The ONNX model file (~80MB)
2. **tokenizer.json** - The tokenizer configuration file (~455KB)

## How to Obtain Model Files

### Option 1: Using Python (Recommended)

```bash
# Install huggingface_hub
pip install huggingface_hub

# Download model (note: ONNX model is in 'onnx/' subdirectory on HuggingFace)
python -c "from huggingface_hub import hf_hub_download; import os; os.makedirs('.', exist_ok=True); hf_hub_download('sentence-transformers/all-MiniLM-L6-v2', 'onnx/model.onnx', local_dir='.', local_dir_use_symlinks=False); import shutil; shutil.copy('onnx/model.onnx', 'all-MiniLM-L6-v2.onnx')"

# Download tokenizer
python -c "from huggingface_hub import hf_hub_download; hf_hub_download('sentence-transformers/all-MiniLM-L6-v2', 'tokenizer.json', local_dir='.', local_dir_use_symlinks=False)"
```

### Option 1b: Using the Download Script (Easiest)

```bash
# From ai-infrastructure-module root
./scripts/download-onnx-model.sh
```

The script will automatically:
- Download the ONNX model and tokenizer
- Copy files to all necessary locations (core module and integration tests)

### Option 2: Convert from PyTorch Model

If you have access to the PyTorch model, convert it to ONNX:

```python
from sentence_transformers import SentenceTransformer
import torch

# Load model
model = SentenceTransformer('all-MiniLM-L6-v2')

# Export to ONNX (requires optimum library)
from optimum.onnxruntime import ORTModelForFeatureExtraction

# Convert and save
ort_model = ORTModelForFeatureExtraction.from_pretrained(
    'sentence-transformers/all-MiniLM-L6-v2',
    export=True
)
```

### Option 3: Use Pre-converted ONNX Models

Some pre-converted ONNX models are available at:
- https://github.com/onnx/models
- Various model repositories

## Model Information

- **Model**: sentence-transformers/all-MiniLM-L6-v2
- **Embedding Dimension**: 384
- **Max Sequence Length**: 512 tokens
- **Model Size**: ~80MB (ONNX format)
- **Quality**: Good balance between speed and quality

## Alternative Models

You can use other embedding models:
- `all-mpnet-base-v2` - Better quality, larger (~420MB, 768 dimensions)
- `all-MiniLM-L12-v2` - Better quality than L6, medium size (~120MB, 384 dimensions)

Just update the configuration in `application.yml`:

```yaml
ai:
  providers:
    onnx-model-path: ./models/embeddings/your-model.onnx
```

## Current Status

✅ Both files are present:
- `all-MiniLM-L6-v2.onnx` (~86MB)
- `tokenizer.json` (~455KB)

Files are included in git versioning and ready to use.

