import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Dispatch, SetStateAction } from "react";

import {
  cancelConversationHandover,
  configuredHandoverIntake,
  conversationHandoverEnabled,
  fetchConversationControl,
  openConversationEvents,
  requestConversationHandover,
  retryConversationHandover,
  resumeAiConversation,
  type HandoverIntakeSchema,
} from "@/api/handover";
import { createConversationId } from "@/api/chat";
import { isPublicRuntimeSessionInvalidatedError } from "@/api/client";
import { getWidgetConfig } from "@/config";
import type { ChatMessage, ConversationControlView, RuntimeConversationMessage } from "@/types";

type ToastFn = (opts: any) => void;

export function useConversationHandover({
  isOpen,
  currentConversationId,
  setCurrentConversationId,
  setChatMessages,
  setSuggestions,
  setShowSuggestions,
  toast,
}: {
  isOpen: boolean;
  currentConversationId: string | null;
  setCurrentConversationId: Dispatch<SetStateAction<string | null>>;
  setChatMessages: Dispatch<SetStateAction<ChatMessage[]>>;
  setSuggestions: Dispatch<SetStateAction<string[]>>;
  setShowSuggestions: Dispatch<SetStateAction<boolean>>;
  toast: ToastFn;
}) {
  const enabled = conversationHandoverEnabled();
  const [control, setControl] = useState<ConversationControlView | null>(null);
  const [intakeSchema, setIntakeSchema] = useState<HandoverIntakeSchema | null>(null);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [isBusy, setIsBusy] = useState(false);
  const latestMessageSequence = useRef(0);
  const eventCursor = useRef<string | null>(null);
  const previousConversationId = useRef<string | null>(currentConversationId);
  const activeConversation = useRef(currentConversationId);
  const acceptedControl = useRef<ConversationControlView | null>(null);
  activeConversation.current = currentConversationId;

  const applyControl = useCallback((view: ConversationControlView | null | undefined) => {
    if (!view?.conversationId || !view.controlState) return;
    if (view.conversationId !== activeConversation.current) return;
    if (acceptedControl.current?.conversationId === view.conversationId
      && acceptedControl.current.controlVersion > view.controlVersion) return;
    acceptedControl.current = view;
    setControl(view);
    const runtimeMessages = Array.isArray(view.messages) ? view.messages : [];
    for (const message of runtimeMessages) {
      latestMessageSequence.current = Math.max(latestMessageSequence.current, Number(message.sequence) || 0);
    }
    const humanMessages = runtimeMessages.filter((message) => message.role === "HUMAN");
    if (humanMessages.length > 0) {
      setChatMessages((existing) => mergeHumanMessages(existing, humanMessages, view.safeHumanDisplayName));
    }
    if (view.controlState !== "AI_ACTIVE") {
      setSuggestions([]);
      setShowSuggestions(false);
    }
  }, [setChatMessages, setShowSuggestions, setSuggestions]);

  const ensureConversationId = useCallback(() => {
    if (currentConversationId) return currentConversationId;
    if (getWidgetConfig().hostSession) return null;
    const created = createConversationId();
    activeConversation.current = created;
    setCurrentConversationId(created);
    return created;
  }, [currentConversationId, setCurrentConversationId]);

  const refreshControl = useCallback(async () => {
    if (!enabled || !currentConversationId) return null;
    const view = await fetchConversationControl(currentConversationId, latestMessageSequence.current);
    applyControl(view);
    return view;
  }, [applyControl, currentConversationId, enabled]);

  const submitHandover = useCallback(async (
    intake: Record<string, string>,
    consentedFields: string[],
    continueAnonymously: boolean,
  ) => {
    if (!enabled || getWidgetConfig().host?.readOnly) return;
    const conversationId = ensureConversationId();
    if (!conversationId) return;
    setIsBusy(true);
    try {
      const admission = await requestConversationHandover(conversationId, {
        reasonCode: "USER_REQUESTED",
        continueAnonymously,
        intake,
        consentedFields,
      });
      applyControl(admission.conversation);
      if (admission.outcome === "HANDOVER_INTAKE_REQUIRED" && admission.intakeSchema) {
        setIntakeSchema(admission.intakeSchema);
        setIsDialogOpen(true);
        return;
      }
      setIsDialogOpen(false);
      toast({
        title: admission.conversation.controlState === "HUMAN_ACTIVE" ? "Human support active" : "Connecting to a person",
        description: admission.message || "Your conversation is being transferred without losing its history.",
      });
    } catch (error) {
      if (isPublicRuntimeSessionInvalidatedError(error)) return;
      toast({
        title: "Unable to request human support",
        description: error instanceof Error ? error.message : "Please try again.",
        variant: "destructive",
      });
    } finally {
      setIsBusy(false);
    }
  }, [applyControl, enabled, ensureConversationId, toast]);

  const openHandover = useCallback(() => {
    if (!enabled || isBusy || getWidgetConfig().host?.readOnly) return;
    if (getWidgetConfig().hostSession && !currentConversationId) return;
    if (control?.controlState && control.controlState !== "AI_ACTIVE") {
      setIsDialogOpen(true);
      return;
    }
    const configured = configuredHandoverIntake();
    if (configured && configured.fields.length > 0) {
      ensureConversationId();
      setIntakeSchema(configured);
      setIsDialogOpen(true);
      return;
    }
    void submitHandover({}, [], configured?.anonymousAllowed !== false);
  }, [control?.controlState, currentConversationId, enabled, ensureConversationId, isBusy, submitHandover]);

  const cancelHandover = useCallback(async () => {
    if (!currentConversationId || !control?.handoverId || isBusy || getWidgetConfig().host?.readOnly) return;
    setIsBusy(true);
    try {
      applyControl(await cancelConversationHandover(currentConversationId, control.handoverId));
      setIsDialogOpen(false);
      toast({ title: "Human support request cancelled", description: "The AI assistant can respond again." });
    } catch (error) {
      if (!isPublicRuntimeSessionInvalidatedError(error)) {
        toast({
          title: "Unable to cancel the request",
          description: error instanceof Error ? error.message : "Please try again.",
          variant: "destructive",
        });
      }
    } finally {
      setIsBusy(false);
    }
  }, [applyControl, control?.handoverId, currentConversationId, isBusy, toast]);

  const resumeAi = useCallback(async () => {
    if (!currentConversationId || isBusy || getWidgetConfig().host?.readOnly) return;
    setIsBusy(true);
    try {
      applyControl(await resumeAiConversation(currentConversationId));
      setIsDialogOpen(false);
      toast({ title: "AI assistant resumed", description: "You can continue in the same conversation." });
    } catch (error) {
      if (!isPublicRuntimeSessionInvalidatedError(error)) {
        toast({
          title: "Unable to return to AI",
          description: error instanceof Error ? error.message : "Please try again.",
          variant: "destructive",
        });
      }
    } finally {
      setIsBusy(false);
    }
  }, [applyControl, currentConversationId, isBusy, toast]);

  const retryHandover = useCallback(async () => {
    if (!currentConversationId || !control?.handoverId || isBusy || getWidgetConfig().host?.readOnly) return;
    setIsBusy(true);
    try {
      applyControl(await retryConversationHandover(currentConversationId, control.handoverId));
      toast({ title: "Human support request retried", description: "The same conversation remains queued without returning control to AI." });
    } catch (error) {
      if (!isPublicRuntimeSessionInvalidatedError(error)) {
        toast({
          title: "Unable to retry the request",
          description: error instanceof Error ? error.message : "Please try again.",
          variant: "destructive",
        });
      }
    } finally {
      setIsBusy(false);
    }
  }, [applyControl, control?.handoverId, currentConversationId, isBusy, toast]);

  const syncFromRuntimeResponse = useCallback((response: any) => {
    if (!enabled || !response || typeof response !== "object") return;
    const candidates = [
      response.metadata?.conversationControl,
      response.conversation,
      response.result?.data?.conversation,
      response.result?.sanitizedPayload?.data?.actionResult?.data?.conversation,
    ];
    const view = candidates.find(isConversationControlView);
    if (view) applyControl(view);

    const intakeCandidates = [
      response.intakeSchema,
      response.result?.data?.intakeSchema,
      response.result?.sanitizedPayload?.data?.actionResult?.data?.intakeSchema,
    ];
    const intake = intakeCandidates.find(isHandoverIntakeSchema);
    if (intake) {
      setIntakeSchema(intake);
      setIsDialogOpen(true);
    }
    if (!view && currentConversationId) void refreshControl().catch(() => undefined);
  }, [applyControl, currentConversationId, enabled, refreshControl]);

  useEffect(() => {
    latestMessageSequence.current = 0;
    eventCursor.current = null;
    const previous = previousConversationId.current;
    previousConversationId.current = currentConversationId;
    if (!currentConversationId || (previous && previous !== currentConversationId)) {
      setControl(null);
      setIntakeSchema(null);
      setIsDialogOpen(false);
    }
  }, [currentConversationId]);

  useEffect(() => {
    if (!enabled || !isOpen || !currentConversationId) return;
    let stopped = false;
    const abort = new AbortController();

    const connect = async () => {
      while (!stopped) {
        try {
          await refreshControl();
          const response = await openConversationEvents(currentConversationId, eventCursor.current, abort.signal);
          if (!response.ok || !response.body) throw new Error(`Conversation event stream returned HTTP ${response.status}.`);
          await consumeEventStream(response.body, (event) => {
            if (event.id) eventCursor.current = event.id;
            void refreshControl().catch(() => undefined);
          }, abort.signal);
        } catch (error) {
          if (stopped || abort.signal.aborted || isPublicRuntimeSessionInvalidatedError(error)) return;
          console.warn("[MaxMode] Conversation handover event stream will reconnect:", error);
        }
        await delay(1_000, abort.signal);
      }
    };
    void connect();
    return () => {
      stopped = true;
      abort.abort();
    };
  }, [currentConversationId, enabled, isOpen, refreshControl]);

  const controlState = control?.controlState ?? "AI_ACTIVE";
  const blocksAi = controlState === "HUMAN_PENDING"
    || controlState === "HUMAN_ACTIVE"
    || controlState === "AI_RESUME_PENDING";

  return useMemo(() => ({
    handoverEnabled: enabled && !getWidgetConfig().host?.readOnly,
    conversationControl: control,
    conversationControlState: controlState,
    handoverBlocksAi: blocksAi,
    handoverIntakeSchema: intakeSchema,
    isHandoverDialogOpen: isDialogOpen,
    setIsHandoverDialogOpen: setIsDialogOpen,
    isHandoverBusy: isBusy,
    openHandover,
    submitHandover,
    cancelHandover,
    retryHandover,
    resumeAi,
    refreshConversationControl: refreshControl,
    syncConversationControlFromResponse: syncFromRuntimeResponse,
  }), [
    blocksAi,
    cancelHandover,
    control,
    controlState,
    enabled,
    intakeSchema,
    isBusy,
    isDialogOpen,
    openHandover,
    refreshControl,
    retryHandover,
    resumeAi,
    submitHandover,
    syncFromRuntimeResponse,
  ]);
}

