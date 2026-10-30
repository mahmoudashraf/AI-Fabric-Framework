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
import { getWidgetConfig, getWidgetStorageKey, type MaxModeWidgetConfig } from "./config";

export interface SharedAttachment {
  type: string;
  data: any;
  /** Optional host-owned label for contextual tool scope presentation. */
  contextLabel?: string;
}

export interface MaxModeState {
  chatMessages: any[];
  attachedItems: SharedAttachment[];
  currentPosition: "landing" | "catalog" | "search" | "cart";
  currentMode: "navigator" | "navigator_deep" | "thinker_deep" | "cart_assistant" | "executor";
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

export function MaxModeProvider({ children, config }: { children: ReactNode; config?: MaxModeWidgetConfig }) {
  // A retired mount must never persist an asynchronous update into the next account's partition.
  const [storageKey] = useState(() => getWidgetStorageKey(STORAGE_KEY, config));
  const [pendingKey] = useState(() => getWidgetStorageKey(PENDING_ATTACHMENTS_KEY, config));
  const [hostControlled] = useState(() => Boolean((config || getWidgetConfig()).hostSession));
  const [maxModeState, setMaxModeStateInternal] =
    useState<MaxModeState>(defaultState);
  const [pendingAttachments, setPendingAttachments] = useState<
    SharedAttachment[]
  >([]);

  useEffect(() => {
    if (hostControlled) return;
    const persisted = loadFromStorage();
    if (persisted) setMaxModeStateInternal(persisted);

    const pending = loadPendingFromStorage();
    if (pending?.length) setPendingAttachments(pending);
  }, []);

  const loadFromStorage = (): MaxModeState | null => {
    try {
      const stored = sessionStorage.getItem(storageKey);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  };

  const loadPendingFromStorage = (): SharedAttachment[] => {
    try {
      const stored = sessionStorage.getItem(pendingKey);
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  };

  const persistState = useCallback(() => {
    try {
      if (!hostControlled) sessionStorage.setItem(storageKey, JSON.stringify(maxModeState));
    } catch {}
  }, [hostControlled, maxModeState, storageKey]);

  const loadPersistedState = useCallback((): MaxModeState | null => {
    return loadFromStorage();
  }, [storageKey]);

  const clearPersistedState = useCallback(() => {
    try {
      sessionStorage.removeItem(storageKey);
      sessionStorage.removeItem(pendingKey);
      setMaxModeStateInternal(defaultState);
      setPendingAttachments([]);
    } catch {}
  }, [storageKey, pendingKey]);

  const setMaxModeState = useCallback((state: MaxModeState) => {
    setMaxModeStateInternal(state);
    try {
      if (!hostControlled) sessionStorage.setItem(storageKey, JSON.stringify(state));
    } catch {}
  }, [hostControlled, storageKey]);

  const updateMaxModeState = useCallback((updates: Partial<MaxModeState>) => {
    setMaxModeStateInternal((prev) => {
      const newState = { ...prev, ...updates };
      try {
        if (!hostControlled) sessionStorage.setItem(storageKey, JSON.stringify(newState));
      } catch {}
      return newState;
    });
  }, [hostControlled, storageKey]);

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
          pendingKey,
          JSON.stringify(newAttachments),
        );
      } catch {}
      return newAttachments;
    });
  }, [pendingKey]);

  const clearPendingAttachments = useCallback(() => {
    setPendingAttachments([]);
    try {
      sessionStorage.removeItem(pendingKey);
    } catch {}
  }, [pendingKey]);

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
