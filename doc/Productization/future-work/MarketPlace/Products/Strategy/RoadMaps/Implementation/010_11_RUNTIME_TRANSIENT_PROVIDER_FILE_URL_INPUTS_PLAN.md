# 010.11 Runtime Transient Provider File URL Inputs Plan

Status: Implemented for framework/runtime/provider contract on 2026-05-27; live ProdUS temporary URL proof remains an integration gate  
Parent plans: 010, 010.5, 010.7  
Primary consumer: ProdUS project creation analysis  
Scope: LoomAI runtime, core provider contract, provider adapters, debug/audit redaction

Implementation decision on 2026-05-26: provider support is fail-closed by default. Every configured provider must either pass temporary document URLs through a verified native file/document URL input path, use a provider-specific transient conversion path that does not persist/index/log/prompt-inline the document, or return explicit `documentUsage.status=NOT_USED` evidence with a provider/type reason. No provider may silently ignore a supplied document and still report it as used.

Implementation update on 2026-05-27: runtime extraction and provider propagation are implemented for both `/api/chat/me/query` and `/api/chat/me/query-once`. OpenAI uses provider-native `input_file.file_url` for document/spreadsheet/text-like files and `input_image.image_url` for images; Azure OpenAI uses Responses file inputs with transient PDF `file_data` and native image URL inputs; Anthropic uses URL blocks for PDFs and images; Gemini fetches approved short-lived URLs transiently and sends provider `inlineData` for supported text, PDF, image, audio, and video types; Cohere uses transient text/PDF extraction into Cohere `documents`; unsupported provider/type combinations fail closed with `NOT_USED`.

## Purpose

Support owner-approved, short-lived document URLs as provider-native file inputs across LoomAI runtime providers.

This plan replaces the rejected MCP document-read approach for ProdUS project creation documents. ProdUS will send selected documents in the canonical runtime request context as temporary HTTPS file URLs. LoomAI must pass those URLs to the configured LLM provider as typed file/document URL inputs where the provider supports it.

The runtime must not:

- read document bytes into LoomAI prompt text,
- index or vectorize the document,
- persist the temporary URL,
- log the temporary URL,
- expose the temporary URL in debug payloads,
- mark a document as used unless the provider actually used it and returned owner-safe evidence.

## Non-Goals

- Do not build a ProdUS-specific action or MCP document-read tool.
- Do not add RAG indexing for private project creation files.
- Do not add a generic LoomAI indexing or durable fetch-and-convert path.
- Do not put fetched document text into the normal prompt string. Provider-specific document fields are allowed when the provider lacks a native URL field and the adapter remains transient.
- Do not store temporary file content in chat history, vector stores, prompt previews, or audit rows.
- Do not force all providers to pretend support if their public API does not accept external file/document URLs.

## Endpoint Policy

Both runtime endpoints may accept transient document URL inputs:

- `POST /api/chat/me/query`
- `POST /api/chat/me/query-once`

`/query-once` remains the recommended path for ProdUS project creation analysis because it intentionally avoids persisted conversation history. However, the feature is not limited to `/query-once`.

For `/query`, persistence rules are stricter:

- conversation turn may persist the final answer as usual,
- `documentUsage` may persist only with redacted document metadata,
- temporary URLs must not persist,
- raw provider request bodies must not persist,
- temporary file content must not persist,
- prompt preview must not include document URLs or document text.

Short-lived URLs are still treated as secrets because they are bearer-style access URLs. Redaction is required even if the URL TTL is only minutes.

## Canonical Request Shape

No top-level field is required initially. The first supported shape is the existing canonical context document array:

