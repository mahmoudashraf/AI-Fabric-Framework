export type ClarificationEdits = Record<string, string | boolean>;

export function clarificationDisplayValue(value: unknown): string | boolean {
  if (typeof value === "boolean") return value;
  if (value == null) return "";
  return typeof value === "object" ? JSON.stringify(value) : String(value);
}

export function clarificationValuePresent(value: unknown): boolean {
  return value != null && (typeof value !== "string" || value.trim().length > 0);
}

export function clarificationParameters(
  keys: readonly string[],
  provided: Record<string, unknown>,
  edits: ClarificationEdits,
): Record<string, unknown> {
  return Object.fromEntries(keys.map((key) => {
    // Unedited server-provided values keep their original JSON types and structure.
    if (!Object.prototype.hasOwnProperty.call(edits, key)) {
      return [key, Object.prototype.hasOwnProperty.call(provided, key) ? provided[key] : ""];
    }
    const value = edits[key];
    const original = provided[key];
    if (typeof original === "boolean") {
      if (typeof value !== "boolean") throw new Error(`${key} must be a boolean.`);
      return [key, value];
    }
    if (typeof original === "number") {
      const number = typeof value === "string" && value.trim() !== "" ? Number(value) : NaN;
      if (!Number.isFinite(number)) throw new Error(`${key} must be a number.`);
      return [key, number];
    }
    if (original !== null && typeof original === "object") {
      let parsed: unknown;
      try { parsed = typeof value === "string" ? JSON.parse(value) : undefined; } catch { /* Report a field error below. */ }
      if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed) !== Array.isArray(original)) {
        throw new Error(`${key} must be a JSON ${Array.isArray(original) ? "array" : "object"}.`);
      }
      return [key, parsed];
    }
    // Missing fields have no declared type here; never infer one from their text.
    return [key, value];
  }));
}
