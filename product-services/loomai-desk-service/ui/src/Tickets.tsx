import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowRight,
  ArrowLeft,
  Bot,
  CheckCheck,
  ChevronDown,
  Clock3,
  RefreshCw,
  Search,
  ShieldCheck,
  Ticket as TicketIcon,
} from "lucide-react";
import { ApiError, api, bindingPath, commandKey } from "./api";
import {
  dateTime,
  Empty,
  ErrorNotice,
  humanize,
  IconButton,
  Loading,
  MobileBack,
  Modal,
  Notice,
  relativeTime,
  Status,
} from "./components";
import type {
  Binding,
  Conversation,
  Ticket,
  TicketPage,
  TicketStatus,
} from "./types";

// Dependency internals are not an operator-facing error contract.
function ticketError(error: unknown, scope: "list" | "ticket" = "ticket") {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Your session has expired. Sign in again.";
    if (error.status === 403)
      return "Support access is no longer available for this workspace.";
    if (error.status === 404)
      return scope === "list"
        ? "The ticket list is unavailable for this workspace. Refresh the workspace and try again."
        : "This ticket is unavailable in the selected workspace. Refresh the ticket list.";
    if (error.status === 409)
      return "The ticket changed or this command conflicts with an earlier request. Review the latest ticket before trying again.";
    if (error.status === 405 || error.status === 422)
      return "The ticket provider does not support this command in the current state.";
  }
  return "The ticket service could not confirm this request. Refresh to check its current state. No command was automatically retried.";
}

