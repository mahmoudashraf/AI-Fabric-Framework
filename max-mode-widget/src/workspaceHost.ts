import type { ConversationControlState } from "./types";

/** Opaque host-provided partitions, not authentication or authorization claims. */
export interface WorkspaceHostSession {
  namespace: string;
  accountKey: string;
}

export interface WorkspaceState {
  status: "initializing" | "ready" | "loading" | "error" | "destroyed";
  ready: boolean;
  isOpen: boolean;
  busy: boolean;
  conversationId: string | null;
  controlState: ConversationControlState | null;
  controlVersion: number | null;
  error: { code: string; message: string } | null;
}

export interface WorkspaceOpenConversationOptions {
  /** IIFE callers open the workspace by default. false keeps its current visibility. */
  open?: boolean;
}

export interface WorkspaceHostApi {
  getState(): WorkspaceState;
  /** Emits the current state immediately. The returned function unsubscribes. */
  onStateChange(listener: (state: WorkspaceState) => void): () => void;
  whenReady(): Promise<WorkspaceState>;
  openConversation(conversationId: string, options?: WorkspaceOpenConversationOptions): Promise<WorkspaceState>;
  refreshConversation(): Promise<WorkspaceState>;
}

export class WorkspaceHostError extends Error {
  constructor(readonly code: string, message: string) {
    super(message);
    this.name = "WorkspaceHostError";
  }
}

type Handlers = {
  openConversation(id: string): Promise<Partial<WorkspaceState>>;
  refreshConversation(): Promise<Partial<WorkspaceState>>;
};
type HostRecord = {
  state: WorkspaceState;
  listeners: Set<(state: WorkspaceState) => void>;
  handlers?: Handlers;
  generation: number;
  commandRunning?: boolean;
};
const records = new WeakMap<WorkspaceHostApi, HostRecord>();

export function createWorkspaceHost(): WorkspaceHostApi {
  const record: HostRecord = {
    state: {
      status: "initializing", ready: false, isOpen: false, busy: false,
      conversationId: null, controlState: null, controlVersion: null, error: null,
    },
    listeners: new Set(),
    generation: 0,
  };
  const api: WorkspaceHostApi = {
    getState: () => snapshot(record),
    onStateChange(listener) {
      record.listeners.add(listener);
      notify(listener, record);
      return () => { record.listeners.delete(listener); };
    },
    whenReady: () => waitFor(record, () => record.state.ready),
    async openConversation(id) {
      if (typeof id !== "string" || !id.trim() || id.length > 512) {
        throw new WorkspaceHostError("INVALID_CONVERSATION_ID", "A conversation ID is required.");
      }
      return invoke(record, () => record.handlers!.openConversation(id.trim()));
    },
    async refreshConversation() {
      return invoke(record, () => record.handlers!.refreshConversation());
    },
  };
  records.set(api, record);
  return api;
}

/** Internal lifecycle binding. Hosts receive only WorkspaceHostApi. */
export function bindWorkspaceHost(api: WorkspaceHostApi, handlers: Handlers): () => void {
  const record = requireRecord(api);
  // React development remounts can rebind the same explicit host handle.
  if (record.state.status === "destroyed") {
    record.state = { ...record.state, status: "initializing" };
  }
  record.handlers = handlers;
  updateWorkspaceHost(api, { status: "ready", ready: true, error: null });
  return () => {
    if (record.handlers !== handlers) return;
    destroyWorkspaceHost(api);
  };
}

export function updateWorkspaceHost(api: WorkspaceHostApi, update: Partial<WorkspaceState>): void {
  const record = requireRecord(api);
  if (record.state.status === "destroyed") return;
  const next = mergeState(record.state, update);
  if (JSON.stringify(next) === JSON.stringify(record.state)) return;
  record.state = next;
  for (const listener of record.listeners) notify(listener, record);
}

export function destroyWorkspaceHost(api: WorkspaceHostApi): void {
  const record = requireRecord(api);
  record.generation++;
  record.handlers = undefined;
  record.commandRunning = false;
  record.state = {
    status: "destroyed", ready: false, busy: false, isOpen: false,
    conversationId: null, controlState: null, controlVersion: null, error: null,
  };
  for (const listener of record.listeners) notify(listener, record);
  record.listeners.clear();
}

async function invoke(record: HostRecord, operation: () => Promise<Partial<WorkspaceState>>) {
  const generation = record.generation;
  await waitFor(record, () => record.state.ready);
  if (generation !== record.generation) throw destroyedError();
  if (record.commandRunning || record.state.busy) throw new WorkspaceHostError("WORKSPACE_BUSY", "Wait for the current workspace operation to finish.");
  if (!record.handlers) throw destroyedError();
  record.commandRunning = true;
  record.state = { ...record.state, status: "loading", busy: true, error: null };
  for (const listener of record.listeners) notify(listener, record);
  try {
    const update = await operation();
    if (generation !== record.generation) throw destroyedError();
    record.state = { ...mergeState(record.state, update), status: "ready", busy: false, error: null };
    for (const listener of record.listeners) notify(listener, record);
    return snapshot(record);
  } catch (error) {
    if (generation !== record.generation) throw destroyedError();
    record.state = {
      ...record.state, status: "error", busy: false,
      error: { code: error instanceof WorkspaceHostError ? error.code : "CONVERSATION_LOAD_FAILED", message: "Unable to load the authorized conversation." },
    };
    for (const listener of record.listeners) notify(listener, record);
    throw error;
  } finally {
    if (generation === record.generation) record.commandRunning = false;
  }
}

function waitFor(record: HostRecord, predicate: () => boolean): Promise<WorkspaceState> {
  if (record.state.status === "destroyed") return Promise.reject(destroyedError());
  if (predicate()) return Promise.resolve(snapshot(record));
  return new Promise((resolve, reject) => {
    const finish = () => {
      record.listeners.delete(check);
      clearTimeout(timeout);
    };
    const check = () => {
      if (record.state.status === "destroyed") {
        finish();
        reject(destroyedError());
      } else if (predicate()) {
        finish();
        resolve(snapshot(record));
      }
    };
    const timeout = setTimeout(() => {
      finish();
      reject(new WorkspaceHostError("WORKSPACE_NOT_READY", "The workspace did not become ready in time."));
    }, 10_000);
    record.listeners.add(check);
  });
}

function requireRecord(api: WorkspaceHostApi): HostRecord {
  const record = records.get(api);
  if (!record) throw new WorkspaceHostError("INVALID_WORKSPACE_HOST", "Use createWorkspaceHost() for this widget build.");
  return record;
}
function snapshot(record: HostRecord): WorkspaceState {
  return { ...record.state, error: record.state.error ? { ...record.state.error } : null };
}
function mergeState(current: WorkspaceState, update: Partial<WorkspaceState>): WorkspaceState {
  const next = { ...current, ...update };
  if (next.conversationId && next.conversationId === current.conversationId
    && current.controlVersion !== null && (next.controlVersion === null || next.controlVersion < current.controlVersion)) {
    next.controlVersion = current.controlVersion;
    next.controlState = current.controlState;
  }
  return next;
}
function notify(listener: (state: WorkspaceState) => void, record: HostRecord) {
  try { listener(snapshot(record)); } catch (error) { console.error("[MaxMode] Workspace state listener failed:", error); }
}
function destroyedError() {
  return new WorkspaceHostError("WORKSPACE_DESTROYED", "This workspace has been destroyed. Use the current installation controller.");
}
