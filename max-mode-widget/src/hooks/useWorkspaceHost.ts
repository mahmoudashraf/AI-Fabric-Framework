import { useEffect, useRef } from "react";
import type { ConversationControlView, ConversationDetail } from "../types";
import { bindWorkspaceHost, updateWorkspaceHost, WorkspaceHostError, type WorkspaceHostApi } from "../workspaceHost";

export function useWorkspaceHost({
  api, hydrated, isOpen, busy, conversationId, loadedConversation, control, loadConversation, clearTransientState,
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
}) {
  const latest = useRef({ conversationId, loadConversation, clearTransientState });
  latest.current = { conversationId, loadConversation, clearTransientState };

  useEffect(() => {
    if (!api || !hydrated) return;
    return bindWorkspaceHost(api, {
      async openConversation(id) {
        latest.current.clearTransientState();
        const data = await latest.current.loadConversation(id);
        return { conversationId: id, controlState: data.controlState || null, controlVersion: data.controlVersion ?? null };
      },
      async refreshConversation() {
        // A caller may refresh immediately after awaiting open, before React's next render.
        const id = api.getState().conversationId;
        if (!id) throw new WorkspaceHostError("NO_CONVERSATION", "Open an existing conversation before refreshing it.");
        const data = await latest.current.loadConversation(id, true);
        return { conversationId: id, controlState: data.controlState || null, controlVersion: data.controlVersion ?? null };
      },
    });
  }, [api, hydrated]);

  useEffect(() => {
    if (!api || !hydrated) return;
    const currentControl = control?.conversationId === conversationId ? control : null;
    updateWorkspaceHost(api, {
      isOpen, busy,
      conversationId,
      controlState: currentControl?.controlState || loadedConversation?.controlState || null,
      controlVersion: currentControl?.controlVersion ?? loadedConversation?.controlVersion ?? null,
    });
  }, [api, hydrated, isOpen, busy, conversationId, loadedConversation, control]);
}