```json
{
  "query": "Create a product draft from this owner brief",
  "conversationId": "optional-correlation-or-chat-id",
  "mode": "support_assistant",
  "position": "project_creation",
  "context": {
    "contextVersion": "produs-project-creation-v1",
    "documentSharingPolicy": {
      "scope": "project-creation-analysis-only",
      "indexing": "not-allowed",
      "retention": "do-not-store-document-content",
      "access": "temporary-url-selected-by-owner",
      "ttl": "minutes"
    },
    "documents": [
      {
        "attachmentId": "f0d47c2b-00ef-4068-9bb2-c4423a5a76d3",
        "fileName": "owner-brief.pdf",
        "contentType": "application/pdf",
        "sizeBytes": 123456,
        "temporaryAccessUrl": "https://produs-api-staging.example.com/api/product-attachments/ai-access/<token>",
        "expiresAt": "2026-05-26T16:45:00Z",
        "providerInputHint": "typed-file-url"
      }
    ],
    "outputContract": {
      "format": "strict-json-object",
      "fields": ["documentUsage"]
    }
  }
}
```

Future option: introduce a first-class `transientInputs.documents[]` request field after the behavior is proven. The initial implementation should avoid forcing ProdUS to change its already agreed context contract.

## Internal Contract

Add provider-agnostic transient input DTOs in core:

```java
public class AIGenerationRequest {
    ...
    private List<AIGenerationInputPart> inputParts;
    private TransientInputPolicy transientInputPolicy;
}
```

```java
public class AIGenerationInputPart {
    private AIGenerationInputType type; // TEXT, FILE_URL
    private String text;
    private String url;
    private String documentId;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    private Instant expiresAt;
    private Map<String, String> metadata;
}
```

```java
public class TransientInputPolicy {
    private boolean doNotPersistUrls;
    private boolean doNotPersistContent;
    private boolean doNotIndex;
    private boolean redactFromLogs;
    private boolean requireDocumentUsage;
}
```

The pipeline should build normal text prompts as it does today, then append typed file URL parts to the provider request through `AIGenerationRequest.inputParts`. `AttachmentPromptAugmentationStep` must not transform these file URLs into prompt text.

## Runtime Extraction

Add a runtime extraction step before orchestration generation:

1. Read `context.documents[]`.
2. Select entries with `temporaryAccessUrl`.
3. Validate policy:
   - `documentSharingPolicy.indexing == not-allowed`
   - `documentSharingPolicy.retention == do-not-store-document-content`
   - `temporaryAccessUrl` is HTTPS
   - localhost, private IP, link-local, and metadata-service hosts are always rejected
   - host is checked against `ai.fabric.runtime.transient-file-url.allowed-hosts` when that deployment allowlist is configured
   - `expiresAt`, when present, is not expired and remains near-term
   - file count and declared size are under deployment limits
4. Convert valid entries into `AIGenerationInputPart(type=FILE_URL)`.
5. Replace `context.documents[].temporaryAccessUrl` with a redacted placeholder before storing request context metadata.
6. Attach sanitized document descriptors to canonical debug metadata:
   - `documentId`
   - `fileName`
   - `contentType`
   - `sizeBytes`
   - `expiresAt`
   - `urlRedacted=true`

## Provider Capability Matrix

| Provider module | Provider-native external URL support | Planned behavior |
|---|---:|---|
| `ai-infrastructure-provider-openai` | Yes, Responses API file and image inputs. | Route `FILE_URL` inputs through Responses. PDFs/text/Office-style documents use `input_file.file_url`; images use `input_image.image_url`; unsupported types fail closed. |
| `ai-infrastructure-provider-azure` | Conditional; Azure Responses supports file inputs via `file_data`/`file_id` and image URL inputs. | Use Responses for supported inputs: transiently fetch PDFs and pass base64 `file_data`; pass image URLs as native image URL inputs. Unsupported types fail closed. |
| `ai-infrastructure-provider-anthropic` | Yes for supported URL document/image blocks. | Map PDFs to Anthropic `document` URL blocks and images to `image` URL blocks; unsupported types return `NOT_USED`. |
| `ai-infrastructure-provider-gemini` | Yes for multimodal inline parts; direct arbitrary external URLs are not assumed safe/portable. | Validate and transiently fetch approved HTTPS URLs, then send supported text, PDF, image, audio, and video bytes as `inlineData`. Unsupported/oversized types return `NOT_USED`. |
| `ai-infrastructure-provider-cohere` | No matching provider-fetched raw file URL contract; Cohere Chat supports `documents`. | For text-like files and PDFs, transiently fetch/extract and pass content through Cohere `documents`; images/audio/video/unsupported binaries fail closed. |
| `ai-infrastructure-onnx-starter` | Embeddings only; no chat/document URL generation. | Not applicable; fail closed if selected for generation. |

