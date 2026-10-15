import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from "react";
import { Headphones, Loader2, UserRound, X } from "lucide-react";

import type { MaxModeHandoverIntakeFieldConfig } from "@/config";
import type { ConversationControlState } from "@/types";
import { Button } from "@/ui/button";

export function ConversationHandoverDialog({
  open,
  onOpenChange,
  state,
  handoverStatus,
  displayName,
  fields,
  anonymousAllowed,
  canCancel,
  canResume,
  busy,
  onSubmit,
  onCancel,
  onRetry,
  onResume,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  state: ConversationControlState;
  handoverStatus?: string | null;
  displayName?: string | null;
  fields: MaxModeHandoverIntakeFieldConfig[];
  anonymousAllowed: boolean;
  canCancel: boolean;
  canResume: boolean;
  busy: boolean;
  onSubmit: (values: Record<string, string>, consentedFields: string[], anonymous: boolean) => void;
  onCancel: () => void;
  onRetry: () => void;
  onResume: () => void;
}) {
  const [values, setValues] = useState<Record<string, string>>({});
  const [consents, setConsents] = useState<Record<string, boolean>>({});
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useLayoutEffect(() => {
    const dialog = dialogRef.current;
    if (!open || !dialog) return;
    let previous = dialog.ownerDocument.activeElement;
    while (previous?.shadowRoot?.activeElement) previous = previous.shadowRoot.activeElement;
    dialog.showModal();
    const initial = dialog.querySelector<HTMLElement>("input:not([disabled]), select:not([disabled]), textarea:not([disabled])")
      ?? dialog.querySelector<HTMLElement>("button:not([disabled])");
    (initial ?? dialog).focus({ preventScroll: true });
    return () => {
      dialog.close();
      if (previous instanceof HTMLElement && previous.isConnected) previous.focus({ preventScroll: true });
    };
  }, [open]);

  useEffect(() => {
    if (!open) {
      setValues({});
      setConsents({});
    }
  }, [open]);

  const canSubmit = useMemo(() => fields.every((field) => {
    const value = values[field.key]?.trim();
    if (field.required && !value) return false;
    if (value && field.consentRequired && !consents[field.key]) return false;
    return true;
  }), [consents, fields, values]);

  if (!open) return null;

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      aria-modal="true"
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onOpenChange(false);
      }}
      onKeyDown={(event) => {
        if (event.key !== "Tab") return;
        const dialog = event.currentTarget;
        const controls = Array.from(dialog.querySelectorAll<HTMLElement>("button, input, select, textarea, a[href], [tabindex]"))
          .filter((element) => element.tabIndex >= 0 && !element.matches(":disabled, [hidden]") && element.getClientRects().length > 0);
        const active = (dialog.getRootNode() as Document | ShadowRoot).activeElement;
        const first = controls[0];
        const last = controls[controls.length - 1];
        if (!first) {
          event.preventDefault();
          dialog.focus();
        } else if (event.shiftKey && (active === first || active === dialog || !dialog.contains(active))) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && (active === last || active === dialog || !dialog.contains(active))) {
          event.preventDefault();
          first.focus();
        }
      }}
      className="max-mode-viewport max-mode-handover-modal m-0 max-h-none max-w-none items-end justify-center border-0 bg-transparent p-3 text-left text-gray-950 outline-none pointer-events-auto sm:items-center dark:text-white"
    >
      <div
        className="flex max-h-full w-full max-w-lg flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-2xl dark:border-gray-700 dark:bg-gray-950"
      >
        <div className="flex flex-shrink-0 items-center justify-between border-b border-gray-200 px-5 py-4 dark:border-gray-800">
          <div className="flex min-w-0 items-center gap-3">
            <span className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">
              <Headphones className="h-5 w-5" />
            </span>
            <div>
              <h2 id={titleId} className="text-base font-bold text-gray-950 dark:text-white">
                {dialogTitle(state)}
              </h2>
              <p id={descriptionId} className="text-xs text-gray-500 dark:text-gray-400">Your current conversation and context stay together.</p>
            </div>
          </div>
          <Button variant="ghost" size="icon" disabled={busy} onClick={() => onOpenChange(false)} aria-label="Close human support panel">
            <X className="h-5 w-5" />
          </Button>
        </div>

        <div className="min-h-0 space-y-4 overflow-y-auto p-5">
          {state === "AI_ACTIVE" && (
            <>
              <p className="text-sm leading-relaxed text-gray-600 dark:text-gray-300">
                Share only the contact details you want the support team to use. These fields are sent directly through the configured human-support channel.
              </p>
              {fields.map((field) => (
                <IntakeField
                  key={field.key}
                  field={field}
                  value={values[field.key] || ""}
                  consented={consents[field.key] === true}
                  onValue={(value) => setValues((current) => ({ ...current, [field.key]: value }))}
                  onConsent={(value) => setConsents((current) => ({ ...current, [field.key]: value }))}
                />
              ))}
              <div className="flex flex-col-reverse gap-2 pt-1 sm:flex-row sm:justify-end">
                {anonymousAllowed && (
                  <Button
                    type="button"
                    variant="outline"
                    disabled={busy}
                    onClick={() => onSubmit({}, [], true)}
                  >
                    Continue anonymously
                  </Button>
                )}
                <Button
                  type="button"
                  disabled={busy || !canSubmit}
                  onClick={() => onSubmit(
                    Object.fromEntries(Object.entries(values).filter(([, value]) => value.trim())),
                    Object.entries(consents).filter(([, accepted]) => accepted).map(([key]) => key),
                    false,
                  )}
                  className="bg-emerald-600 text-white hover:bg-emerald-700"
                >
                  {busy && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Talk to a person
                </Button>
              </div>
            </>
          )}

          {state === "HUMAN_PENDING" && handoverStatus === "EXPIRED" && (
            <StatusContent
              icon={<Headphones className="h-6 w-6" />}
              title="No person joined yet"
              description="The queue wait expired. AI is still paused; retry the human channel or explicitly continue with AI."
            >
              <div className="flex flex-wrap justify-end gap-2">
                <Button variant="outline" disabled={busy} onClick={onCancel}>Continue with AI</Button>
                <Button disabled={busy} onClick={onRetry}>Retry</Button>
              </div>
            </StatusContent>
          )}

          {state === "HUMAN_PENDING" && handoverStatus !== "EXPIRED" && (
            <StatusContent
              icon={<Loader2 className="h-6 w-6 animate-spin" />}
              title="Connecting to a person"
              description="Messages you send now are queued for the human support channel. The AI will not reply or run actions while the transfer is pending."
            >
              {canCancel && <Button variant="outline" disabled={busy} onClick={onCancel}>Cancel request</Button>}
            </StatusContent>
          )}

          {state === "HUMAN_ACTIVE" && (
            <StatusContent
              icon={<UserRound className="h-6 w-6" />}
              title={displayName || "Human support active"}
              description="Your messages are going to a person. AI orchestration remains paused until you or the operator explicitly returns this conversation to AI."
            >
              {canResume && <Button disabled={busy} onClick={onResume}>Continue with AI</Button>}
            </StatusContent>
          )}

          {state === "AI_RESUME_PENDING" && (
            <StatusContent
              icon={<Loader2 className="h-6 w-6 animate-spin" />}
              title="Returning to AI"
              description="The human channel is being finalized before AI is allowed to answer again."
            />
          )}

          {state === "CLOSED" && (
            <StatusContent
              icon={<Headphones className="h-6 w-6" />}
              title="Conversation closed"
              description="Start a new conversation to contact AI or human support again."
            />
          )}
        </div>
      </div>
    </dialog>
  );
}

