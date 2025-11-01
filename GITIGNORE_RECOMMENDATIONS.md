# GitIgnore Recommendations

## Summary

Based on the project structure, the following files/directories should be added to `.gitignore`:

### ✅ Should be Ignored

1. **Lucene Index Files** (Runtime Generated)**
   - `**/data/lucene-vector-index/` - Lucene indexes generated at runtime
   - `**/data/test-lucene-index/` - Test indexes
   - All Lucene index files: `*.cfe`, `*.cfs`, `*.si`, `*.fdm`, `*.fdt`, `*.fdx`, `*.fnm`, `*.nvd`, `*.nvm`, `*.doc`, `*.pos`, `*.tim`, `*.tip`, `*.tmd`, `segments_*`
   - Lock files: `write.lock`, `*.lock`

2. **HuggingFace Cache** (Model Download Cache)
   - `**/models/**/.cache/` - HuggingFace download cache
   - Lock files in cache: `*.lock`

3. **Build Artifacts**
   - `target/` - Maven build output (already covered)
   - `*.class` - Compiled Java files
   - `*.jar`, `*.war`, `*.ear` - Build artifacts

4. **Data Directories** (Runtime Generated)
   - `ai-infrastructure-module/data/` - Runtime data
   - `backend/data/` - Runtime data
   - `**/integration-tests/data/` - Test data

### ❌ Should NOT be Ignored (Keep in Git)

1. **ONNX Model Files** (As requested by user)
   - `*.onnx` - ONNX model files are versioned
   - `tokenizer.json` - Tokenizer configuration is versioned
   - `**/models/embeddings/*.onnx` - Keep these files

2. **Configuration Files**
   - `*.yml`, `*.yaml` - Configuration files
   - `*.json` - Configuration JSON files (except lock files)

3. **Source Code**
   - `*.java` - Java source files
   - `*.ts`, `*.tsx` - TypeScript files

4. **Documentation**
   - `*.md` - Markdown documentation

## Updated .gitignore Files

The root `.gitignore` and `ai-infrastructure-module/.gitignore` have been updated to:

- ✅ Ignore Lucene index files
- ✅ Ignore HuggingFace cache
- ✅ Ignore runtime data directories
- ✅ Keep ONNX model files (.onnx)
- ✅ Keep tokenizer files (tokenizer.json)

