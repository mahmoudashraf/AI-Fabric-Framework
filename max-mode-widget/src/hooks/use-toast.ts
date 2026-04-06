/**
 * Lightweight toast notification system for the widget.
 * Self-contained — no external toast library dependency.
 */
import * as React from "react";

export interface ToastData {
  id: string;
  title?: string;
  description?: string;
  variant?: "default" | "destructive";
  duration?: number;
}

type Action =
  | { type: "ADD"; toast: ToastData }
  | { type: "DISMISS"; toastId?: string }
  | { type: "REMOVE"; toastId?: string };

interface State {
  toasts: ToastData[];
}

const TOAST_LIMIT = 3;
const TOAST_DURATION = 4000;

let count = 0;
function genId() {
  count = (count + 1) % Number.MAX_SAFE_INTEGER;
  return count.toString();
}

const toastTimeouts = new Map<string, ReturnType<typeof setTimeout>>();

function addToRemoveQueue(toastId: string) {
  if (toastTimeouts.has(toastId)) return;
  const timeout = setTimeout(() => {
    toastTimeouts.delete(toastId);
    dispatch({ type: "REMOVE", toastId });
  }, TOAST_DURATION);
  toastTimeouts.set(toastId, timeout);
}

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case "ADD":
      return {
        ...state,
        toasts: [action.toast, ...state.toasts].slice(0, TOAST_LIMIT),
      };
    case "DISMISS": {
      const { toastId } = action;
      if (toastId) {
        addToRemoveQueue(toastId);
      } else {
        state.toasts.forEach((t) => addToRemoveQueue(t.id));
      }
      return state;
    }
    case "REMOVE":
      if (!action.toastId) return { ...state, toasts: [] };
      return {
        ...state,
        toasts: state.toasts.filter((t) => t.id !== action.toastId),
      };
  }
}

const listeners: Array<(state: State) => void> = [];
let memoryState: State = { toasts: [] };

function dispatch(action: Action) {
  memoryState = reducer(memoryState, action);
  listeners.forEach((listener) => listener(memoryState));
}

type ToastInput = Omit<ToastData, "id">;

function toast(props: ToastInput) {
  const id = genId();
  dispatch({ type: "ADD", toast: { ...props, id } });

  // Auto-dismiss
  setTimeout(() => {
    dispatch({ type: "DISMISS", toastId: id });
  }, props.duration ?? TOAST_DURATION);

  return {
    id,
    dismiss: () => dispatch({ type: "DISMISS", toastId: id }),
  };
}

function useToast() {
  const [state, setState] = React.useState<State>(memoryState);

  React.useEffect(() => {
    listeners.push(setState);
    return () => {
      const index = listeners.indexOf(setState);
      if (index > -1) listeners.splice(index, 1);
    };
  }, [state]);

  return {
    ...state,
    toast,
    dismiss: (toastId?: string) =>
      dispatch({ type: "DISMISS", toastId }),
  };
}

export { useToast, toast };
