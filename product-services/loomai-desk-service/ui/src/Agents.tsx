import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  CheckCheck,
  Headphones,
  Pencil,
  Plus,
  ShieldCheck,
  Trash2,
  Users,
} from "lucide-react";
import { api, bindingPath, errorMessage, post } from "./api";
import {
  Avatar,
  Empty,
  ErrorNotice,
  IconButton,
  Loading,
  Modal,
  Notice,
} from "./components";
import type { Agent, Binding, Role } from "./types";

export function Agents({
  binding,
  accessFresh,
}: {
  binding: Binding;
  accessFresh: boolean;
}) {
  const [assigning, setAssigning] = useState(false);
  const [editing, setEditing] = useState<Agent | null>(null);
  const [removing, setRemoving] = useState<Agent | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const root = `${bindingPath(binding.bindingId)}/agents`;
  const owner = binding.roles.includes("OWNER");
  const agents = useQuery({
    queryKey: ["binding", binding.bindingId, "agents"],
    queryFn: ({ signal }) => api<Agent[]>(root, { signal }),
    enabled: owner,
    refetchInterval: 5000,
  });
  async function revoke() {
    if (!removing || busy || !accessFresh) return;
    setBusy(true);
    setError("");
    try {
      await api(`${root}/${encodeURIComponent(removing.agentId)}`, {
        method: "DELETE",
      });
      setNotice(
        `Access removed for ${removing.displayName || removing.email || "agent"}.`,
      );
      setRemoving(null);
      await agents.refetch();
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  if (!owner)
    return (
      <Empty
        icon={ShieldCheck}
        title="Owner access required"
        text="Only the workspace owner can assign or revoke agent access."
      />
    );
  return (
    <section className="agents-page">
      <header className="page-heading">
        <div>
          <span className="eyebrow">PEOPLE MAKE THE DIFFERENCE</span>
          <h2>Your support team</h2>
          <p>
            Access for <strong>{binding.deploymentName}</strong>{" "}
            <span className="inline-dot" /> {binding.environment}
          </p>
        </div>
        <button
          className="button primary"
          disabled={!accessFresh}
          onClick={() => {
            setAssigning(true);
            setError("");
          }}
        >
          <Plus size={17} />
          Assign agent
        </button>
      </header>
      <div className="scope-note">
        <ShieldCheck size={18} />
        <p>
          Assignments cover this deployment and environment, including all
          connected apps. Support and review permissions are independent.
        </p>
      </div>
      {notice && <Notice>{notice}</Notice>}
      {agents.isError && (
        <ErrorNotice
          message={errorMessage(agents.error)}
          retry={() => void agents.refetch()}
        />
      )}
      <div className="team-table">
        <div className="team-table-header">
          <h3>
            Assigned agents{" "}
            <span className="count-badge">{agents.data?.length ?? 0}</span>
          </h3>
          <span className="caption">Registered accounts only</span>
        </div>
        {agents.isPending ? (
          <Loading label="Loading team" />
        ) : agents.data?.length ? (
          <table>
            <thead>
              <tr>
                <th>Agent</th>
                <th>Permissions</th>
                <th>
                  <span className="sr-only">Manage</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {agents.data.map((agent) => (
                <tr key={agent.agentId}>
                  <td>
                    <div className="team-person">
                      <Avatar
                        name={agent.displayName || agent.email || "Agent"}
                        tone="teal"
                      />
                      <div>
                        <strong>
                          {agent.displayName ||
                            agent.email ||
                            "Registered agent"}
                        </strong>
                        <span>{agent.email || agent.agentReference}</span>
                      </div>
                    </div>
                  </td>
                  <td>
                    <div className="role-list">
                      {agent.roles.map((role) => (
                        <span
                          className={`role-badge ${role.toLowerCase()}`}
                          key={role}
                        >
                          {role === "SUPPORT" ? (
                            <Headphones size={12} />
                          ) : role === "REVIEWER" ? (
                            <CheckCheck size={12} />
                          ) : (
                            <ShieldCheck size={12} />
                          )}
                          {role === "REVIEWER"
                            ? "Reviewer"
                            : role === "SUPPORT"
                              ? "Support"
                              : "Owner"}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td>
                    <div className="row-actions">
                      <IconButton
                        icon={Pencil}
                        label={`Edit permissions for ${agent.displayName || agent.email || "agent"}`}
                        onClick={() => setEditing(agent)}
                        disabled={!accessFresh}
                      />
                      <IconButton
                        icon={Trash2}
                        label={`Remove access for ${agent.displayName || agent.email || "agent"}`}
                        onClick={() => {
                          setRemoving(agent);
                          setError("");
                        }}
                        disabled={agent.roles.includes("OWNER") || !accessFresh}
                      />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <Empty
            icon={Users}
            title="Build your support team"
            text="Assign a registered agent to start handling conversations or reviewing actions."
          />
        )}
      </div>
      <div className="team-guidance">
        <div>
          <Headphones size={18} />
          <h3>Support agents</h3>
          <p>
            Read and claim conversations, reply to visitors, return to AI, and
            close a support session.
          </p>
        </div>
        <div>
          <CheckCheck size={18} />
          <h3>Action reviewers</h3>
          <p>
            Inspect action proposals and make an explicit approval or rejection.
            No conversation access is implied.
          </p>
        </div>
      </div>
      {assigning && (
        <AssignModal
          binding={binding}
          accessFresh={accessFresh}
          onClose={() => setAssigning(false)}
          onAssigned={() => {
            setAssigning(false);
            setNotice("Agent assigned to this workspace.");
            void agents.refetch();
          }}
        />
      )}
      {editing && (
        <AssignModal
          binding={binding}
          accessFresh={accessFresh}
          agent={editing}
          onClose={() => setEditing(null)}
          onAssigned={() => {
            setEditing(null);
            setNotice("Agent permissions updated.");
            void agents.refetch();
          }}
        />
      )}
      {removing && (
        <Modal
          title="Remove agent access?"
          onClose={() => {
            if (!busy) setRemoving(null);
          }}
          footer={
            <>
              <button
                className="button secondary"
                disabled={busy}
                onClick={() => setRemoving(null)}
              >
                Cancel
              </button>
              <button
                className="button danger"
                disabled={busy || !accessFresh}
                onClick={() => void revoke()}
              >
                {busy ? "Removing..." : "Remove access"}
              </button>
            </>
          }
        >
          <p>
            <strong>{removing.displayName || removing.email}</strong> will lose
            their assigned permissions for {binding.deploymentName} (
            {binding.environment}).
          </p>
          <p>
            Existing conversations are not deleted or automatically returned to
            AI.
          </p>
          {error && <ErrorNotice message={error} />}
        </Modal>
      )}
    </section>
  );
}

function AssignModal({
  binding,
  accessFresh,
  agent,
  onClose,
  onAssigned,
}: {
  binding: Binding;
  accessFresh: boolean;
  agent?: Agent;
  onClose: () => void;
  onAssigned: () => void;
}) {
  const [method, setMethod] = useState<"email" | "agentReference">(
    agent ? "agentReference" : "email",
  );
  const [identifier, setIdentifier] = useState(
    agent?.agentReference ?? agent?.agentId ?? "",
  );
  const [support, setSupport] = useState(
    agent ? agent.roles.includes("SUPPORT") : true,
  );
  const [reviewer, setReviewer] = useState(
    agent ? agent.roles.includes("REVIEWER") : false,
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function assign(event: FormEvent) {
    event.preventDefault();
    if (busy || !accessFresh || (!support && !reviewer)) return;
    setBusy(true);
    setError("");
    const roles: Role[] = [
      ...(support ? ["SUPPORT" as const] : []),
      ...(reviewer ? ["REVIEWER" as const] : []),
    ];
    try {
      await post(`${bindingPath(binding.bindingId)}/agents`, {
        [method]: identifier.trim(),
        roles,
      });
      onAssigned();
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      title={agent ? "Edit agent permissions" : "Assign an agent"}
      onClose={() => {
        if (!busy) onClose();
      }}
      footer={
        <>
          <button
            className="button secondary"
            disabled={busy}
            onClick={onClose}
          >
            Cancel
          </button>
          <button
            className="button primary"
            form="assign-agent"
            disabled={
              busy ||
              !accessFresh ||
              !identifier.trim() ||
              (!support && !reviewer)
            }
          >
            {busy ? "Saving..." : agent ? "Save permissions" : "Assign agent"}
            <Plus size={16} />
          </button>
        </>
      }
    >
      <p>
        The agent needs a verified, registered Desk account first. Access is
        scoped to{" "}
        <strong>
          {binding.deploymentName} / {binding.environment}
        </strong>
        .
      </p>
      <form id="assign-agent" onSubmit={assign}>
        {agent ? (
          <div className="team-person">
            <Avatar
              name={agent.displayName || agent.email || "Agent"}
              tone="teal"
            />
            <div>
              <strong>{agent.displayName}</strong>
              <span>{agent.email || identifier}</span>
            </div>
          </div>
        ) : (
          <>
            <div className="segmented" role="group" aria-label="Find agent by">
              <button
                type="button"
                aria-pressed={method === "email"}
                onClick={() => {
                  setMethod("email");
                  setIdentifier("");
                }}
              >
                Email address
              </button>
              <button
                type="button"
                aria-pressed={method === "agentReference"}
                onClick={() => {
                  setMethod("agentReference");
                  setIdentifier("");
                }}
              >
                Agent reference
              </button>
            </div>
            <label>
              {method === "email" ? "Verified email" : "Agent reference"}
              <input
                type={method === "email" ? "email" : "text"}
                required
                value={identifier}
                onChange={(event) => setIdentifier(event.target.value)}
                autoComplete="off"
                placeholder={
                  method === "email"
                    ? "agent@company.com"
                    : "Exact registered reference"
                }
              />
            </label>
          </>
        )}
        <fieldset className="permission-picker">
          <legend>Permissions</legend>
          <label>
            <input
              type="checkbox"
              checked={support}
              onChange={(event) => setSupport(event.target.checked)}
            />
            <Headphones size={17} />
            <span>
              <strong>Support</strong>
              <small>Conversations, claims, and human replies</small>
            </span>
          </label>
          <label>
            <input
              type="checkbox"
              checked={reviewer}
              onChange={(event) => setReviewer(event.target.checked)}
            />
            <CheckCheck size={17} />
            <span>
              <strong>Action reviewer</strong>
              <small>Approve or reject governed action proposals</small>
            </span>
          </label>
        </fieldset>
        {error && <ErrorNotice message={error} />}
      </form>
    </Modal>
  );
}
