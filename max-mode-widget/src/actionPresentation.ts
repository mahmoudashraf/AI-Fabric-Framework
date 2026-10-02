import type { ComponentType } from "react";

export interface MaxModeActionPresentationFieldProjection {
  sourcePath: string;
  target?: string;
}

export interface MaxModeActionPresentationReferenceProjection {
  /** Public lookup value used only as buyer-facing context; the server must re-resolve it. */
  lookupField?: string;
  labelFields: string[];
  scope?: string;
}

export interface MaxModeActionPresentationObjectProjection {
  sourcePath: string;
  target: string;
  includeFields: string[];
  reference?: MaxModeActionPresentationReferenceProjection;
}

export interface MaxModeActionPresentationCollectionProjection {
  sourcePath: string;
  target: string;
  includeFields: string[];
  maxItems?: number;
  reference?: MaxModeActionPresentationReferenceProjection;
}

export interface MaxModeActionPresentationProjection {
  fields?: MaxModeActionPresentationFieldProjection[];
  objects?: MaxModeActionPresentationObjectProjection[];
  collections?: MaxModeActionPresentationCollectionProjection[];
}

export interface MaxModeActionPresentationMapping {
  actionName: string;
  rendererId: string;
  schemaVersion: string;
  projection: MaxModeActionPresentationProjection;
  /** Reviewed public configuration supplied to the renderer. */
  rendererContext?: Record<string, string | number | boolean>;
}

export interface MaxModeActionPresentationCommands {
  ask(input: { query: string; resultReferenceKeys?: string[] }): Promise<void>;
  attachResult(referenceKey: string): void;
  detachResult(referenceKey: string): void;
  navigate(input: { url: string; target?: "same-window" | "new-window" }): void;
}

export interface MaxModeActionPresentationComponentProps {
  actionName: string;
  rendererId: string;
  schemaVersion: string;
  presentationData: Readonly<Record<string, unknown>>;
  resultReferences: readonly MaxModePresentationResultReference[];
  selectedResultKeys: readonly string[];
  context: Readonly<Record<string, string | number | boolean>>;
  commands: MaxModeActionPresentationCommands;
}

export interface MaxModeReactActionRendererRegistration {
  id: string;
  kind: "react";
  component: ComponentType<MaxModeActionPresentationComponentProps>;
  schemaVersions?: string[];
}

export interface MaxModeCustomElementActionRendererRegistration {
  id: string;
  kind: "custom-element";
  elementName: string;
  schemaVersions?: string[];
}

export type MaxModeActionRendererRegistration =
  | MaxModeReactActionRendererRegistration
  | MaxModeCustomElementActionRendererRegistration;

export interface MaxModeActionPresentationConfig {
  renderers?: MaxModeActionRendererRegistration[];
  mappings?: MaxModeActionPresentationMapping[];
}

export interface MaxModePresentationResultReference {
  key: string;
  label: string;
  scope?: string;
  lookupValue?: string;
  sourceMessageId: string;
  sourceActionName: string;
  safeData: Readonly<Record<string, unknown>>;
}

export interface ResolvedActionPresentation {
  actionName: string;
  renderer: MaxModeActionRendererRegistration;
  rendererId: string;
  schemaVersion: string;
  presentationData: Readonly<Record<string, unknown>>;
  resultReferences: readonly MaxModePresentationResultReference[];
  context: Readonly<Record<string, string | number | boolean>>;
}

const MAX_COLLECTION_ITEMS = 24;
const MAX_ARRAY_VALUES = 64;
const MAX_STRING_LENGTH = 4_000;
const MAX_RENDERER_CONTEXT_ENTRIES = 32;
const SENSITIVE_CONTEXT_KEY = /(authorization|assertion|cookie|credential|password|private|secret|token|api.?key)/i;

