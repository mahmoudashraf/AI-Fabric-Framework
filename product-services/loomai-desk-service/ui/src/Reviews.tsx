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
import { api, bindingPath, commandKey, errorMessage, post } from "./api";
import {
  DataFields,
  dateTime,
  Empty,
  ErrorNotice,
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
  const [filter, setFilter] = useState("PENDING");
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
                {tasks.data?.filter((task) => task.status === "PENDING")
                  .length ?? 0}
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
            {["PENDING", "ALL"].map((item) => (
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
                    Action proposal
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
  const detail = useQuery({
    queryKey: ["binding", binding.bindingId, "review", taskId],
    queryFn: ({ signal }) => api<ReviewDetail>(path, { signal }),
    refetchInterval: 5000,
  });
  const task = detail.data?.task;
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
      setReceipt(result);
      setDecision(null);
      await client.invalidateQueries({
        queryKey: ["binding", binding.bindingId, "reviews"],
      });
      await detail.refetch();
    } catch (error) {
      setError(errorMessage(error));
      void detail.refetch();
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
    task.status === "PENDING" && (!receipt || !!receipt.failureReason);
  const expires = task.expiresAt
    ? new Date(task.expiresAt).getTime() <= Date.now()
    : false;
  const outcome = receipt?.outcome ?? detail.data.outcome;
  return (
    <section className="review-detail">
      <header className="review-detail-top">
        <MobileBack onClick={onBack} />
        <span className="review-symbol">
          <FileCheck2 size={22} />
        </span>
        <div>
          <span className="eyebrow">ACTION PROPOSAL</span>
          <h2>{task.title}</h2>
        </div>
        <Status value={receipt?.task?.status ?? task.status} />
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
              {task.policyId
                ? `${task.policyId.name} / ${task.policyId.version}`
                : "Not supplied"}
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
        {detail.data.proposal && (
          <section className="proposal-section">
            <h3>Proposed action details</h3>
            <DataFields value={detail.data.proposal} />
          </section>
        )}
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
        {detail.data.successorTaskId && (
          <div className="notice">
            Follow-up task:{" "}
            <span className="mono">{detail.data.successorTaskId}</span>
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
            className={`decision-receipt ${receipt.failureReason || outcome?.success === false ? "failed" : ""}`}
            role="status"
          >
            <span className="eyebrow">DECISION RECEIPT</span>
            <h3>
              {receipt.task?.status
                ? `Task ${receipt.task.status.toLowerCase()}`
                : "Decision response received"}
            </h3>
            {receipt.message && <p>{receipt.message}</p>}
            {receipt.failureReason && (
              <ErrorNotice message={receipt.failureReason} />
            )}
            {!outcome && (
              <p>
                No business execution outcome was returned. Approval alone does
                not confirm that the action succeeded.
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
                expires ||
                !accessFresh ||
                detail.isError ||
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
                expires ||
                !accessFresh ||
                detail.isError ||
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
                  expires ||
                  !accessFresh ||
                  detail.isError ||
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
