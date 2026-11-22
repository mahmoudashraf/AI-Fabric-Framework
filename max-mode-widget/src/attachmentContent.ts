const MAX_CONTENT_CHARS = 2_000;
const MAX_DEPTH = 8;
const MAX_ENTRIES = 64;
const MAX_NODES = 512;
const OMITTED_FIELDS = new Set([
  "authorization", "proxyauthorization", "cookie", "setcookie", "password", "passwd",
  "credential", "credentials", "accesstoken", "refreshtoken", "idtoken", "sessiontoken",
  "securitytoken", "token", "apikey", "apisecret", "clientsecret", "secret", "secretkey",
  "privatekey", "signingkey", "requestheaders", "responseheaders", "executioncontext", "authcontext",
]);

export function explicitAttachmentContent(value: unknown): { value: unknown; text: string; truncated: boolean } {
  const ancestors = new Set<object>();
  let nodes = 0;
  let truncated = false;
  const visit = (entry: unknown, depth: number): unknown => {
    if (++nodes > MAX_NODES || depth > MAX_DEPTH) {
      truncated = true;
      return "[Truncated]";
    }
    if (entry === null || typeof entry === "boolean" || typeof entry === "number") return entry;
    if (typeof entry === "string") {
      if (entry.length <= MAX_CONTENT_CHARS) return entry;
      truncated = true;
      return `${entry.slice(0, MAX_CONTENT_CHARS)}[Truncated]`;
    }
    if (typeof entry !== "object" || entry === undefined) return undefined;
    if (ancestors.has(entry)) {
      truncated = true;
      return "[Circular reference omitted]";
    }
    ancestors.add(entry);
    let result: unknown;
    if (Array.isArray(entry)) {
      if (entry.length > MAX_ENTRIES) truncated = true;
      result = entry.slice(0, MAX_ENTRIES).map((child) => visit(child, depth + 1));
    } else {
      const entries = Object.entries(entry);
      if (entries.length > MAX_ENTRIES) truncated = true;
      result = Object.fromEntries(entries.slice(0, MAX_ENTRIES)
        .filter(([key]) => !OMITTED_FIELDS.has(key.replace(/[_-]/g, "").toLowerCase()))
        .map(([key, child]) => [key, visit(child, depth + 1)])
        .filter(([, child]) => child !== undefined));
    }
    ancestors.delete(entry);
    return result;
  };
  const retained = visit(value, 0);
  let text = JSON.stringify(retained) ?? "";
  const marker = "\n[Attachment truncated by size/depth limits]";
  if (text.length > MAX_CONTENT_CHARS) truncated = true;
  if (truncated) text = `${text.slice(0, MAX_CONTENT_CHARS - marker.length)}${marker}`;
  return { value: retained, text, truncated };
}