function mergeHumanMessages(
  existing: ChatMessage[],
  incoming: RuntimeConversationMessage[],
  fallbackDisplayName?: string | null,
) {
  const known = new Set(existing.map((message) => message.id));
  const additions = incoming
    .filter((message) => !known.has(message.messageId))
    .map((message): ChatMessage => ({
      id: message.messageId,
      type: "ai",
      participantRole: "human",
      participantDisplayName: message.participantDisplayName
        || stringValue(message.metadata?.safeActorDisplayName)
        || fallbackDisplayName
        || "Human support",
      content: message.content,
      timestamp: message.createdAt,
      success: true,
    }));
  return additions.length > 0 ? [...existing, ...additions] : existing;
}

function isConversationControlView(value: unknown): value is ConversationControlView {
  if (!value || typeof value !== "object") return false;
  const candidate = value as ConversationControlView;
  return typeof candidate.conversationId === "string"
    && typeof candidate.controlVersion === "number"
    && ["AI_ACTIVE", "HUMAN_PENDING", "HUMAN_ACTIVE", "AI_RESUME_PENDING", "CLOSED"].includes(candidate.controlState);
}

function isHandoverIntakeSchema(value: unknown): value is HandoverIntakeSchema {
  if (!value || typeof value !== "object") return false;
  const candidate = value as HandoverIntakeSchema;
  return typeof candidate.anonymousAllowed === "boolean" && Array.isArray(candidate.fields);
}

