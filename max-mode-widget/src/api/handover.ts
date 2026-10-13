import { getWidgetConfig } from "@/config";
import type { MaxModeHandoverIntakeFieldConfig } from "@/config";
import type { ConversationControlView } from "@/types";
import { apiFetchJson, apiFetchResponse } from "./client";

export interface HandoverIntakeSchema {
  schemaVersion?: string;
  anonymousAllowed: boolean;
  fields: MaxModeHandoverIntakeFieldConfig[];
}

export interface HandoverAdmission {
  outcome: "HANDOVER_INTAKE_REQUIRED" | "HANDOVER_REQUESTED" | "IDEMPOTENT_ACTIVE_HANDOVER" | string;
  conversation: ConversationControlView;
  intakeSchema?: HandoverIntakeSchema | null;
  code?: string;
  message?: string;
}

export interface HandoverRequestPayload {
  reasonCode?: string;
  continueAnonymously: boolean;
  intake: Record<string, string>;
  consentedFields: string[];
}

type HandoverRouteName =
  | "handoverRequestUrlTemplate"
  | "handoverCancelUrlTemplate"
  | "handoverRetryUrlTemplate"
  | "conversationControlUrlTemplate"
  | "conversationEventsUrlTemplate"
  | "aiResumeRequestUrlTemplate";

export function conversationHandoverEnabled() {
  const config = getWidgetConfig().apiConfig;
  const routes = config.runtimeRoutes;
  return config.conversationHandover?.enabled === true
    && Boolean(routes?.conversationMessageUrlTemplate)
    && Boolean(routes?.handoverRequestUrlTemplate)
    && Boolean(routes?.handoverCancelUrlTemplate)
    && Boolean(routes?.handoverRetryUrlTemplate)
    && Boolean(routes?.conversationControlUrlTemplate)
    && Boolean(routes?.conversationEventsUrlTemplate)
    && Boolean(routes?.aiResumeRequestUrlTemplate);
}

export function configuredHandoverIntake(): HandoverIntakeSchema | null {
  const intake = getWidgetConfig().apiConfig.conversationHandover?.intake;
  if (!intake) return null;
  return {
    anonymousAllowed: intake.anonymousAllowed !== false,
    fields: Array.isArray(intake.fields) ? intake.fields.slice(0, 16) : [],
  };
}

export async function requestConversationHandover(
  conversationId: string,
  payload: HandoverRequestPayload,
) {
  const response = await apiFetchResponse(route("handoverRequestUrlTemplate", conversationId), {
    method: "POST",
    headers: mutationHeaders("handover"),
    body: JSON.stringify(payload),
  });
  const body = await response.json().catch(() => null) as HandoverAdmission | null;
  if (response.status !== 422 && !response.ok) {
    throw new Error(`Handover request failed (${response.status}): ${body?.message || response.statusText}`);
  }
  if (!body) throw new Error("Handover request returned no response.");
  return body;
}

export async function cancelConversationHandover(
  conversationId: string,
  handoverId: string,
) {
  return apiFetchJson<ConversationControlView>(
    route("handoverCancelUrlTemplate", conversationId, handoverId),
    { method: "POST", headers: mutationHeaders("handover-cancel") },
  );
}

export async function retryConversationHandover(
  conversationId: string,
  handoverId: string,
) {
  return apiFetchJson<ConversationControlView>(
    route("handoverRetryUrlTemplate", conversationId, handoverId),
    { method: "POST", headers: mutationHeaders("handover-retry") },
  );
}

export async function resumeAiConversation(conversationId: string) {
  return apiFetchJson<ConversationControlView>(
    route("aiResumeRequestUrlTemplate", conversationId),
    { method: "POST", headers: mutationHeaders("ai-resume") },
  );
}

export async function fetchConversationControl(conversationId: string, afterMessageSequence = 0) {
  const path = `${route("conversationControlUrlTemplate", conversationId)}?afterMessageSequence=${Math.max(0, afterMessageSequence)}`;
  return apiFetchJson<ConversationControlView>(path);
}

export function openConversationEvents(
  conversationId: string,
  lastEventId: string | null,
  signal: AbortSignal,
) {
  return apiFetchResponse(route("conversationEventsUrlTemplate", conversationId), {
    method: "GET",
    headers: {
      Accept: "text/event-stream",
      ...(lastEventId ? { "Last-Event-ID": lastEventId } : {}),
    },
    cache: "no-store",
    signal,
  });
}

function route(name: HandoverRouteName, conversationId: string, handoverId?: string) {
  const template = getWidgetConfig().apiConfig.runtimeRoutes?.[name]?.trim();
  if (!template) throw new Error(`Conversation handover route ${name} is not configured.`);
  const resolved = template
    .replace("{conversationId}", encodeURIComponent(conversationId.trim()))
    .replace("{handoverId}", encodeURIComponent(handoverId?.trim() || ""));
  if (resolved.includes("{")) throw new Error(`Conversation handover route ${name} is incomplete.`);
  return resolved;
}

function mutationHeaders(prefix: string) {
  const suffix = typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
  return {
    "Content-Type": "application/json",
    "Idempotency-Key": `${prefix}-${suffix}`,
  };
}