export function resolveActionPresentation({
  config,
  actionName,
  actionData,
  messageId,
}: {
  config?: MaxModeActionPresentationConfig;
  actionName?: string;
  actionData: unknown;
  messageId: string;
}): ResolvedActionPresentation | null {
  if (!config || !actionName || !isRecord(actionData)) {
    return null;
  }

  const mapping = config.mappings?.find((candidate) => candidate.actionName === actionName);
  if (!mapping) {
    return null;
  }
  const renderer = config.renderers?.find((candidate) => candidate.id === mapping.rendererId);
  if (!renderer || !supportsSchema(renderer, mapping.schemaVersion)) {
    return null;
  }

  const projected = projectActionData(actionData, mapping.projection, messageId, actionName);
  if (!projected) {
    return null;
  }

  return {
    actionName,
    renderer,
    rendererId: mapping.rendererId,
    schemaVersion: mapping.schemaVersion,
    presentationData: Object.freeze(projected.data),
    resultReferences: Object.freeze(projected.references),
    context: Object.freeze(projectRendererContext(mapping.rendererContext)),
  };
}

export function extractActionName(value: unknown): string | undefined {
  if (!isRecord(value)) {
    return undefined;
  }
  return firstString(
    value.action,
    value.actionName,
    isRecord(value.actionResult) ? value.actionResult.action : undefined,
  );
}

export function isActionRendererAvailable(renderer: MaxModeActionRendererRegistration): boolean {
  if (renderer.kind === "react") {
    return typeof renderer.component === "function";
  }
  return isValidCustomElementName(renderer.elementName)
    && typeof customElements !== "undefined"
    && Boolean(customElements.get(renderer.elementName));
}

export function presentationReferenceAttachmentId(reference: MaxModePresentationResultReference): string {
  return `action-result:${reference.sourceMessageId}:${reference.key}`;
}

function supportsSchema(renderer: MaxModeActionRendererRegistration, schemaVersion: string): boolean {
  return !renderer.schemaVersions?.length || renderer.schemaVersions.includes(schemaVersion);
}

function projectActionData(
  actionData: Record<string, unknown>,
  projection: MaxModeActionPresentationProjection,
  messageId: string,
  actionName: string,
): { data: Record<string, unknown>; references: MaxModePresentationResultReference[] } | null {
  const data: Record<string, unknown> = {};
  const references: MaxModePresentationResultReference[] = [];

  for (const field of projection.fields || []) {
    const value = readPath(actionData, field.sourcePath);
    const safeValue = cloneSafeValue(value);
    if (safeValue !== undefined) {
      data[field.target || lastPathSegment(field.sourcePath)] = safeValue;
    }
  }

  for (const objectProjection of projection.objects || []) {
    const value = readPath(actionData, objectProjection.sourcePath);
    if (!isRecord(value)) {
      continue;
    }
    const projectedObject = projectRecord(value, objectProjection.includeFields);
    data[objectProjection.target] = projectedObject;
    if (objectProjection.reference) {
      references.push(buildReference({
        key: `${objectProjection.target}-0`,
        record: projectedObject,
        projection: objectProjection.reference,
        messageId,
        actionName,
      }));
      projectedObject.presentationKey = `${objectProjection.target}-0`;
    }
  }

  for (const collectionProjection of projection.collections || []) {
    const value = readPath(actionData, collectionProjection.sourcePath);
    if (!Array.isArray(value)) {
      continue;
    }
    const limit = Math.max(1, Math.min(collectionProjection.maxItems ?? MAX_COLLECTION_ITEMS, MAX_COLLECTION_ITEMS));
    const projectedItems = value
      .filter(isRecord)
      .slice(0, limit)
      .map((record, index) => {
        const item = projectRecord(record, collectionProjection.includeFields);
        if (collectionProjection.reference) {
          const key = `${collectionProjection.target}-${index}`;
          item.presentationKey = key;
          references.push(buildReference({
            key,
            record: item,
            projection: collectionProjection.reference,
            messageId,
            actionName,
          }));
        }
        return item;
      });
    data[collectionProjection.target] = projectedItems;
  }

  if (Object.keys(data).length === 0) {
    return null;
  }
  return { data, references };
}