export function Tickets({
  binding,
  accessFresh,
  selected,
  onSelect,
  onOpenConversation,
}: {
  binding: Binding;
  accessFresh: boolean;
  selected: string;
  onSelect: (id: string) => void;
  onOpenConversation: (handoverId: string, reference: string) => void;
}) {
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<TicketStatus | "ALL">("ALL");
  const [cursors, setCursors] = useState([""]);
  const cursor = cursors[cursors.length - 1]!;
  const canSupport = binding.roles.includes("SUPPORT");
  const configured = binding.ticketingConfigured === true;
  const queue = useQuery({
    queryKey: ["binding", binding.bindingId, "tickets", filter, cursor],
    queryFn: ({ signal }) => {
      const params = new URLSearchParams({ limit: "50" });
      if (filter !== "ALL") params.set("status", filter);
      if (cursor) params.set("cursor", cursor);
      return api<TicketPage>(
        `${bindingPath(binding.bindingId)}/tickets?${params}`,
        { signal },
      );
    },
    enabled: configured && canSupport && accessFresh,
    refetchInterval: 5000,
  });
  const items = queue.data?.items ?? [];
  const visible = items.filter(
    (item) =>
      (filter === "ALL" || item.status === filter) &&
      `${item.reference} ${item.subject}`
        .toLowerCase()
        .includes(search.trim().toLowerCase()),
  );
  function changeFilter(value: TicketStatus | "ALL") {
    setCursors([""]);
    setFilter(value);
  }
  if (!configured)
    return (
      <Empty
        icon={TicketIcon}
        title="Ticketing not configured"
        text="Ticketing has not been configured for this workspace."
      />
    );
  if (!canSupport)
    return (
      <Empty
        icon={ShieldCheck}
        title="Support access required"
        text="Ask the workspace owner to assign the Support role for this deployment and environment. Ownership and review access do not grant ticket access."
      />
    );
  return (
    <div className={`tickets-layout ${selected ? "has-selection" : ""}`}>
      <section className="queue-panel" aria-label="Ticket queue">
        <div className="queue-heading">
          <div>
            <span className="eyebrow">SUPPORT REQUESTS</span>
            <h2>
              Tickets <span className="count-badge">{items.length}</span>
            </h2>
          </div>
          <span className="queue-waiting">
            {items.filter((item) => item.status === "OPEN").length} open
          </span>
        </div>
        <label className="search-field">
          <Search size={16} />
          <input
            aria-label="Search tickets"
            type="search"
            placeholder="Search tickets"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
        <div className="queue-filters">
          <div role="tablist" aria-label="Ticket filter">
            {(["ALL", "OPEN"] as const).map((value) => (
              <button
                key={value}
                role="tab"
                aria-selected={filter === value}
                onClick={() => changeFilter(value)}
              >
                {value === "ALL" ? "All" : "Open"}
              </button>
            ))}
          </div>
          <div className="filter-select">
            <select
              aria-label="Ticket status"
              value={
                filter === "RESOLVED" || filter === "CANCELLED" ? filter : ""
              }
              onChange={(event) =>
                changeFilter(
                  (event.target.value || "ALL") as TicketStatus | "ALL",
                )
              }
            >
              <option value="">Status</option>
              <option value="RESOLVED">Resolved</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
            <ChevronDown size={12} />
          </div>
        </div>
        <div className="queue-list">
          {queue.isError ? (
            <ErrorNotice
              message={ticketError(queue.error, "list")}
              retry={() => void queue.refetch()}
            />
          ) : queue.isPending ? (
            <Loading label="Loading tickets" />
          ) : !visible.length ? (
            <Empty
              icon={TicketIcon}
              title={
                search || filter !== "ALL"
                  ? "No matching tickets"
                  : "No tickets yet"
              }
              text={
                search || filter !== "ALL"
                  ? "Try another reference or status."
                  : "Support requests will appear here."
              }
            />
          ) : (
            visible.map((ticket) => (
              <button
                className={`ticket-item ${selected === ticket.id ? "selected" : ""}`}
                key={ticket.id}
                onClick={() => onSelect(ticket.id)}
                aria-pressed={selected === ticket.id}
              >
                <div className="ticket-item-meta">
                  <span className="mono">{ticket.reference}</span>
                  <time title={dateTime(ticket.updatedAt)}>
                    {relativeTime(ticket.updatedAt)}
                  </time>
                </div>
                <h3>{ticket.subject}</h3>
                <div className="ticket-item-bottom">
                  <Status value={ticket.status} />
                  {ticket.unread && (
                    <span
                      className="ticket-unread"
                      aria-label="Unread activity"
                    />
                  )}
                </div>
              </button>
            ))
          )}
        </div>
        <footer className="queue-footer">
          <Clock3 size={13} />
          <span>
            {items.length} tickets / page {cursors.length}
          </span>
          <div className="ticket-pagination">
            <IconButton
              icon={ArrowLeft}
              label="Previous ticket page"
              disabled={cursors.length < 2 || queue.isFetching}
              onClick={() => setCursors(cursors.slice(0, -1))}
            />
            <IconButton
              icon={ArrowRight}
              label="Next ticket page"
              disabled={
                !queue.data?.nextCursor || queue.isFetching || queue.isError
              }
              onClick={() => {
                if (queue.data?.nextCursor)
                  setCursors([...cursors, queue.data.nextCursor]);
              }}
            />
          </div>
        </footer>
      </section>
      {selected ? (
        <TicketDetail
          key={selected}
          binding={binding}
          ticketId={selected}
          accessFresh={accessFresh && !queue.isError}
          onBack={() => onSelect("")}
          onOpenConversation={onOpenConversation}
        />
      ) : (
        <section className="thread-empty">
          <Empty
            icon={TicketIcon}
            title="Choose a ticket"
            text="Intake, status and linked conversations will appear here."
          />
        </section>
      )}
    </div>
  );
}

