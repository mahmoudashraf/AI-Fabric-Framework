/** Presentation only: the original source remains unchanged for evidence/attachments. */
export function structuredSourceContent(content: string): object | null {
  try {
    const value: unknown = JSON.parse(content);
    return value !== null && typeof value === "object" ? value : null;
  } catch {
    return null;
  }
}

export function sourceContentPreview(content: string): string {
  const value = structuredSourceContent(content);
  if (!value) return content;
  return Array.isArray(value)
    ? `Structured evidence: ${value.length} record${value.length === 1 ? "" : "s"}. Open to inspect.`
    : `Structured evidence: ${Object.keys(value).length} field${Object.keys(value).length === 1 ? "" : "s"}. Open to inspect.`;
}

type SourceScore = { score?: number; similarity?: number; metadata?: unknown };

export function sourceScoreKind(document: SourceScore): string | null {
  const metadata = document.metadata;
  if (!metadata || typeof metadata !== "object" || Array.isArray(metadata)) return null;
  const scores = (metadata as Record<string, unknown>).retrievalScores;
  if (!scores || typeof scores !== "object" || Array.isArray(scores)) return null;
  const kind = (scores as Record<string, unknown>).scoreType;
  return kind === "RRF" || kind === "BM25" || kind === "VECTOR" ? kind : null;
}

export function sourceScoreLabel(document: SourceScore): string | null {
  const score = document.score ?? document.similarity;
  if (typeof score !== "number" || !Number.isFinite(score)) return null;
  const kind = sourceScoreKind(document);
  if (kind === "RRF") return "Combined search match";
  if (kind === "BM25") return "Word match";
  if (kind === "VECTOR") return "Meaning match";
  return `${(score * 100).toFixed(1)}% match`;
}

export function sourceScoreValue(document: SourceScore, value: number): string {
  return sourceScoreKind(document) ? value.toFixed(4) : `${(value * 100).toFixed(1)}%`;
}