function buildReference({
  key,
  record,
  projection,
  messageId,
  actionName,
}: {
  key: string;
  record: Record<string, unknown>;
  projection: MaxModeActionPresentationReferenceProjection;
  messageId: string;
  actionName: string;
}): MaxModePresentationResultReference {
  const label = projection.labelFields
    .map((field) => primitiveText(record[field]))
    .filter(Boolean)
    .join(" ") || "Selected result";
  const lookupValue = projection.lookupField
    ? primitiveText(record[projection.lookupField])
    : undefined;
  return Object.freeze({
    key,
    label,
    scope: projection.scope,
    lookupValue,
    sourceMessageId: messageId,
    sourceActionName: actionName,
    safeData: Object.freeze({ ...record }),
  });
}

function projectRecord(record: Record<string, unknown>, includeFields: string[]): Record<string, unknown> {
  const projected: Record<string, unknown> = {};
  for (const field of includeFields) {
    const value = readPath(record, field);
    const safeValue = cloneSafeValue(value);
    if (safeValue !== undefined) {
      projected[lastPathSegment(field)] = safeValue;
    }
  }
  return projected;
}

function projectRendererContext(
  context: Record<string, string | number | boolean> | undefined,
): Record<string, string | number | boolean> {
  if (!context) {
    return {};
  }
  return Object.fromEntries(
    Object.entries(context)
      .filter(([key, value]) => (
        !SENSITIVE_CONTEXT_KEY.test(key)
        && (typeof value === "string" || typeof value === "number" || typeof value === "boolean")
      ))
      .slice(0, MAX_RENDERER_CONTEXT_ENTRIES)
      .map(([key, value]) => [
        key,
        typeof value === "string" && value.length > MAX_STRING_LENGTH
          ? `${value.slice(0, MAX_STRING_LENGTH - 1)}...`
          : value,
      ]),
  );
}

function cloneSafeValue(value: unknown, depth = 0): unknown {
  if (value === null || typeof value === "boolean" || typeof value === "number") {
    return value;
  }
  if (typeof value === "string") {
    return value.length > MAX_STRING_LENGTH ? `${value.slice(0, MAX_STRING_LENGTH - 1)}...` : value;
  }
  if (depth >= 2) {
    return undefined;
  }
  if (Array.isArray(value)) {
    return value
      .slice(0, MAX_ARRAY_VALUES)
      .map((entry) => cloneSafeValue(entry, depth + 1))
      .filter((entry) => entry !== undefined);
  }
  if (isRecord(value)) {
    const safe: Record<string, unknown> = {};
    for (const [key, entry] of Object.entries(value).slice(0, MAX_ARRAY_VALUES)) {
      const cloned = cloneSafeValue(entry, depth + 1);
      if (cloned !== undefined) {
        safe[key] = cloned;
      }
    }
    return safe;
  }
  return undefined;
}

function readPath(value: unknown, path: string): unknown {
  if (!path.trim()) {
    return value;
  }
  return path.split(".").reduce<unknown>((current, segment) => {
    if (!isRecord(current)) {
      return undefined;
    }
    return current[segment];
  }, value);
}

function lastPathSegment(path: string): string {
  const segments = path.split(".");
  return segments[segments.length - 1] || path;
}

function primitiveText(value: unknown): string | undefined {
  if (typeof value === "string") {
    const trimmed = value.trim();
    return trimmed || undefined;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return undefined;
}

function firstString(...values: unknown[]): string | undefined {
  for (const value of values) {
    const text = primitiveText(value);
    if (text) {
      return text;
    }
  }
  return undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isValidCustomElementName(value: string): boolean {
  return /^[a-z][a-z0-9]*(?:-[a-z0-9]+)+$/.test(value);
}
