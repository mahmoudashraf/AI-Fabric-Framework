import { Component, createElement, useEffect, useMemo, useRef } from "react";
import type { ErrorInfo, ReactNode } from "react";

import {
  type MaxModeActionPresentationCommands,
  type MaxModeActionPresentationComponentProps,
  type MaxModePresentationResultReference,
  type ResolvedActionPresentation,
} from "@/actionPresentation";
import { emitEvent } from "@/config";

interface ActionPresentationSurfaceProps {
  presentation: ResolvedActionPresentation;
  selectedResultKeys: readonly string[];
  fallback: ReactNode;
  onAsk: (query: string, references: readonly MaxModePresentationResultReference[]) => Promise<void> | void;
  onAttachResult: (reference: MaxModePresentationResultReference) => void;
  onDetachResult: (reference: MaxModePresentationResultReference) => void;
}

interface PresentationCustomElement extends HTMLElement {
  presentation?: Omit<MaxModeActionPresentationComponentProps, "commands">;
  commands?: MaxModeActionPresentationCommands;
}

export function ActionPresentationSurface({
  presentation,
  selectedResultKeys,
  fallback,
  onAsk,
  onAttachResult,
  onDetachResult,
}: ActionPresentationSurfaceProps) {
  const commandStateRef = useRef({ presentation, onAsk, onAttachResult, onDetachResult });
  commandStateRef.current = { presentation, onAsk, onAttachResult, onDetachResult };
  const commandsRef = useRef<MaxModeActionPresentationCommands>();
  if (!commandsRef.current) {
    commandsRef.current = buildCommands(() => commandStateRef.current);
  }
  const commands = commandsRef.current;
  const componentProps = useMemo<MaxModeActionPresentationComponentProps>(
    () => ({
      actionName: presentation.actionName,
      rendererId: presentation.rendererId,
      schemaVersion: presentation.schemaVersion,
      presentationData: presentation.presentationData,
      resultReferences: presentation.resultReferences,
      selectedResultKeys,
      context: presentation.context,
      commands,
    }),
    [commands, presentation, selectedResultKeys],
  );

  const presentationRevision = presentationFingerprint(componentProps);

  useEffect(() => {
    emitEvent("action-presentation:rendered", {
      actionName: presentation.actionName,
      rendererId: presentation.rendererId,
      schemaVersion: presentation.schemaVersion,
      resultReferencesCount: presentation.resultReferences.length,
    });
  }, [presentationRevision]);

  return (
    <ActionPresentationErrorBoundary
      fallback={fallback}
      actionName={presentation.actionName}
      rendererId={presentation.rendererId}
    >
      {presentation.renderer.kind === "react"
        ? createElement(presentation.renderer.component, componentProps)
        : (
          <CustomElementPresentation
            elementName={presentation.renderer.elementName}
            props={componentProps}
          />
        )}
    </ActionPresentationErrorBoundary>
  );
}

function CustomElementPresentation({
  elementName,
  props,
}: {
  elementName: string;
  props: MaxModeActionPresentationComponentProps;
}) {
  const elementRef = useRef<PresentationCustomElement | null>(null);
  const appliedRevisionRef = useRef<string>();
  const revision = presentationFingerprint(props);

  useEffect(() => {
    const element = elementRef.current;
    if (element) {
      element.commands = props.commands;
    }
  }, [props.commands]);

  useEffect(() => {
    const element = elementRef.current;
    if (!element || appliedRevisionRef.current === revision) {
      return;
    }
    appliedRevisionRef.current = revision;
    element.presentation = {
      actionName: props.actionName,
      rendererId: props.rendererId,
      schemaVersion: props.schemaVersion,
      presentationData: props.presentationData,
      resultReferences: props.resultReferences,
      selectedResultKeys: props.selectedResultKeys,
      context: props.context,
    };
  }, [revision]);

  return createElement(elementName, {
    ref: (node: PresentationCustomElement | null) => {
      elementRef.current = node;
    },
    "data-max-mode-action-renderer": props.rendererId,
    "data-max-mode-action-schema": props.schemaVersion,
  });
}

