/**
 * Self-contained MaxMode context for the widget.
 * No external provider dependency — the widget manages its own state.
 */
import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from "react";
import React from "react";

export interface SharedAttachment {
  type: string;
  data: any;
}

export interface MaxModeState {
  chatMessages: any[];
  attachedItems: SharedAttachment[];
  currentPosition: "landing" | "catalog" | "search" | "cart";
  currentMode: "navigator" | "copilot";
  conversationId: string | null;
  contextDocuments: any[];
}

interface MaxModeContextType {
  maxModeState: MaxModeState;
  pendingAttachments: SharedAttachment[];
  setMaxModeState: (state: MaxModeState) => void;
  updateMaxModeState: (updates: Partial<MaxModeState>) => void;
  addPendingAttachment: (attachment: SharedAttachment) => void;
  clearPendingAttachments: () => void;
  getPendingAttachments: () => SharedAttachment[];
  persistState: () => void;
  loadPersistedState: () => MaxModeState | null;
  clearPersistedState: () => void;
}

const defaultState: MaxModeState = {
  chatMessages: [],
  attachedItems: [],
  currentPosition: "landing",
  currentMode: "navigator",
  conversationId: null,
  contextDocuments: [],
};

const STORAGE_KEY = "maxmode_widget_state";
const PENDING_ATTACHMENTS_KEY = "maxmode_widget_pending_attachments";

const MaxModeContext = createContext<MaxModeContextType | null>(null);

export function MaxModeProvider({ children }: { children: ReactNode }) {
  const [maxModeState, setMaxModeStateInternal] =
    useState<MaxModeState>(defaultState);
  const [pendingAttachments, setPendingAttachments] = useState<
    SharedAttachment[]
  >([]);

  useEffect(() => {
    const persisted = loadFromStorage();
    if (persisted) setMaxModeStateInternal(persisted);

    const pending = loadPendingFromStorage();
    if (pending?.length) setPendingAttachments(pending);
  }, []);

  const loadFromStorage = (): MaxModeState | null => {
    try {
      const stored = sessionStorage.getItem(STORAGE_KEY);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  };

  const loadPendingFromStorage = (): SharedAttachment[] => {
    try {
      const stored = sessionStorage.getItem(PENDING_ATTACHMENTS_KEY);
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  };

  const persistState = useCallback(() => {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(maxModeState));
    } catch {}
  }, [maxModeState]);

  const loadPersistedState = useCallback((): MaxModeState | null => {
    return loadFromStorage();
  }, []);

  const clearPersistedState = useCallback(() => {
    try {
      sessionStorage.removeItem(STORAGE_KEY);
      sessionStorage.removeItem(PENDING_ATTACHMENTS_KEY);
      setMaxModeStateInternal(defaultState);
      setPendingAttachments([]);
    } catch {}
  }, []);

  const setMaxModeState = useCallback((state: MaxModeState) => {
    setMaxModeStateInternal(state);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {}
  }, []);

  const updateMaxModeState = useCallback((updates: Partial<MaxModeState>) => {
    setMaxModeStateInternal((prev) => {
      const newState = { ...prev, ...updates };
      try {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(newState));
      } catch {}
      return newState;
    });
  }, []);

  const addPendingAttachment = useCallback((attachment: SharedAttachment) => {
    setPendingAttachments((prev) => {
      const exists = prev.some(
        (a) =>
          a.type === attachment.type &&
          (a.data.id === attachment.data.id ||
            a.data.sku === attachment.data.sku),
      );
      if (exists) return prev;
      const newAttachments = [...prev, attachment];
      try {
        sessionStorage.setItem(
          PENDING_ATTACHMENTS_KEY,
          JSON.stringify(newAttachments),
        );
      } catch {}
      return newAttachments;
    });
  }, []);

  const clearPendingAttachments = useCallback(() => {
    setPendingAttachments([]);
    try {
      sessionStorage.removeItem(PENDING_ATTACHMENTS_KEY);
    } catch {}
  }, []);

  const getPendingAttachments = useCallback(() => {
    return [...pendingAttachments];
  }, [pendingAttachments]);

  return React.createElement(
    MaxModeContext.Provider,
    {
      value: {
        maxModeState,
        pendingAttachments,
        setMaxModeState,
        updateMaxModeState,
        addPendingAttachment,
        clearPendingAttachments,
        getPendingAttachments,
        persistState,
        loadPersistedState,
        clearPersistedState,
      },
    },
    children,
  );
}

export function useMaxModeContext() {
  const context = useContext(MaxModeContext);
  if (!context) {
    throw new Error(
      "useMaxModeContext must be used within a MaxModeProvider",
    );
  }
  return context;
}

export function useMaxModeContextOptional() {
  return useContext(MaxModeContext);
}