async function consumeEventStream(
  stream: ReadableStream<Uint8Array>,
  onEvent: (event: { id: string | null; type: string | null; data: string }) => void,
  signal: AbortSignal,
) {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (!signal.aborted) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, "\n");
      let boundary = buffer.indexOf("\n\n");
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        const event = parseEvent(block);
        if (event) onEvent(event);
        boundary = buffer.indexOf("\n\n");
      }
    }
  } finally {
    reader.releaseLock();
  }
}

function parseEvent(block: string) {
  let id: string | null = null;
  let type: string | null = null;
  const data: string[] = [];
  for (const line of block.split("\n")) {
    if (line.startsWith(":")) continue;
    const separator = line.indexOf(":");
    const field = separator >= 0 ? line.slice(0, separator) : line;
    const value = separator >= 0 ? line.slice(separator + 1).replace(/^ /, "") : "";
    if (field === "id") id = value;
    if (field === "event") type = value;
    if (field === "data") data.push(value);
  }
  return id || type || data.length > 0 ? { id, type, data: data.join("\n") } : null;
}

function delay(milliseconds: number, signal: AbortSignal) {
  return new Promise<void>((resolve) => {
    if (signal.aborted) return resolve();
    const timeout = window.setTimeout(resolve, milliseconds);
    signal.addEventListener("abort", () => {
      window.clearTimeout(timeout);
      resolve();
    }, { once: true });
  });
}

function stringValue(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}
