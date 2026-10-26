import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Check,
  CheckCheck,
  Clock3,
  FileCheck2,
  Search,
  ShieldCheck,
  X,
} from "lucide-react";
import {
  ApiError,
  api,
  bindingPath,
  commandKey,
  errorMessage,
  post,
} from "./api";
import {
  DataFields,
  dateTime,
  Empty,
  ErrorNotice,
  humanize,
  Loading,
  MobileBack,
  Modal,
  Status,
} from "./components";
import type {
  Binding,
  DecisionResult,
  ReviewDetail,
  ReviewTask,
} from "./types";

export function Reviews({
  binding,
  accessFresh,
}: {
  binding: Binding;
  accessFresh: boolean;
}) {
  const [selected, setSelected] = useState("");
  const [filter, setFilter] = useState<"WAITING_FOR_REVIEW" | "ALL">(
    "WAITING_FOR_REVIEW",
  );
  const [search, setSearch] = useState("");
  const canReview = binding.roles.includes("REVIEWER");
  const tasks = useQuery({
    queryKey: ["binding", binding.bindingId, "reviews"],
    queryFn: ({ signal }) =>
      api<ReviewTask[]>(`${bindingPath(binding.bindingId)}/reviews`, {
        signal,
      }),
    enabled: canReview,
    refetchInterval: 5000,
  });
  const visible = (tasks.data ?? []).filter(
    (task) =>
      (filter === "ALL" || task.status === filter) &&
      `${task.title} ${task.summary}`
        .toLowerCase()
        .includes(search.toLowerCase()),
  );
  if (!canReview)
    return (
      <Empty
        icon={ShieldCheck}
        title="Reviewer access required"
        text="Support and action-review permissions are separate. Ask the workspace owner to assign the Reviewer role for this deployment and environment."
      />
    );
  return (
    <div className={`reviews-layout ${selected ? "has-selection" : ""}`}>
      <section className="queue-panel" aria-label="Action review queue">
        <div className="queue-heading">
          <div>
            <span className="eyebrow">CONSIDERED DECISIONS</span>
            <h2>
              Action reviews{" "}
              <span className="count-badge">
                {tasks.data?.filter(
                  (task) => task.status === "WAITING_FOR_REVIEW",
                ).length ?? 0}
              </span>
            </h2>
          </div>
        </div>
        <label className="search-field">
          <Search size={16} />
          <input
            aria-label="Search reviews"
            type="search"
            placeholder="Search action reviews"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
        <div className="queue-filters">
          <div role="tablist" aria-label="Review filter">
            {(["WAITING_FOR_REVIEW", "ALL"] as const).map((item) => (
              <button
                role="tab"
                aria-selected={filter === item}
                key={item}
                onClick={() => setFilter(item)}
              >
                {item === "ALL" ? "All tasks" : "Pending"}
              </button>
            ))}
          </div>
        </div>
        {tasks.isError && (
          <ErrorNotice
            compact
            message={errorMessage(tasks.error)}
            retry={() => void tasks.refetch()}
          />
        )}
        <div className="queue-list">
          {tasks.isPending ? (
            <Loading label="Loading reviews" />
          ) : visible.length ? (
            visible.map((task) => (
              <button
                className={`review-item ${selected === task.taskId ? "selected" : ""}`}
                key={task.taskId}
                onClick={() => setSelected(task.taskId)}
                aria-pressed={selected === task.taskId}
              >
                <div className="review-item-meta">
                  <span>
                    <FileCheck2 size={14} />
                    {humanize(task.type)}
                  </span>
                  <Status value={task.status} />
                </div>
                <h3>{task.title}</h3>
                <p>{task.summary}</p>
                <span className="review-expiry">
                  <Clock3 size={12} />
                  {task.expiresAt
                    ? `Expires ${dateTime(task.expiresAt)}`
                    : `Created ${dateTime(task.createdAt)}`}
                </span>
              </button>
            ))
          ) : (
            <Empty
              icon={CheckCheck}
              title="Nothing waiting on you"
              text="Action proposals requiring a decision will appear here."
            />
          )}
        </div>
      </section>
      {selected ? (
        <ReviewInspector
          key={selected}
          binding={binding}
          taskId={selected}
          accessFresh={accessFresh && !tasks.isError}
          onBack={() => setSelected("")}
        />
      ) : (
        <section className="thread-empty">
          <Empty
            icon={FileCheck2}
            title="Your judgment matters."
            text="Choose an action proposal to inspect its details and record an explicit decision."
          />
          <div className="thread-empty-footer">
            <ShieldCheck size={16} />
            <span>Review is separate from conversation support.</span>
          </div>
        </section>
      )}
    </div>
  );
}

