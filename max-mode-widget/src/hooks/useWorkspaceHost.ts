import { useEffect, useRef } from "react";
import type { ConversationControlView, ConversationDetail } from "../types";
import { bindWorkspaceHost, updateWorkspaceHost, WorkspaceHostError, type WorkspaceHostApi } from "../workspaceHost";

export function useWorkspaceHost({
  api, hydrated, isOpen, busy, conversationId, loadedConversation, control, loadConversation, clearTransientState, resetConversation,
}: {
  api?: WorkspaceHostApi;
  hydrated: boolean;
  isOpen: boolean;
  busy: boolean;
  conversationId: string | null;
  loadedConversation: ConversationDetail | null;
  control: ConversationControlView | null;
  loadConversation(id: string, refresh?: boolean): Promise<ConversationDetail>;
  clearTransientState(): void;
  resetConversation(): void;
}) {
  const latest = useRef({ conversationId, loadConversation, clearTransientState, resetConversation });
  const lastControlUpdate = useRef('');
  latest.current = { conversationId, loadConversation, clearTransientState, resetConversation };

  useEffect(() => {
    if (!api || !hydrated) return;
    return bindWorkspaceHost(api, {
      resetScope() { latest.current.resetConversation(); latest.current.clearTransientState(); },
      async openConversation(id) {
        latest.current.clearTransientState();
        const data = await latest.current.loadConversation(id);
        return { conversationId: id, controlState: data.controlState || null, controlVersion: data.controlVersion ?? null,
          canonicalRevision: (api.getState().canonicalRevision ?? 0) + 1 };
      },
      async refreshConversation() {
        // A caller may refresh immediately after awaiting open, before React's next render.
        const id = api.getState().conversationId;
        if (!id) throw new WorkspaceHostError("NO_CONVERSATION", "Open an existing conversation before refreshing it.");
        const data = await latest.current.loadConversation(id, true);
        return { conversationId: id, controlState: data.controlState || null, controlVersion: data.controlVersion ?? null,
          canonicalRevision: (api.getState().canonicalRevision ?? 0) + 1 };
      },
    });
  }, [api, hydrated]);

  useEffect(() => {
    if (!api || !hydrated) return;
    const currentControl = control?.conversationId === conversationId ? control : null;
    const controlUpdate = currentControl ? JSON.stringify([conversationId, currentControl.controlVersion,
      currentControl.updatedAt, Math.max(0, ...(currentControl.messages ?? []).map(message => message.sequence))]) : '';
    const canonicalChanged = !!controlUpdate && controlUpdate !== lastControlUpdate.current;
    lastControlUpdate.current = controlUpdate;
    updateWorkspaceHost(api, {
      isOpen, busy,
      conversationId,
      controlState: currentControl?.controlState || loadedConversation?.controlState || null,
      controlVersion: currentControl?.controlVersion ?? loadedConversation?.controlVersion ?? null,
      ...(canonicalChanged ? { canonicalRevision: (api.getState().canonicalRevision ?? 0) + 1 } : {}),
    });
  }, [api, hydrated, isOpen, busy, conversationId, loadedConversation, control]);
}
