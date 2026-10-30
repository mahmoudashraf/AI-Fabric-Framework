import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCheck, RefreshCw } from "lucide-react";
import { api, commandKey, errorMessage, post } from "./api";
import { dateTime, ErrorNotice, IconButton, Modal, Status } from "./components";

export interface ExternalConversationContext {
  state: "NOT_CONFIGURED" | "NOT_LINKED" | "AVAILABLE";
  context: {
    reference: string;
    title: string;
    status: string;
    version: number;
    accountLabel?: string | null;
    resourceLabels: string[];
    consent?: { status: string; summary?: string; updatedAt: string } | null;
    observations: {
      label: string;
      status: string;
      summary: string;
      observedAt: string;
    }[];
    resolveAction?: { label: string; confirmation: string } | null;
  } | null;
}

export function ConversationExternalContext({
  root,
  bindingId,
  handoverId,
  accessFresh,
}: {
  root: string;
  bindingId: string;
  handoverId: string;
  accessFresh: boolean;
}) {
  const client = useQueryClient();
  const key = ["binding", bindingId, "conversation-context", handoverId];
  const query = useQuery({
    queryKey: key,
    queryFn: ({ signal }) =>
      api<ExternalConversationContext>(`${root}/context`, { signal }),
    enabled: accessFresh,
    retry: false,
    refetchInterval: (query) =>
      query.state.data?.state === "NOT_CONFIGURED" ? false : 30000,
  });
  const [confirmation, setConfirmation] = useState<{
    version: number;
    label: string;
    text: string;
  } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const commands = useRef(new Map<string, string>());
  const context = query.data?.context;
  const action = context?.resolveAction;
  const mayResolve =
    accessFresh && !query.isError && !!action && !!context && !busy;
  const changedSinceConfirmation =
    confirmation !== null && context?.version !== confirmation.version;

  async function resolve() {
    if (!mayResolve || !context || !confirmation || changedSinceConfirmation)
      return;
    setBusy(true);
    setError("");
    const command = commandKey(
      commands.current,
      "resolve",
      String(confirmation.version),
    );
    try {
      const result = await post<ExternalConversationContext>(
        `${root}/context/resolve`,
        {
          idempotencyKey: command.id,
          expectedVersion: confirmation.version,
        },
      );
      if (result.state !== "AVAILABLE" || !result.context)
        throw new Error(
          "The provider has not confirmed the updated case. Refresh context before continuing.",
        );
      client.setQueryData(key, result);
      command.done();
      setConfirmation(null);
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  if (!accessFresh) return null;
  if (query.isPending)
    return (
      <section>
        <p className="caption" role="status">
          Loading external context...
        </p>
      </section>
    );
  if (query.isError)
    return (
      <section aria-label="External context">
        <ErrorNotice
          compact
          message={`External context unavailable. ${errorMessage(query.error)}`}
          retry={() => void query.refetch()}
        />
      </section>
    );
  if (query.data?.state === "NOT_CONFIGURED") return null;

  return (
    <section className="external-context" aria-label="External context">
      <div className="external-context-heading">
        <span className="eyebrow">EXTERNAL CONTEXT</span>
        <IconButton
          icon={RefreshCw}
          label="Refresh external context"
          disabled={query.isFetching || busy}
          onClick={() => void query.refetch()}
        />
      </div>
      {!context ? (
        <p className="caption">
          No external case is linked to this conversation.
        </p>
      ) : (
        <>
          <span className="external-context-reference">
            {context.reference}
          </span>
          <h3>{context.title}</h3>
          <Status value={context.status} />
          <dl className="inspector-fields">
            {context.accountLabel && (
              <div>
                <dt>Account</dt>
                <dd>{context.accountLabel}</dd>
              </div>
            )}
            {context.resourceLabels.length > 0 && (
              <div>
                <dt>Selected resources</dt>
                <dd>
                  <ul>
                    {context.resourceLabels.map((label, index) => (
                      <li key={index}>{label}</li>
                    ))}
                  </ul>
                </dd>
              </div>
            )}
            {context.consent && (
              <div>
                <dt>Diagnostic consent</dt>
                <dd>
                  <Status value={context.consent.status} />
                  {context.consent.summary && <p>{context.consent.summary}</p>}
                  <time className="caption">
                    {dateTime(context.consent.updatedAt)}
                  </time>
                </dd>
              </div>
            )}
          </dl>
          {context.observations.length > 0 && (
            <div className="external-context-observations">
              <h3>Checks so far</h3>
              {context.observations.map((observation, index) => (
                <div key={index}>
                  <strong>{observation.label}</strong>
                  <Status value={observation.status} />
                  <p>{observation.summary}</p>
                  <time>{dateTime(observation.observedAt)}</time>
                </div>
              ))}
            </div>
          )}
          {action && (
            <div className="external-context-resolution">
              <button
                className="button secondary small"
                disabled={!mayResolve}
                onClick={() => {
                  setError("");
                  setConfirmation({
                    version: context.version,
                    label: action.label,
                    text: action.confirmation,
                  });
                }}
              >
                <CheckCheck size={14} />
                {action.label}
              </button>
              <p className="caption">
                Case resolution does not close the conversation or return
                control to AI.
              </p>
            </div>
          )}
        </>
      )}
      {confirmation && (
        <Modal
          title={confirmation.label}
          onClose={() => {
            if (!busy) setConfirmation(null);
          }}
          footer={
            <>
              <button
                className="button secondary"
                disabled={busy}
                onClick={() => setConfirmation(null)}
              >
                Cancel
              </button>
              <button
                className="button primary"
                disabled={!mayResolve || changedSinceConfirmation}
                onClick={() => void resolve()}
              >
                {busy ? "Submitting..." : confirmation.label}
              </button>
            </>
          }
        >
          <p>{confirmation.text}</p>
          <p className="caption">Conversation control stays unchanged.</p>
          {changedSinceConfirmation && (
            <p role="status">
              Context changed. Cancel and review the latest case before
              resolving.
            </p>
          )}
          {error && <ErrorNotice compact message={error} />}
        </Modal>
      )}
    </section>
  );
}
