import { useEffect, useRef, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeftRight,
  ArrowRight,
  Bot,
  Check,
  CheckCheck,
  ChevronDown,
  Clock3,
  Headphones,
  Info,
  MessageSquare,
  LoaderCircle,
  PanelRightClose,
  PanelRightOpen,
  Paperclip,
  Search,
  Send,
  ShieldCheck,
  UserRoundCheck,
  X,
} from "lucide-react";
import { api, bindingPath, commandKey, errorMessage, post } from "./api";
import { ConversationExternalContext } from "./ConversationExternalContext";
import {
  Avatar,
  DataFields,
  dateTime,
  Empty,
  ErrorNotice,
  humanize,
  IconButton,
  Loading,
  MobileBack,
  Modal,
  relativeTime,
  Status,
} from "./components";
import type {
  Binding,
  CallbackOperation,
  CommandResult,
  Conversation,
  ConversationDetail,
  Message,
} from "./types";

export function Conversations({
  binding,
  operatorId,
  accessFresh,
}: {
  binding: Binding;
  operatorId: string;
  accessFresh: boolean;
}) {
  const client = useQueryClient();
  const [filter, setFilter] = useState("ALL");
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState("");
  const [inspector, setInspector] = useState(false);
  const canSupport = binding.roles.includes("SUPPORT");
  const path = `${bindingPath(binding.bindingId)}/conversations`;
  const queue = useQuery({
    queryKey: ["binding", binding.bindingId, "conversations"],
    queryFn: ({ signal }) =>
      api<Conversation[]>(`${path}?limit=100`, { signal }),
    enabled: canSupport,
    refetchInterval: 5000,
  });
  const items = queue.data ?? [];
  const visible = items.filter(
    (item) =>
      (filter === "ALL" ||
        (filter === "MINE"
          ? item.claimedBy === operatorId
          : filter === "WAITING"
            ? ["WAITING", "CLAIM_PENDING"].includes(item.status)
            : item.status === filter)) &&
      (!search ||
        `${item.safeCustomerLabel} ${item.externalConversationRef}`
          .toLowerCase()
          .includes(search.toLowerCase())),
  );
  useEffect(() => {
    if (
      selected &&
      queue.isSuccess &&
      !items.some((item) => item.handoverId === selected)
    )
      setSelected("");
  }, [queue.data, selected]);
  const waiting = items.filter((item) =>
    ["WAITING", "CLAIM_PENDING"].includes(item.status),
  ).length;
  if (!canSupport)
    return (
      <Empty
        icon={ShieldCheck}
        title="Support access required"
        text="Conversation access is separate from workspace ownership and action review. Ask the workspace owner to assign the Support role for this deployment and environment."
      />
    );
  return (
    <div
      className={`inbox-layout ${selected ? "has-selection" : ""} ${inspector ? "inspector-open" : ""}`}
    >
      <section className="queue-panel" aria-label="Conversation queue">
        <div className="queue-heading">
          <div>
            <span className="eyebrow">HUMAN SUPPORT</span>
            <h2>
              Inbox <span className="count-badge">{items.length}</span>
            </h2>
          </div>
          <span className="queue-waiting" title="Waiting for a support agent">
            <span />
            {waiting} waiting
          </span>
        </div>
        <label className="search-field">
          <Search size={16} />
          <input
            aria-label="Search conversations"
            type="search"
            placeholder="Search conversations"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
        <div className="queue-filters">
          <div role="tablist" aria-label="Conversation filter">
            {[
              ["ALL", "All"],
              ["WAITING", "Waiting"],
              ["MINE", "Mine"],
            ].map(([value, label]) => (
              <button
                role="tab"
                aria-selected={filter === value}
                key={value}
                onClick={() => setFilter(value!)}
              >
                {label}
              </button>
            ))}
          </div>
          <div className="filter-select">
            <select
              aria-label="Conversation status"
              value={
                ["ACTIVE", "CLOSED", "RETURNED_TO_AI"].includes(filter)
                  ? filter
                  : ""
              }
              onChange={(event) => setFilter(event.target.value || "ALL")}
            >
              <option value="">Status</option>
              <option value="ACTIVE">Active</option>
              <option value="RETURNED_TO_AI">Returned to AI</option>
              <option value="CLOSED">Closed</option>
            </select>
            <ChevronDown size={12} />
          </div>
        </div>
        {queue.isError && (
          <ErrorNotice
            compact
            message={errorMessage(queue.error)}
            retry={() => void queue.refetch()}
          />
        )}
        <div className="queue-list">
          {queue.isPending ? (
            <Loading label="Loading conversations" />
          ) : !visible.length ? (
            <Empty
              title={search ? "No matches" : "All quiet here"}
              text={
                search
                  ? "Try another name or conversation reference."
                  : "New human handovers will appear in this queue."
              }
            />
          ) : (
            visible.map((item) => (
              <button
                type="button"
                className={`queue-item ${selected === item.handoverId ? "selected" : ""}`}
                key={item.handoverId}
                onClick={() => setSelected(item.handoverId)}
                aria-pressed={selected === item.handoverId}
              >
                <div className="queue-item-top">
                  <Avatar
                    name={item.safeCustomerLabel}
                    small
                    tone={item.unreadCount ? "coral" : "neutral"}
                  />
                  <strong>{item.safeCustomerLabel || "Visitor"}</strong>
                  <time title={dateTime(item.updatedAt)}>
                    {relativeTime(item.updatedAt)}
                  </time>
                </div>
                <div className="queue-item-reference">
                  {item.externalConversationRef}
                </div>
                <div className="queue-item-bottom">
                  <Status value={item.status} />
                  <span className="queue-assignee">
                    {item.claimedDisplayName || "Unassigned"}
                  </span>
                  {item.unreadCount > 0 && (
                    <span
                      className="unread-count"
                      aria-label={`${item.unreadCount} unread messages`}
                    >
                      {item.unreadCount}
                    </span>
                  )}
                </div>
              </button>
            ))
          )}
        </div>
        <footer className="queue-footer">
          <Clock3 size={13} />
          <span>
            {queue.dataUpdatedAt
              ? `Checked ${new Date(queue.dataUpdatedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`
              : "Waiting for service"}
            {items.length === 100 ? " / latest 100" : ""}
          </span>
        </footer>
      </section>
      {selected ? (
        <Thread
          key={selected}
          binding={binding}
          operatorId={operatorId}
          handoverId={selected}
          accessFresh={accessFresh && !queue.isError}
          inspector={inspector}
          onToggleInspector={() => setInspector(!inspector)}
          onBack={() => setSelected("")}
          onChanged={() =>
            void client.invalidateQueries({
              queryKey: ["binding", binding.bindingId, "conversations"],
            })
          }
        />
      ) : (
        <section className="thread-empty">
          <Empty
            icon={MessageSquare}
            title="A conversation starts with you."
            text="Choose a conversation to read its context, pick up the handoff, and help someone move forward."
          />
          <div className="thread-empty-footer">
            <Bot size={16} />
            <span>AI brings the context.</span>
            <ArrowRight size={14} />
            <Headphones size={16} />
            <span>You bring the human touch.</span>
          </div>
        </section>
      )}
    </div>
  );
}

