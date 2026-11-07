import type { MaxModeHandoverIntakeFieldConfig } from "./config";

const FIELD_TYPES = new Set(["TEXT", "EMAIL", "PHONE", "BOOLEAN", "SELECT"]);

/** Projects deployment-editor and runtime field contracts into one contact-form model. */
export function adaptHandoverIntakeFields(value: unknown): {
  fields: MaxModeHandoverIntakeFieldConfig[];
  issues: string[];
} {
  if (!Array.isArray(value)) return { fields: [], issues: ["Support contact fields are not configured as a list."] };
  const fields: MaxModeHandoverIntakeFieldConfig[] = [];
  const issues: string[] = [];
  const seen = new Set<string>();
  value.forEach((item, index) => {
    if (!item || typeof item !== "object" || Array.isArray(item)) {
      issues.push(`Support field ${index + 1} has an invalid configuration.`);
      return;
    }
    const field = item as Record<string, unknown>;
    const key = typeof field.key === "string" ? field.key.trim()
      : typeof field.id === "string" ? field.id.trim() : "";
    const label = typeof field.label === "string" ? field.label.trim() : "";
    const identifier = label || key || `Support field ${index + 1}`;
    let type = typeof field.type === "string" ? field.type.trim().toUpperCase() : "";
    if (type === "TEL") type = "PHONE";
    if (!key || !label || seen.has(key.toLowerCase())
      || (typeof field.key === "string" && typeof field.id === "string" && field.key.trim() !== field.id.trim())) {
      issues.push(`${identifier}: missing, conflicting or duplicate field identity.`);
      return;
    }
    seen.add(key.toLowerCase());
    if (!FIELD_TYPES.has(type)) {
      issues.push(`${identifier}: unsupported contact field type ${String(field.type ?? "(missing)")}.`);
      return;
    }
    fields.push({ ...field, key, label, type } as unknown as MaxModeHandoverIntakeFieldConfig);
  });
  return { fields, issues };
}