Provider support means one of two outcomes:

- native support: pass file URL as provider-typed input,
- unsupported support: return a controlled `documentUsage` result that says the file was not used and why.

No provider may silently drop documents and still claim success.

## Provider Adapter Design

### OpenAI

When `inputParts` contains `FILE_URL`, route generation through Responses API instead of Chat Completions.

Request content:

```json
{
  "model": "gpt-5.5",
  "input": [
    {
      "role": "user",
      "content": [
        {
          "type": "input_text",
          "text": "<normal generated prompt>"
        },
        {
          "type": "input_file",
          "file_url": "<temporaryAccessUrl>"
        },
        {
          "type": "input_image",
          "image_url": "<temporaryImageUrl>"
        }
      ]
    }
  ]
}
```

Chat Completions remains available for text-only calls until the provider is fully migrated.

### Azure OpenAI

Use Azure Responses API for transient file inputs.

Rules:

- PDF inputs: fetch bytes transiently, encode as `data:application/pdf;base64,...`, and pass as `input_file.file_data`.
- Image inputs: pass as provider-native `input_image.image_url` where supported.
- Text-like non-PDF inputs: fail closed unless Azure adds a provider-native document field for those types.
- Never log or persist the URL, file bytes, base64 payload, or extracted content.
- Keep Chat Completions for text-only calls.

### Anthropic

Map supported `FILE_URL` inputs to Anthropic URL content blocks where supported.

Rules:

- Accept supported MIME types only: PDFs and common web image formats.
- Map PDFs to `document` blocks and images to `image` blocks.
- If the provider rejects a type, return `documentUsage.status=NOT_USED` for that file.
- Do not download the URL into LoomAI.

### Gemini

Use Gemini multimodal inline parts for provider-accessible temporary URLs.

Rules:

- Validate the short-lived URL with the same HTTPS/private-network rules as other adapters.
- Fetch bytes transiently into memory only.
- Send supported text, PDF, image, audio, and video data as provider `inlineData`.
- Do not persist/log the URL, file bytes, or base64 payload.
- Return `NOT_USED` for unsupported or oversized content.

### Cohere

Cohere does not expose a provider-fetched temporary file URL input equivalent to OpenAI `input_file.file_url`. Cohere Chat does support a `documents` field, so this adapter may fetch text-like documents transiently and pass them as provider document objects.

Rules:

- Accept text-like content types such as `text/*`, JSON, XML, YAML, CSV, and HTML.
- Accept PDFs through transient PDF text extraction.
- Strip basic HTML tags before sending HTML as a document.
- Send extracted PDF text only if readable text is present.
- Send content through Cohere `documents`, not through the prompt text.
- Keep per-document character limits to prevent provider-context blowups.
- If any document is unsupported, expired, too large, or fetch fails, return controlled `NOT_USED` document usage for that file/request.
- Do not log or persist the fetched content.

## Document Usage Contract

The runtime response must include one `documentUsage` item per requested document.

```json
{
  "fileName": "owner-brief.pdf",
  "documentId": "f0d47c2b-00ef-4068-9bb2-c4423a5a76d3",
  "status": "USED",
  "accessMethod": "TEMPORARY_URL",
  "evidence": ["Owner-safe fact extracted from the file."],
  "reason": "Provider accepted the temporary file URL and used it in the answer."
}
```

Required values:

- `status`: `USED` or `NOT_USED`
- `accessMethod`: `TEMPORARY_URL` or `NONE`
- `evidence`: owner-safe facts only; no raw private file excerpts unless the product contract allows it
- `reason`: required for both `USED` and `NOT_USED`

Validation rules:

- `USED` requires at least one non-empty owner-safe evidence item.
- `TEMPORARY_URL` requires that a file URL input was sent to the provider for that document.
- Unsupported provider/type must produce `NOT_USED`.
- Expired/invalid URL must produce `NOT_USED`.
- Missing `documentUsage` when documents were supplied is a response-contract failure.

