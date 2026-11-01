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