function IntakeField({
  field,
  value,
  consented,
  onValue,
  onConsent,
}: {
  field: MaxModeHandoverIntakeFieldConfig;
  value: string;
  consented: boolean;
  onValue: (value: string) => void;
  onConsent: (value: boolean) => void;
}) {
  const inputClass = "mt-1.5 w-full rounded-lg border border-gray-300 bg-white px-3 py-2.5 text-sm text-gray-950 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-white";
  return (
    <div>
      <label className="text-sm font-semibold text-gray-800 dark:text-gray-200" htmlFor={`handover-${field.key}`}>
        {field.label}{field.required ? " *" : ""}
      </label>
      {field.type === "SELECT" ? (
        <select id={`handover-${field.key}`} value={value} onChange={(event) => onValue(event.target.value)} className={inputClass}>
          <option value="">Select an option</option>
          {(field.options || []).map((option) => <option key={option} value={option}>{option}</option>)}
        </select>
      ) : field.type === "BOOLEAN" ? (
        <label className="mt-2 flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
          <input
            id={`handover-${field.key}`}
            type="checkbox"
            checked={value === "true"}
            onChange={(event) => onValue(event.target.checked ? "true" : "")}
          />
          {field.purpose || field.label}
        </label>
      ) : (
        <input
          id={`handover-${field.key}`}
          type={field.type === "EMAIL" ? "email" : field.type === "PHONE" ? "tel" : "text"}
          value={value}
          maxLength={Math.max(1, Math.min(field.maxLength || 500, 4096))}
          onChange={(event) => onValue(event.target.value)}
          className={inputClass}
        />
      )}
      {field.purpose && field.type !== "BOOLEAN" && (
        <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{field.purpose}</p>
      )}
      {field.consentRequired && value.trim() && (
        <label className="mt-2 flex items-start gap-2 text-xs leading-relaxed text-gray-600 dark:text-gray-300">
          <input type="checkbox" checked={consented} onChange={(event) => onConsent(event.target.checked)} className="mt-0.5" />
          I agree that this information may be shared with the human-support provider for the stated purpose.
        </label>
      )}
    </div>
  );
}

function StatusContent({ icon, title, description, children }: {
  icon: React.ReactNode;
  title: string;
  description: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 dark:border-emerald-900 dark:bg-emerald-950/30">
      <div className="flex items-start gap-3 text-emerald-700 dark:text-emerald-300">
        <span className="mt-0.5 flex-shrink-0">{icon}</span>
        <div>
          <h3 className="font-bold">{title}</h3>
          <p className="mt-1 text-sm leading-relaxed text-emerald-900/75 dark:text-emerald-100/75">{description}</p>
        </div>
      </div>
      {children && <div className="mt-4 flex justify-end">{children}</div>}
    </div>
  );
}

function dialogTitle(state: ConversationControlState) {
  if (state === "HUMAN_PENDING") return "Connecting to a person";
  if (state === "HUMAN_ACTIVE") return "Human support active";
  if (state === "AI_RESUME_PENDING") return "Returning to AI";
  if (state === "CLOSED") return "Conversation closed";
  return "Talk to a person";
}