## Security And Privacy

Temporary URLs are treated as secret-bearing values.

Must redact from:

- application logs,
- provider request logs,
- provider error logs,
- prompt preview,
- debug inspector,
- persisted conversation request metadata,
- audit payload details,
- traces exported to support bundles.

Allowed to persist:

- document id,
- file name,
- content type,
- size,
- expiry timestamp,
- `documentUsage` status,
- owner-safe evidence,
- provider request id,
- redaction marker.

Not allowed to persist:

- full temporary URL,
- URL token/path secret,
- raw document bytes,
- raw document text,
- provider request body containing URL.

## Runtime History Behavior

For `/query-once`:

- no chat turn persisted,
- no memory loaded,
- no conversation created,
- temporary URL available only in the in-memory request path.

For `/query`:

- normal answer can persist,
- sanitized `documentUsage` can persist,
- temporary URL must be stripped before any conversation persistence,
- persisted turn should record only `documentInputCount` and redacted descriptors.

This lets normal chat use provider-file documents without leaking temporary access URLs into long-lived history.

## Debug Behavior

Debug inspector may show:

- `transientDocuments.count`,
- per-document sanitized descriptor,
- provider capability decision,
- provider route selected, such as `openai.responses` or `anthropic.messages.document_url`,
- `documentUsage`,
- whether URL was redacted.

Debug inspector must not show:

- `temporaryAccessUrl`,
- provider request body with file URL,
- raw file content,
- raw provider error containing URL.

## Implementation Slices

### Slice 1: Core Contract

Files:

- `ai-infrastructure-core/.../dto/AIGenerationRequest.java`
- new DTOs under `ai-infrastructure-core/.../dto/`
- provider interface capability additions

Work:

- Add typed transient input DTOs.
- Add provider capability enum or method.
- Add redaction helpers for transient inputs.
- Add unit tests for DTO serialization and redaction.

### Slice 2: Runtime Extraction And Sanitization

Files:

- `ai-fabric-runtime/.../ChatRuntimeController.java`
- orchestration context metadata helpers
- prompt/debug sanitization utilities

Work:

- Extract `context.documents[].temporaryAccessUrl`.
- Validate URL and policy.
- Attach `FILE_URL` input parts to generation request.
- Redact URLs from request context metadata before persistence.
- Add tests for `/query` and `/query-once`.

### Slice 3: Orchestration Propagation

Files:

- `IntentHandlingStep.java`
- `ReadActionResolutionService.java` if needed for analysis mode
- pipeline context/generation request builders

Work:

- Carry transient inputs from orchestration metadata to `AIGenerationRequest`.
- Ensure attachment prompt augmentation does not consume transient file URLs.
- Ensure prompt preview excludes URLs.

### Slice 4: OpenAI Provider

Files:

- `OpenAIProvider.java`
- `OpenAIProviderTest.java`

Work:

- Add Responses API request builder.
- Use Responses only when file URL inputs are present.
- Map text prompt plus file URL parts.
- Parse output text into existing `AIGenerationResponse`.
- Add payload tests verifying `input_file.file_url`.
- Add log tests or assertions that URL is redacted.

### Slice 5: Anthropic Provider

Files:

- `AnthropicProvider.java`
- provider tests

Work:

- Add document URL block mapping for supported MIME types.
- Add unsupported type fail-closed behavior.
- Add tests for PDF URL mapping and unsupported type handling.

### Slice 6: Gemini Provider

Files:

- `GeminiProvider.java`
- provider tests

Work:

- Add URL Context/document URL mapping for supported models.
- Add feature flag if model support differs.
- Add tests for URL context request shape and unsupported handling.

### Slice 7: Azure Provider

Files:

- `AzureOpenAIProvider.java`
- provider tests

Work:

- Add Azure Responses route for transient file inputs.
- Fetch PDF bytes transiently and send `input_file.file_data`.
- Pass image URLs as `input_image.image_url` where supported.
- Fail closed for unsupported content types.
- Add tests proving file bytes/URLs are not logged or persisted in metadata.

