import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  CheckCheck,
  ChevronDown,
  CircleHelp,
  Headphones,
  Inbox,
  LogOut,
  Menu,
  Moon,
  PanelsTopLeft,
  RefreshCw,
  Sun,
  Users,
  UserRound,
  X,
} from "lucide-react";
import { api, configureCsrf, errorMessage, post } from "./api";
import { Auth, Unassigned } from "./Auth";
import { Account } from "./Account";
import {
  Avatar,
  Empty,
  ErrorNotice,
  IconButton,
  Loading,
  Modal,
} from "./components";
import { Conversations } from "./Conversations";
import { Reviews } from "./Reviews";
import { Agents } from "./Agents";
import type { Binding, DeskConfig, Session } from "./types";

type View = "conversations" | "reviews" | "agents";
function initialBinding() {
  return new URLSearchParams(location.search).get("bindingId") ?? "";
}
function initialTheme() {
  try {
    const value = localStorage.getItem("loomai-desk-theme");
    if (value === "dark" || value === "light") return value;
  } catch {
    /* Storage may be unavailable in a restricted browser. */
  }
  return matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function App({ emailReturn = false }: { emailReturn?: boolean }) {
  const client = useQueryClient();
  const [theme, setTheme] = useState(initialTheme);
  const [expired, setExpired] = useState(false);
  const [logoutError, setLogoutError] = useState("");
  const [loggingOut, setLoggingOut] = useState(false);
  const [bindingId, setBindingId] = useState(initialBinding);
  const [view, setView] = useState<View>("conversations");
  const [navOpen, setNavOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const config = useQuery({
    queryKey: ["config"],
    queryFn: async ({ signal }) => {
      return api<DeskConfig>("/api/desk/v1/config", { signal });
    },
  });
  const session = useQuery({
    queryKey: ["session"],
    queryFn: async ({ signal }) => {
      const value = await api<Session>("/api/desk/v1/session", { signal });
      configureCsrf(value);
      return value;
    },
    refetchInterval: 15_000,
    enabled: !expired,
  });
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem("loomai-desk-theme", theme);
    } catch {
      /* Theme still applies without persistence. */
    }
  }, [theme]);
  useEffect(() => {
    const endSession = () => {
      setExpired(true);
      client.removeQueries({
        predicate: (query) => query.queryKey[0] === "binding",
      });
      client.setQueryData(["session"], { authenticated: false, bindings: [] });
    };
    const accessChanged = () => {
      void client.invalidateQueries({ queryKey: ["session"] });
    };
    window.addEventListener("desk:session-expired", endSession);
    window.addEventListener("desk:access-changed", accessChanged);
    return () => {
      window.removeEventListener("desk:session-expired", endSession);
      window.removeEventListener("desk:access-changed", accessChanged);
    };
  }, [client]);
  useEffect(() => {
    const back = () =>
      setBindingId(new URLSearchParams(location.search).get("bindingId") ?? "");
    window.addEventListener("popstate", back);
    return () => window.removeEventListener("popstate", back);
  }, []);
  const bindings = session.data?.bindings ?? [];
  const effectiveId = bindingId || bindings[0]?.bindingId;
  const binding = bindings.find((item) => item.bindingId === effectiveId);
  const previousBinding = useRef<string>();
  useEffect(() => {
    const prior = previousBinding.current;
    if (prior && prior !== binding?.bindingId) {
      void client.cancelQueries({ queryKey: ["binding", prior] });
      client.removeQueries({ queryKey: ["binding", prior] });
    }
    previousBinding.current = binding?.bindingId;
  }, [binding?.bindingId, client]);
  useEffect(() => {
    document.title = binding
      ? `${binding.deploymentName} | LoomAI Desk`
      : "LoomAI Desk";
  }, [binding]);

  async function logout() {
    setLoggingOut(true);
    setLogoutError("");
    try {
      await post("/logout", {});
      client.clear();
      location.replace("/?login=signed-out");
    } catch (error) {
      setLogoutError(errorMessage(error));
      setLoggingOut(false);
    }
  }
  function selectBinding(id: string) {
    const old = effectiveId;
    if (old) {
      void client.cancelQueries({ queryKey: ["binding", old] });
      client.removeQueries({ queryKey: ["binding", old] });
    }
    setBindingId(id);
    setView("conversations");
    setNavOpen(false);
    const url = new URL(location.href);
    url.searchParams.set("bindingId", id);
    history.pushState({}, "", url);
  }
  const themeButton = (
    <IconButton
      icon={theme === "light" ? Moon : Sun}
      label={`Use ${theme === "light" ? "dark" : "light"} theme`}
      onClick={() => setTheme(theme === "light" ? "dark" : "light")}
    />
  );
  if (loggingOut)
    return (
      <div className="boot">
        <Brand />
        <Loading label="Signing out of your Desk" />
      </div>
    );
  if (session.isPending || config.isPending)
    return (
      <div className="boot">
        <Brand />
        <Loading label="Opening your Desk" />
      </div>
    );
  if ((session.isError && !session.data) || config.isError)
    return (
      <div className="boot">
        <Brand />
        <ErrorNotice
          message={errorMessage(session.error ?? config.error)}
          retry={() => {
            void session.refetch();
            void config.refetch();
          }}
        />
      </div>
    );
  if (!session.data?.authenticated)
    return (
      <Auth
        config={config.data!}
        expired={expired}
        themeButton={themeButton}
        emailReturn={emailReturn}
      />
    );
  if (!session.data.operator || !bindings.length)
    return (
      <Unassigned
        config={config.data!}
        session={session.data}
        themeButton={themeButton}
        onLogout={logout}
        logoutError={logoutError}
      />
    );
  const operator = session.data.operator;
  const titles = {
    conversations: "Conversations",
    reviews: "Action reviews",
    agents: "Agents & access",
  };
  return (
    <div className={`desk-shell ${navOpen ? "nav-is-open" : ""}`}>
      {navOpen && (
        <button
          className="nav-scrim"
          aria-label="Close navigation"
          onClick={() => setNavOpen(false)}
        />
      )}
      <aside className="workspace-rail">
        <div className="brand-row">
          <Brand />
          <IconButton
            className="mobile-only"
            icon={X}
            label="Close navigation"
            onClick={() => setNavOpen(false)}
          />
        </div>
        <div className="workspace-select">
          <label htmlFor="workspace">Workspace</label>
          <div className="select-wrap">
            <select
              id="workspace"
              value={binding?.bindingId ?? ""}
              onChange={(event) => selectBinding(event.target.value)}
            >
              {!binding && <option value="">Select workspace</option>}
              {bindings.map((item) => (
                <option key={item.bindingId} value={item.bindingId}>
                  {item.deploymentName} / {item.environment}
                </option>
              ))}
            </select>
            <ChevronDown size={14} />
          </div>
        </div>
        <span className="nav-section-label">YOUR DESK</span>
        <nav aria-label="Workspace navigation">
          <button
            className={
              view === "conversations" ? "nav-link active" : "nav-link"
            }
            onClick={() => {
              setView("conversations");
              setNavOpen(false);
            }}
          >
            <Inbox size={18} />
            <span>Conversations</span>
          </button>
          <button
            className={view === "reviews" ? "nav-link active" : "nav-link"}
            onClick={() => {
              setView("reviews");
              setNavOpen(false);
            }}
          >
            <CheckCheck size={18} />
            <span>Action reviews</span>
          </button>
          {binding?.roles.includes("OWNER") && (
            <button
              className={view === "agents" ? "nav-link active" : "nav-link"}
              onClick={() => {
                setView("agents");
                setNavOpen(false);
              }}
            >
              <Users size={18} />
              <span>Agents & access</span>
            </button>
          )}
        </nav>
        <div className="rail-bottom">
          <div className="workspace-footnote">
            <Headphones size={16} />
            <span>
              Human support.
              <br />
              <strong>Connected to your AI.</strong>
            </span>
          </div>
          <div className="identity-row">
            <Avatar small name={operator.displayName} tone="teal" />
            <div>
              <strong title={operator.displayName}>
                {operator.displayName}
              </strong>
              <span>
                {binding?.roles.map((role) => role.toLowerCase()).join(" / ") ??
                  "Registered agent"}
              </span>
            </div>
          </div>
          <div className="rail-tools">
            {themeButton}
            <IconButton
              icon={UserRound}
              label="Profile"
              onClick={() => setProfileOpen(true)}
            />
            <a
              className="icon-button"
              title="Desk help"
              aria-label="Desk help"
              href="#desk-help"
              onClick={(event) => {
                event.preventDefault();
                setHelpOpen(true);
              }}
            >
              <CircleHelp size={18} />
            </a>
            <span className="rail-tools-spacer" />
            <IconButton
              icon={LogOut}
              label={loggingOut ? "Signing out" : "Sign out"}
              disabled={loggingOut}
              onClick={logout}
            />
          </div>
        </div>
      </aside>
      <main className="workspace-main">
        <header className="topbar">
          <div className="topbar-title">
            <IconButton
              className="mobile-only"
              icon={Menu}
              label="Open navigation"
              onClick={() => setNavOpen(true)}
            />
            <span className="topbar-deployment">
              {binding?.deploymentName ?? "Workspace"}
            </span>
            <span className="slash">/</span>
            <h1>{titles[view]}</h1>
            {binding && (
              <span
                className={`environment ${binding.environment.toLowerCase()}`}
              >
                {binding.environment}
              </span>
            )}
          </div>
          <div className="topbar-tools">
            <span className="poll-label">
              <span />
              Live updates
            </span>
            <IconButton
              icon={RefreshCw}
              label="Refresh workspace"
              onClick={() => {
                if (binding)
                  void client.invalidateQueries({
                    queryKey: ["binding", binding.bindingId],
                  });
                void session.refetch();
              }}
            />
          </div>
        </header>
        {logoutError && <ErrorNotice compact message={logoutError} />}
        {session.isRefetchError && (
          <ErrorNotice
            compact
            message="Workspace permissions could not be refreshed. Reconnect before continuing."
            retry={() => void session.refetch()}
          />
        )}
        {!binding ? (
          <Empty
            icon={PanelsTopLeft}
            title="Workspace unavailable"
            text="This workspace does not exist or is not assigned to you. Choose an authorized workspace from the menu."
          />
        ) : (
          <BindingWorkspace
            key={`${binding.bindingId}:${binding.roles.join(",")}`}
            binding={binding}
            view={view}
            operatorId={operator.id}
            accessFresh={!session.isRefetchError}
          />
        )}
      </main>
      {helpOpen && (
        <Modal
          title="Your human support workspace"
          onClose={() => setHelpOpen(false)}
        >
          <p>
            Conversations contains visitors transferred to your support team.
            Claim a conversation to reply, then explicitly return it to AI or
            close it.
          </p>
          <p>
            Action reviews have separate reviewer permissions. A conversation
            reply never approves an action.
          </p>
          <p>
            Access is shared by apps on the selected deployment and environment.
          </p>
        </Modal>
      )}
      {profileOpen && (
        <Modal title="Your account" onClose={() => setProfileOpen(false)}>
          <Account config={config.data!} session={session.data} />
        </Modal>
      )}
    </div>
  );
}

function BindingWorkspace({
  binding,
  view,
  operatorId,
  accessFresh,
}: {
  binding: Binding;
  view: View;
  operatorId: string;
  accessFresh: boolean;
}) {
  if (view === "reviews")
    return <Reviews binding={binding} accessFresh={accessFresh} />;
  if (view === "agents")
    return <Agents binding={binding} accessFresh={accessFresh} />;
  return (
    <Conversations
      binding={binding}
      operatorId={operatorId}
      accessFresh={accessFresh}
    />
  );
}

export function Brand() {
  return (
    <div className="brand">
      <span className="brand-mark">
        <PanelsTopLeft size={22} strokeWidth={1.7} />
      </span>
      <span>
        LoomAI <strong>Desk</strong>
        <i />
      </span>
    </div>
  );
}