function ReviewInspector({
  binding,
  taskId,
  accessFresh,
  onBack,
}: {
  binding: Binding;
  taskId: string;
  accessFresh: boolean;
  onBack: () => void;
}) {
  const client = useQueryClient();
  const [decision, setDecision] = useState<"APPROVE" | "REJECT" | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [receipt, setReceipt] = useState<DecisionResult | null>(null);
  const ids = useRef(new Map<string, string>());
  const path = `${bindingPath(binding.bindingId)}/reviews/${encodeURIComponent(taskId)}`;
  const detailKey = ["binding", binding.bindingId, "review", taskId];
  const detail = useQuery({
    queryKey: detailKey,
    queryFn: ({ signal }) => api<ReviewDetail>(path, { signal }),
    refetchInterval: 5000,
  });
  const task = detail.data?.task;
  function recordResult(result: DecisionResult) {
    setReceipt(result);
    setDecision(null);
    if (result.task) {
      const returnedTask = result.task;
      client.setQueryData<ReviewDetail>(detailKey, (previous) =>
        previous ? { ...previous, task: returnedTask } : previous,
      );
    }
  }
  async function decide() {
    if (!task || !decision || busy) return;
    setBusy(true);
    setError("");
    const request = commandKey(ids.current, decision, String(task.version));
    try {
      const result = await post<DecisionResult>(`${path}/decisions`, {
        decisionId: request.id,
        decision,
        expectedVersion: task.version,
      });
      request.done();
      recordResult(result);
      await client.invalidateQueries({
        queryKey: ["binding", binding.bindingId, "reviews"],
      });
      await detail.refetch();
    } catch (error) {
      if (error instanceof ApiError && error.failure) {
        request.done();
        recordResult(error.payload as DecisionResult);
      } else {
        setError(errorMessage(error));
      }
      void detail.refetch();
      void client.invalidateQueries({
        queryKey: ["binding", binding.bindingId, "reviews"],
      });
    } finally {
      setBusy(false);
    }
  }
  if (detail.isPending)
    return (
      <div className="review-detail">
        <Loading label="Opening action proposal" />
      </div>
    );
  if (!task || !detail.data)
    return (
      <section className="review-detail">
        <MobileBack onClick={onBack} />
        <ErrorNotice
          message={errorMessage(detail.error)}
          retry={() => void detail.refetch()}
        />
      </section>
    );
  const pending =
    task.status === "WAITING_FOR_REVIEW" && (!receipt || !!receipt.failure);
  const expires = new Date(task.expiresAt).getTime() <= Date.now();
  const outcome = detail.data.outcome ?? receipt?.outcome ?? null;
  const successorTaskId =
    detail.data.successorTaskId ?? receipt?.successorTaskId ?? null;
  const decisionUnavailable =
    !accessFresh ||
    detail.isError ||
    expires ||
    task.status !== "WAITING_FOR_REVIEW" ||
    !!(receipt?.failure && !receipt.failure.retryable);
  return (
    <section className="review-detail">
      <header className="review-detail-top">
        <MobileBack onClick={onBack} />
        <span className="review-symbol">
          <FileCheck2 size={22} />
        </span>
        <div>
          <span className="eyebrow">{humanize(task.type)}</span>
          <h2>{task.title}</h2>
        </div>
        <Status value={task.status} />
      </header>
      <div className="review-detail-body">
        {detail.isError && (
          <ErrorNotice
            message={errorMessage(detail.error)}
            retry={() => void detail.refetch()}
          />
        )}
        <p className="review-summary">{task.summary}</p>
        <div className="review-metadata">
          <div>
            <span>Policy</span>
            <strong>
              {`${task.policyId.name} / ${task.policyId.version}`}
            </strong>
          </div>
          <div>
            <span>Version</span>
            <strong>{task.version}</strong>
          </div>
          <div>
            <span>Created</span>
            <strong>{dateTime(task.createdAt)}</strong>
          </div>
          <div>
            <span>Expires</span>
            <strong>{dateTime(task.expiresAt)}</strong>
          </div>
        </div>
        {detail.data.requestedInformation != null && (
          <section className="proposal-section">
            <h3>Requested information</h3>
            <DataFields value={detail.data.requestedInformation} />
          </section>
        )}
        {detail.data.suppliedInformation != null && (
          <section className="proposal-section">
            <h3>Supplied information</h3>
            <DataFields value={detail.data.suppliedInformation} />
          </section>
        )}
        {successorTaskId && (
          <div className="notice">
            Follow-up task: <span className="mono">{successorTaskId}</span>
          </div>
        )}
        {detail.data.message && (
          <div className="notice">{detail.data.message}</div>
        )}
        {detail.data.failureReason && (
          <ErrorNotice message={detail.data.failureReason} />
        )}
        {receipt && (
          <section
            className={`decision-receipt ${receipt.failure ? "failed" : ""}`}
            role={receipt.failure ? "alert" : "status"}
          >
            <span className="eyebrow">DECISION RECEIPT</span>
            <h3>
              {receipt.failure
                ? "Decision not accepted"
                : receipt.task?.status
                  ? `Task ${receipt.task.status.toLowerCase()}`
                  : "Decision response received"}
            </h3>
            {receipt.failure && (
              <>
                <p>{receipt.failure.publicMessage}</p>
                <p className="mono">{receipt.failure.reason}</p>
                <p className="caption">
                  {receipt.failure.retryable
                    ? "Retry permitted. No automatic retry was made."
                    : "This decision is not retryable."}
                </p>
              </>
            )}
            {!outcome && (
              <p>
                No business execution outcome was returned. The decision alone
                does not confirm business execution.
              </p>
            )}
          </section>
        )}
        {outcome && (
          <section className="proposal-section">
            <h3>Returned execution outcome</h3>
            <DataFields value={outcome} />
          </section>
        )}
        <div className="review-boundary">
          <ShieldCheck size={18} />
          <p>
            Your decision is recorded under your reviewer identity. Approval may
            continue execution through the deployment's existing action policy.
          </p>
        </div>
        {error && !decision && <ErrorNotice message={error} />}
      </div>
      {pending && (
        <footer className="review-actions">
          <span>
            <Clock3 size={15} />
            {expires ? "This task has expired" : "Awaiting your decision"}
          </span>
          <div>
            <button
              className="button secondary reject"
              disabled={
                busy ||
                decisionUnavailable ||
                !task.allowedDecisions.includes("REJECT")
              }
              onClick={() => {
                setError("");
                setDecision("REJECT");
              }}
            >
              <X size={16} />
              Reject
            </button>
            <button
              className="button primary"
              disabled={
                busy ||
                decisionUnavailable ||
                !task.allowedDecisions.includes("APPROVE")
              }
              onClick={() => {
                setError("");
                setDecision("APPROVE");
              }}
            >
              <Check size={16} />
              Approve
            </button>
          </div>
        </footer>
      )}
      {decision && (
        <Modal
          title={
            decision === "APPROVE"
              ? "Approve this action?"
              : "Reject this action?"
          }
          onClose={() => {
            if (!busy) setDecision(null);
          }}
          footer={
            <>
              <button
                className="button secondary"
                disabled={busy}
                onClick={() => setDecision(null)}
              >
                Cancel
              </button>
              <button
                className={`button ${decision === "APPROVE" ? "primary" : "danger"}`}
                disabled={
                  busy ||
                  decisionUnavailable ||
                  !task.allowedDecisions.includes(decision)
                }
                onClick={() => void decide()}
              >
                {busy
                  ? "Recording..."
                  : decision === "APPROVE"
                    ? "Approve action"
                    : "Reject action"}
              </button>
            </>
          }
        >
          <p>
            <strong>{task.title}</strong>
          </p>
          <p>
            {decision === "APPROVE"
              ? "Approval can authorize the deployment to continue this action. The service will return the actual decision and execution outcome."
              : "Rejection records your decision and does not authorize this action to execute."}
          </p>
          <p className="caption">
            Reviewing version {task.version}. The deployment checks permission,
            expiry, and decision conflicts.
          </p>
          {error && <ErrorNotice message={error} />}
        </Modal>
      )}
    </section>
  );
}