### Slice 8: Cohere And ONNX

Files:

- `CohereProvider.java`
- ONNX/provider selection handling

Work:

- Detect `FILE_URL` inputs.
- Fetch supported text-like URLs transiently.
- Pass fetched text via Cohere `documents`, not prompt text.
- Return controlled unsupported result for unsupported/binary types rather than silently ignoring documents.

### Slice 9: Document Usage Enforcement

Files:

- runtime canonical response builder,
- response contract validation utilities,
- ProdUS-specific prompt/policy config if needed.

Work:

- Require `documentUsage` when transient documents are supplied.
- Validate `USED` evidence.
- Convert missing/invalid usage to safe failure or `NOT_USED` result.
- Do not claim file analysis unless evidence exists.

### Slice 10: Live Verification

Work:

- Deploy managed ProdUS staging runtime.
- Call `/api/chat/me/query-once` with a real short-lived ProdUS temporary URL.
- Call `/api/chat/me/query` with the same shape and verify the URL is not persisted.
- Verify OpenAI route uses Responses API.
- Verify unsupported provider path returns `NOT_USED`.
- Verify logs do not contain the temporary URL token.
- Verify debug output shows redacted document descriptors and `documentUsage`.

## Tests

Required unit tests:

- accepts valid `context.documents[].temporaryAccessUrl`,
- rejects non-HTTPS URL,
- rejects private IP / localhost / metadata URL,
- rejects non-allowlisted host,
- strips URL before persistence for `/query`,
- keeps URL out of `/query-once` history because no history is written,
- keeps URL out of prompt preview,
- keeps URL out of debug payload,
- OpenAI maps to `input_file.file_url`,
- Anthropic maps supported URL document types,
- Gemini maps supported URL inputs,
- Azure disabled capability returns `NOT_USED`,
- Cohere returns `NOT_USED`,
- missing `documentUsage` fails response contract.

Required integration tests:

- `/api/chat/me/query-once` with transient document returns strict JSON and no conversation.
- `/api/chat/me/query` with transient document returns strict JSON and persisted conversation contains no URL.
- Provider fallback path returns safe `NOT_USED` document usage.

## Release Gates

This feature is complete only when:

- all provider modules handle `FILE_URL` inputs either natively or fail closed,
- `/query` and `/query-once` both accept the contract,
- no temporary URL appears in persisted conversation rows,
- no temporary URL appears in logs during test runs,
- no temporary URL appears in debug inspector,
- ProdUS receives `documentUsage` per selected file,
- at least one real OpenAI live test proves provider-native file URL analysis,
- at least one unsupported provider test proves honest `NOT_USED` behavior.

## Documentation Updates

After implementation, update:

- `Final_Documentation/Development_Guides/PRIVATE_RUNTIME_CUSTOMER_INTEGRATION_GUIDE.md`
- `Final_Documentation/Development_Guides/PRODUS_LOOMAI_STAGING_DEPLOYMENT_DEV_GUIDE.md`
- ProdUS handoff docs, if still maintained in the sibling repo
- provider matrix guide

## Open Review Questions

1. Should we allow `context.documents[]` for all positions, or only deployment-configured positions such as `project_creation`?
2. Should provider file URL capability be declared per deployment profile, per provider config, or both?
3. For `/query`, should persisted `documentUsage.evidence` be retained by default, or should evidence also be redacted unless the deployment explicitly allows it?
4. Should Azure be enabled only after a live proof on the exact Azure deployment shape we use?
5. Should ProdUS temporary URL host allowlisting be global platform config or deployment-specific config?

## Initial Recommendation

Implement the generic core and runtime contract first, then enable providers incrementally:

1. OpenAI first because it directly matches the requested `input_file.file_url` contract.
2. Anthropic and Gemini next with provider-specific URL document mappings.
3. Azure only behind an explicit capability flag until live verified.
4. Cohere and ONNX fail closed until their provider APIs support provider-fetched temporary document URLs.

This gives ProdUS a clean architecture now without coupling LoomAI to ProdUS or incorrectly indexing private files.