function buildCommands(
  state: () => Omit<ActionPresentationSurfaceProps, "selectedResultKeys" | "fallback">,
): MaxModeActionPresentationCommands {
  return Object.freeze({
    async ask({ query, resultReferenceKeys }) {
      const normalizedQuery = typeof query === "string" ? query.trim() : "";
      if (!normalizedQuery) {
        return;
      }
      const { presentation, onAsk } = state();
      const references = referenceMap(presentation);
      const selected = (resultReferenceKeys || [])
        .map((key) => references.get(key))
        .filter((reference): reference is MaxModePresentationResultReference => Boolean(reference));
      emitEvent("action-presentation:command", {
        command: "ask",
        actionName: presentation.actionName,
        rendererId: presentation.rendererId,
        selectedCount: selected.length,
      });
      await onAsk(normalizedQuery, selected);
    },
    attachResult(referenceKey) {
      const { presentation, onAttachResult } = state();
      const references = referenceMap(presentation);
      const reference = references.get(referenceKey);
      if (!reference) {
        return;
      }
      emitEvent("action-presentation:command", {
        command: "attach-result",
        actionName: presentation.actionName,
        rendererId: presentation.rendererId,
      });
      onAttachResult(reference);
    },
    detachResult(referenceKey) {
      const { presentation, onDetachResult } = state();
      const references = referenceMap(presentation);
      const reference = references.get(referenceKey);
      if (!reference) {
        return;
      }
      emitEvent("action-presentation:command", {
        command: "detach-result",
        actionName: presentation.actionName,
        rendererId: presentation.rendererId,
      });
      onDetachResult(reference);
    },
    navigate({ url, target }) {
      const { presentation } = state();
      const safeUrl = resolveSafeNavigationUrl(url);
      if (!safeUrl) {
        emitEvent("error", {
          code: "action-presentation-navigation-rejected",
          actionName: presentation.actionName,
          rendererId: presentation.rendererId,
        });
        return;
      }
      emitEvent("action-presentation:command", {
        command: "navigate",
        actionName: presentation.actionName,
        rendererId: presentation.rendererId,
      });
      if (target === "new-window") {
        window.open(safeUrl, "_blank", "noopener,noreferrer");
        return;
      }
      window.location.assign(safeUrl);
    },
  });
}

function referenceMap(presentation: ResolvedActionPresentation) {
  return new Map(presentation.resultReferences.map((reference) => [reference.key, reference]));
}

function presentationFingerprint(props: MaxModeActionPresentationComponentProps): string {
  return stableSerialize({
    actionName: props.actionName,
    rendererId: props.rendererId,
    schemaVersion: props.schemaVersion,
    presentationData: props.presentationData,
    resultReferences: props.resultReferences,
    selectedResultKeys: props.selectedResultKeys,
    context: props.context,
  });
}

function stableSerialize(value: unknown): string {
  if (value === null || typeof value !== "object") {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map(stableSerialize).join(",")}]`;
  }
  return `{${Object.entries(value as Record<string, unknown>)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, entry]) => `${JSON.stringify(key)}:${stableSerialize(entry)}`)
    .join(",")}}`;
}

function resolveSafeNavigationUrl(value: string): string | null {
  if (typeof window === "undefined" || typeof value !== "string") {
    return null;
  }
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  try {
    const relative = trimmed.startsWith("/") || trimmed.startsWith("./") || trimmed.startsWith("../");
    const url = new URL(trimmed, window.location.href);
    if (url.username || url.password) {
      return null;
    }
    if (relative && url.origin === window.location.origin) {
      return url.toString();
    }
    return url.protocol === "https:" ? url.toString() : null;
  } catch {
    return null;
  }
}

class ActionPresentationErrorBoundary extends Component<{
  children: ReactNode;
  fallback: ReactNode;
  actionName: string;
  rendererId: string;
}, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(_error: Error, _info: ErrorInfo) {
    emitEvent("action-presentation:fallback", {
      reason: "renderer-error",
      actionName: this.props.actionName,
      rendererId: this.props.rendererId,
    });
  }

  render() {
    return this.state.failed ? this.props.fallback : this.props.children;
  }
}