function TicketDetail({
  binding,
  ticketId,
  accessFresh,
  onBack,
  onOpenConversation,
}: {
  binding: Binding;
  ticketId: string;
  accessFresh: boolean;
  onBack: () => void;
  onOpenConversation: (handoverId: string, reference: string) => void;
}) {
  const client = useQueryClient();
  const [confirmation, setConfirmation] = useState<{ version: string } | null>(
    null,
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [resolved, setResolved] = useState(false);
  const commands = useRef(new Map<string, string>());
  const path = `${bindingPath(binding.bindingId)}/tickets/${encodeURIComponent(ticketId)}`;
  const key = ["binding", binding.bindingId, "ticket", ticketId];
  const detail = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => api<Ticket>(path, { signal }),
    enabled: accessFresh,
    refetchInterval: busy ? false : 5000,
  });
  const ticket = detail.data;
  const handovers = useQuery({
    queryKey: ["binding", binding.bindingId, "conversations"],
    queryFn: ({ signal }) =>
      api<Conversation[]>(
        `${bindingPath(binding.bindingId)}/conversations?limit=100`,
        { signal },
      ),
    enabled: accessFresh,
    refetchInterval: 5000,
  });
  const supportsResolve =
    ticket?.capabilities.includes("AGENT_RESOLVE") ?? false;
  const mayResolve =
    accessFresh &&
    !detail.isError &&
    !detail.isFetching &&
    supportsResolve &&
    ticket?.status === "OPEN" &&
    !busy;
  const changed = !!confirmation && confirmation.version !== ticket?.version;

  async function resolve() {
    if (!ticket || !confirmation || !mayResolve || changed) return;
    setBusy(true);
    setError("");
    const request = commandKey(
      commands.current,
      "resolve",
      confirmation.version,
    );
    try {
      await client.cancelQueries({ queryKey: key });
      const result = await api<Ticket>(`${path}/resolve`, {
        method: "POST",
        headers: { "Idempotency-Key": request.id },
        body: JSON.stringify({ expectedVersion: confirmation.version }),
      });
      if (
        result.id !== ticket.id ||
        result.providerBindingId !== ticket.providerBindingId ||
        result.status !== "RESOLVED"
      )
        throw new Error("Ticket resolution was not confirmed.");
      request.done();
      client.setQueryData(key, result);
      setConfirmation(null);
      setResolved(true);
      void client.invalidateQueries({
        queryKey: ["binding", binding.bindingId, "tickets"],
      });
    } catch (cause) {
      setError(ticketError(cause));
      if (cause instanceof ApiError && cause.status === 409) {
        request.done();
        void detail.refetch();
      }
    } finally {
      setBusy(false);
    }
  }

  if (!accessFresh || detail.isError || (!detail.isPending && !ticket))
    return (
      <section className="ticket-detail" aria-label="Ticket detail">
        <div className="ticket-detail-heading">
          <MobileBack onClick={onBack} />
        </div>
        <ErrorNotice
          message={
            !accessFresh
              ? "Ticket updates are unavailable. Refresh workspace access and the ticket list before continuing."
              : ticketError(detail.error)
          }
          retry={() => void detail.refetch()}
        />
      </section>
    );
  if (detail.isPending || !ticket)
    return (
      <section className="ticket-detail">
        <Loading label="Opening ticket" />
      </section>
    );
  return (
    <section className="ticket-detail" aria-label="Ticket detail">
      <header className="ticket-detail-heading">
        <MobileBack onClick={onBack} />
        <span className="review-symbol">
          <TicketIcon size={21} />
        </span>
        <div>
          <span className="eyebrow mono">{ticket.reference}</span>
          <h2>{ticket.subject}</h2>
        </div>
        <IconButton
          icon={RefreshCw}
          label="Refresh ticket"
          disabled={detail.isFetching || busy}
          onClick={() => void detail.refetch()}
        />
      </header>
      <div className="ticket-detail-body">
        {resolved && ticket.status === "RESOLVED" && (
          <Notice>Ticket resolved.</Notice>
        )}
        <div className="ticket-status-row">
          <Status value={ticket.status} />
          {ticket.upstreamStatus && <span>{ticket.upstreamStatus}</span>}
        </div>
        <dl className="ticket-metadata">
          {ticket.topic && (
            <div>
              <dt>Topic</dt>
              <dd>{ticket.topic}</dd>
            </div>
          )}
          <div>
            <dt>Created</dt>
            <dd>{dateTime(ticket.createdAt)}</dd>
          </div>
          <div>
            <dt>Updated</dt>
            <dd>{dateTime(ticket.updatedAt)}</dd>
          </div>
          <div>
            <dt>Provider binding</dt>
            <dd className="mono">{ticket.providerBindingId}</dd>
          </div>
          <div>
            <dt>Version</dt>
            <dd className="mono">{ticket.version}</dd>
          </div>
        </dl>
        <section className="ticket-section" aria-label="Ticket intake">
          <h3>Intake</h3>
          <p className="ticket-details">{ticket.details}</p>
        </section>
        <section className="ticket-section" aria-label="Ticket status history">
          <h3>Status history</h3>
          {ticket.events.length ? (
            <ol className="ticket-history">
              {ticket.events.map((event, index) => (
                <li key={`${event.createdAt}:${index}`}>
                  <span>{humanize(event.type)}</span>
                  <time>{dateTime(event.createdAt)}</time>
                </li>
              ))}
            </ol>
          ) : (
            <p className="caption">No status history supplied.</p>
          )}
        </section>
        <section className="ticket-section" aria-label="Linked conversations">
          <h3>Linked conversations</h3>
          {ticket.episodes.length ? (
            <ol className="ticket-episodes">
              {ticket.episodes.map((episode) => {
                const handover =
                  !handovers.isError &&
                  handovers.data?.find(
                    (item) =>
                      item.externalConversationRef === episode.conversationId,
                  );
                return (
                  <li key={episode.episodeId}>
                    <div className="ticket-episode-heading">
                      <strong>Episode {episode.number}</strong>
                      {handover && <Status value={handover.status} />}
                      {handover &&
                        handover.status === "ACTIVE" &&
                        handover.claimedBy &&
                        handover.claimedDisplayName && (
                          <span>{handover.claimedDisplayName}</span>
                        )}
                    </div>
                    <span className="mono">{episode.conversationId}</span>
                    <div className="ticket-episode-meta">
                      <time>{dateTime(episode.createdAt)}</time>
                      <span>
                        Opening delivery: <Status value={episode.delivery} />
                      </span>
                    </div>
                    {handover && !handovers.isError ? (
                      <button
                        className="button secondary small"
                        onClick={() =>
                          onOpenConversation(
                            handover.handoverId,
                            ticket.reference,
                          )
                        }
                      >
                        <ArrowRight size={14} />
                        Open conversation
                      </button>
                    ) : (
                      <p className="caption ticket-ai-only">
                        <Bot size={14} />
                        {handovers.isPending
                          ? "Checking human handover..."
                          : handovers.isError
                            ? "Human handover status unavailable."
                            : "No human handover in the current queue."}
                      </p>
                    )}
                  </li>
                );
              })}
            </ol>
          ) : (
            <p className="caption">No linked conversation episodes.</p>
          )}
        </section>
      </div>
      <footer className="ticket-actions">
        <span className="caption">
          {!supportsResolve
            ? "Resolution is not supported by this ticket provider."
            : ticket.status !== "OPEN"
              ? "This ticket is no longer open."
              : "Conversation control stays unchanged."}
        </span>
        <button
          className="button primary"
          title={
            !supportsResolve
              ? "This provider does not support resolution"
              : "Resolve ticket"
          }
          disabled={!mayResolve}
          onClick={() => {
            setError("");
            setConfirmation({ version: ticket.version });
          }}
        >
          <CheckCheck size={16} />
          Resolve ticket
        </button>
      </footer>
      {confirmation && (
        <Modal
          title={`Resolve ${ticket.reference}?`}
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
                Keep open
              </button>
              <button
                className="button primary"
                disabled={!mayResolve || changed}
                onClick={() => void resolve()}
              >
                <CheckCheck size={16} />
                {busy ? "Resolving..." : "Resolve ticket"}
              </button>
            </>
          }
        >
          <p>{ticket.subject}</p>
          <p className="caption">Conversation control stays unchanged.</p>
          {changed && (
            <p role="status">
              Ticket changed. Close this confirmation and review the latest
              ticket before resolving.
            </p>
          )}
          {error && <ErrorNotice compact message={error} />}
        </Modal>
      )}
    </section>
  );
}