function Thread({
  binding,
  operatorId,
  handoverId,
  accessFresh,
  inspector,
  onToggleInspector,
  onBack,
  onChanged,
}: {
  binding: Binding;
  operatorId: string;
  handoverId: string;
  accessFresh: boolean;
  inspector: boolean;
  onToggleInspector: () => void;
  onBack: () => void;
  onChanged: () => void;
}) {
  const client = useQueryClient();
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  const [outcome, setOutcome] = useState("");
  const [busy, setBusy] = useState("");
  const [confirmation, setConfirmation] = useState<
    "return-to-ai" | "close" | null
  >(null);
  const [retrying, setRetrying] = useState("");
  const [deliveryError, setDeliveryError] = useState("");
  const commands = useRef(new Map<string, string>());
  const messageRoot = useRef<HTMLDivElement>(null);
  const nearBottom = useRef(true);
  const root = `${bindingPath(binding.bindingId)}/conversations/${encodeURIComponent(handoverId)}`;
  const key = ["binding", binding.bindingId, "conversation", handoverId];
  const detail = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => api<ConversationDetail>(root, { signal }),
    refetchInterval: 5000,
  });
  const callbackKey = ["binding", binding.bindingId, "callbacks"];
  const callbackRoot = `${bindingPath(binding.bindingId)}/operations/callbacks`;
  const callbacks = useQuery({
    queryKey: callbackKey,
    queryFn: ({ signal }) =>
      api<CallbackOperation[]>(`${callbackRoot}?limit=100`, { signal }),
    refetchInterval: 5000,
  });
  const delivery = (callbacks.data ?? []).filter(
    (item) => item.handoverId === handoverId,
  );
  const deliveryIssues = delivery.filter((item) =>
    ["DEAD", "RETRY"].includes(item.status),
  );
  const conversation = detail.data?.conversation;
  const messages = detail.data?.messages ?? [];
  useEffect(() => {
    if (nearBottom.current && messageRoot.current)
      messageRoot.current.scrollTop = messageRoot.current.scrollHeight;
  }, [messages.length, handoverId]);
  const own =
    conversation?.status === "ACTIVE" && conversation.claimedBy === operatorId;
  const mayClaim = conversation?.status === "WAITING";
  const pendingTransition =
    conversation &&
    ["CLAIM_PENDING", "RETURN_PENDING", "CLOSE_PENDING"].includes(
      conversation.status,
    );
  const writable = own && accessFresh && !detail.isError && !busy;
  async function retryCallback(callbackId: string) {
    if (retrying || !accessFresh) return;
    setRetrying(callbackId);
    setDeliveryError("");
    const request = commandKey(commands.current, `callback:${callbackId}`);
    try {
      const result = await post<{
        accepted: boolean;
        outcome: string;
        callbacks: CallbackOperation[];
      }>(`${callbackRoot}/${encodeURIComponent(callbackId)}/retry`, {
        idempotencyKey: request.id,
      });
      if (!result.accepted)
        throw new Error(humanize(result.outcome || "Retry not accepted"));
      request.done();
      client.setQueryData(callbackKey, result.callbacks);
      void callbacks.refetch();
    } catch (error) {
      setDeliveryError(errorMessage(error));
    } finally {
      setRetrying("");
    }
  }
  async function command(operation: string) {
    if (busy) return false;
    setBusy(operation);
    setError("");
    setOutcome("");
    const payload = operation === "messages" ? { text: text.trim() } : {};
    const request = commandKey(
      commands.current,
      operation,
      JSON.stringify(payload),
    );
    try {
      const result = await post<CommandResult>(`${root}/${operation}`, {
        ...payload,
        idempotencyKey: request.id,
      });
      if (!result.accepted)
        throw new Error(humanize(result.outcome || "Command not accepted"));
      request.done();
      if (result.conversation) client.setQueryData(key, result.conversation);
      setOutcome(humanize(result.outcome || "Accepted"));
      setConfirmation(null);
      if (operation === "messages") {
        setText("");
        nearBottom.current = true;
      }
      void detail.refetch();
      onChanged();
      return true;
    } catch (error) {
      setError(errorMessage(error));
      return false;
    } finally {
      setBusy("");
    }
  }
  if (detail.isPending)
    return (
      <section className="thread-panel">
        <Loading label="Opening conversation" />
      </section>
    );
  if (!conversation || !detail.data)
    return (
      <section className="thread-panel">
        <div className="mobile-back-row">
          <MobileBack onClick={onBack} />
        </div>
        <ErrorNotice
          message={errorMessage(detail.error)}
          retry={() => void detail.refetch()}
        />
      </section>
    );
  async function send(event: FormEvent) {
    event.preventDefault();
    if (writable && text.trim()) await command("messages");
  }
  return (
    <>
      <section className="thread-panel" aria-label="Conversation transcript">
        <header className="thread-header">
          <MobileBack onClick={onBack} />
          <Avatar name={conversation.safeCustomerLabel} tone="coral" />
          <div className="thread-heading">
            <h2>{conversation.safeCustomerLabel || "Visitor"}</h2>
            <div>
              <Status value={conversation.status} />
              <span>
                {conversation.claimedDisplayName || "Not assigned yet"}
              </span>
            </div>
          </div>
          <div className="thread-tools">
            <IconButton
              icon={inspector ? PanelRightClose : PanelRightOpen}
              label={
                inspector
                  ? "Hide conversation context"
                  : "Show conversation context"
              }
              onClick={onToggleInspector}
            />
            <IconButton
              icon={ArrowLeftRight}
              label="Return to AI"
              disabled={!writable}
              onClick={() => setConfirmation("return-to-ai")}
            />
            <IconButton
              icon={CheckCheck}
              label="Close conversation"
              disabled={!writable}
              onClick={() => setConfirmation("close")}
            />
          </div>
        </header>
        {detail.isError && (
          <ErrorNotice
            compact
            message={`Updates paused. ${errorMessage(detail.error)}`}
            retry={() => void detail.refetch()}
          />
        )}
        {deliveryIssues.length > 0 && !inspector && (
          <div className="notice compact delivery-attention">
            <Info size={16} />
            <span>
              {deliveryIssues.length} delivery operation
              {deliveryIssues.length === 1 ? "" : "s"} need attention.
            </span>
            <button className="text-button" onClick={onToggleInspector}>
              View delivery
            </button>
          </div>
        )}
        {mayClaim && (
          <div className="claim-banner">
            <span className="claim-icon">
              <Headphones size={17} />
            </span>
            <div>
              <strong>A human touch is needed.</strong>
              <span>Claim this conversation to start replying.</span>
            </div>
            <button
              className="button primary small"
              disabled={!!busy || !accessFresh || detail.isError}
              onClick={() => void command("claim")}
            >
              <UserRoundCheck size={15} />
              {busy === "claim" ? "Claiming..." : "Claim"}
            </button>
          </div>
        )}
        {pendingTransition && (
          <div className="notice compact" role="status">
            <Clock3 size={16} />
            <span>
              {humanize(conversation.status)}. Waiting for deployment
              confirmation.
            </span>
          </div>
        )}
        {conversation.status === "ACTIVE" && !own && (
          <div className="notice compact">
            <Info size={16} />
            <span>
              {conversation.claimedDisplayName || "Another agent"} is handling
              this conversation.
            </span>
          </div>
        )}
        <div
          className="message-stream"
          ref={messageRoot}
          role="log"
          aria-label="Messages"
          onScroll={() => {
            const el = messageRoot.current;
            if (el)
              nearBottom.current =
                el.scrollHeight - el.scrollTop - el.clientHeight < 100;
          }}
        >
          <div className="transcript-start">
            <span />
            <time>
              {new Date(conversation.createdAt).toLocaleDateString(undefined, {
                month: "long",
                day: "numeric",
                year: "numeric",
              })}
            </time>
            <span />
          </div>
          {messages.length ? (
            messages.map((message, index) => (
              <MessageRow
                key={message.messageId}
                message={message}
                previous={messages[index - 1]}
              />
            ))
          ) : (
            <Empty
              title="No messages yet"
              text="The permitted conversation transcript will appear here."
            />
          )}
        </div>
        <div className="composer-area">
          {error && <ErrorNotice message={error} compact />}
          {outcome && (
            <div className="command-outcome" role="status">
              <Check size={14} />
              {outcome}
            </div>
          )}
          {["CLOSED", "RETURNED_TO_AI"].includes(conversation.status) ? (
            <div className="resolved-banner">
              <span className="resolved-icon">
                {conversation.status === "CLOSED" ? (
                  <CheckCheck size={20} />
                ) : (
                  <Bot size={20} />
                )}
              </span>
              <div>
                <strong>
                  {conversation.status === "CLOSED"
                    ? "Conversation closed"
                    : "Back with AI"}
                </strong>
                <span>
                  {conversation.status === "CLOSED"
                    ? "This support conversation is read-only."
                    : "The AI is handling the conversation again."}
                </span>
              </div>
            </div>
          ) : (
            <form
              onSubmit={send}
              className={`composer ${!own ? "disabled" : ""}`}
            >
              <label htmlFor="reply">
                Reply to {conversation.safeCustomerLabel || "visitor"}
                <span>
                  <span className="status-dot" />
                  Human reply
                </span>
              </label>
              <textarea
                id="reply"
                placeholder={
                  pendingTransition
                    ? "Waiting for deployment confirmation..."
                    : own
                      ? "Write a thoughtful reply..."
                      : "Claim this conversation to reply"
                }
                value={text}
                onChange={(event) => setText(event.target.value)}
                disabled={!writable}
                rows={3}
                maxLength={16000}
              />
              <div className="composer-footer">
                <span>
                  {own
                    ? "Sent through the deployment support channel"
                    : "Only the assigned agent can send messages"}
                </span>
                <button
                  type="submit"
                  className="send-button"
                  title="Send reply"
                  aria-label="Send reply"
                  disabled={!writable || !text.trim()}
                >
                  {busy === "messages" ? (
                    <LoaderCircle size={17} className="spin" />
                  ) : (
                    <Send size={17} />
                  )}
                </button>
              </div>
            </form>
          )}
        </div>
      </section>
      {inspector && (
        <aside className="context-panel" aria-label="Conversation context">
          <header>
            <h2>Conversation context</h2>
            <IconButton
              className="inspector-close"
              icon={X}
              label="Hide conversation context"
              onClick={onToggleInspector}
            />
          </header>
          <ConversationExternalContext
            root={root}
            bindingId={binding.bindingId}
            handoverId={handoverId}
            accessFresh={accessFresh && !detail.isError}
          />
          <section>
            <Avatar name={conversation.safeCustomerLabel} tone="coral" />
            <h3>{conversation.safeCustomerLabel || "Visitor"}</h3>
            <span className="muted">
              Contact information shared for support
            </span>
            {Object.keys(detail.data.consentedIntake ?? {}).length ? (
              <DataFields value={detail.data.consentedIntake} />
            ) : (
              <p className="caption">No contact details were shared.</p>
            )}
          </section>
          <section>
            <span className="eyebrow">HANDOFF DETAILS</span>
            <dl className="inspector-fields">
              <div>
                <dt>Status</dt>
                <dd>
                  <Status value={conversation.status} />
                </dd>
              </div>
              <div>
                <dt>Assigned to</dt>
                <dd>{conversation.claimedDisplayName || "Unassigned"}</dd>
              </div>
              <div>
                <dt>Started</dt>
                <dd>{dateTime(conversation.createdAt)}</dd>
              </div>
              <div>
                <dt>Last updated</dt>
                <dd>{dateTime(conversation.updatedAt)}</dd>
              </div>
            </dl>
          </section>
          <section>
            <span className="eyebrow">DELIVERY TO DEPLOYMENT</span>
            {callbacks.isError ? (
              <ErrorNotice
                compact
                message={errorMessage(callbacks.error)}
                retry={() => void callbacks.refetch()}
              />
            ) : !delivery.length ? (
              <p className="caption">
                No delivery operations in the latest 100 records.
              </p>
            ) : (
              <div className="delivery-list">
                {delivery.map((item) => (
                  <div className="delivery-operation" key={item.callbackId}>
                    <div>
                      <strong>{humanize(item.eventType)}</strong>
                      <Status value={item.status} />
                    </div>
                    <span>
                      {item.attemptCount} attempt
                      {item.attemptCount === 1 ? "" : "s"}
                      {item.failureCode ? ` / ${item.failureCode}` : ""}
                    </span>
                    {item.retryEligible && (
                      <button
                        className="button secondary small"
                        disabled={
                          !!retrying || !accessFresh || callbacks.isError
                        }
                        onClick={() => void retryCallback(item.callbackId)}
                      >
                        {retrying === item.callbackId
                          ? "Requesting..."
                          : "Retry delivery"}
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}
            {deliveryError && <ErrorNotice compact message={deliveryError} />}
          </section>
          <section>
            <span className="eyebrow">WORKSPACE</span>
            <dl className="inspector-fields">
              <div>
                <dt>Deployment</dt>
                <dd>{binding.deploymentName}</dd>
              </div>
              <div>
                <dt>Environment</dt>
                <dd>{binding.environment}</dd>
              </div>
              <div>
                <dt>Conversation</dt>
                <dd className="mono">{conversation.externalConversationRef}</dd>
              </div>
            </dl>
          </section>
          <div className="context-note">
            <ShieldCheck size={17} />
            <p>
              Only the approved transcript and consented details are available
              here. The deployment remains the conversation authority.
            </p>
          </div>
        </aside>
      )}
      {confirmation && (
        <Modal
          title={
            confirmation === "close"
              ? "Close this conversation?"
              : "Return this conversation to AI?"
          }
          onClose={() => {
            if (!busy) setConfirmation(null);
          }}
          footer={
            <>
              <button
                className="button secondary"
                disabled={!!busy}
                onClick={() => setConfirmation(null)}
              >
                Keep conversation
              </button>
              <button
                className={`button ${confirmation === "close" ? "danger" : "primary"}`}
                disabled={!!busy || !accessFresh || detail.isError}
                onClick={() => void command(confirmation)}
              >
                {busy
                  ? "Sending..."
                  : confirmation === "close"
                    ? "Close conversation"
                    : "Return to AI"}
              </button>
            </>
          }
        >
          <p>
            {confirmation === "close"
              ? "This closes the runtime conversation. It does not resolve an external case. The transcript remains available."
              : "The AI will resume handling this conversation after the deployment accepts the transition."}
          </p>
          {error && <ErrorNotice message={error} />}
        </Modal>
      )}
    </>
  );
}

function MessageRow({
  message,
  previous,
}: {
  message: Message;
  previous?: Message;
}) {
  const role = message.participantRole.toUpperCase();
  const agent = ["OPERATOR", "AGENT", "HUMAN_AGENT", "HUMAN"].includes(role);
  const bot = ["ASSISTANT", "AI"].includes(role);
  const system = role === "SYSTEM";
  const author =
    message.safeActorDisplayName ||
    (bot
      ? "AI assistant"
      : agent
        ? "Support agent"
        : system
          ? "System"
          : "Visitor");
  const grouped =
    previous?.actorRef === message.actorRef &&
    previous?.participantRole === message.participantRole &&
    previous?.safeActorDisplayName === message.safeActorDisplayName;
  if (system)
    return (
      <div className="system-message">
        <Info size={13} />
        <span>{message.content}</span>
        <time title={dateTime(message.createdAt)}>
          {new Date(message.createdAt).toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
          })}
        </time>
      </div>
    );
  return (
    <article
      className={`message-row ${agent ? "agent-message" : bot ? "assistant-message" : "customer-message"} ${grouped ? "grouped" : ""}`}
    >
      <div className="message-avatar">
        {!grouped &&
          (bot ? (
            <span className="avatar bot-avatar">
              <Bot size={17} />
            </span>
          ) : (
            <Avatar name={author} small tone={agent ? "teal" : "neutral"} />
          ))}
      </div>
      <div className="message-body">
        {!grouped && (
          <div className="message-meta">
            <strong>{author}</strong>
            {bot && <span className="message-role">AI</span>}
            {agent && <span className="message-role">Agent</span>}
          </div>
        )}
        <div className="message-content">{message.content}</div>
        {!!message.attachmentRefs?.length && (
          <div className="message-attachments">
            {message.attachmentRefs.map((ref) => (
              <span key={ref} title={ref}>
                <Paperclip size={13} />
                {ref}
              </span>
            ))}
          </div>
        )}
        <time className="message-time" title={dateTime(message.createdAt)}>
          {new Date(message.createdAt).toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
          })}
        </time>
      </div>
    </article>
  );
}
