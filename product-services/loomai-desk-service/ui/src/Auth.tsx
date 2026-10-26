import {
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { createClient } from "@supabase/supabase-js";
import {
  ArrowRight,
  Check,
  Headphones,
  MailCheck,
  ShieldCheck,
  UserPlus,
} from "lucide-react";
import { Brand } from "./App";
import { errorMessage, post } from "./api";
import { ErrorNotice, Loading, Notice } from "./components";
import type { DeskConfig, Session } from "./types";

export function Auth({
  config,
  expired,
  themeButton,
  emailReturn,
}: {
  config: DeskConfig;
  expired: boolean;
  themeButton: ReactNode;
  emailReturn: boolean;
}) {
  const [signup, setSignup] = useState(false);
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const canSignup =
    config.registrationEnabled !== false &&
    !!config.supabaseUrl &&
    !!config.supabasePublishableKey;
  const authEntry =
    config.loginUrl?.startsWith("/") && !config.loginUrl.startsWith("//")
      ? config.loginUrl
      : "/oauth2/authorization/supabase";
  const bindingId = new URLSearchParams(location.search).get("bindingId");
  const authTarget = new URL(authEntry, location.origin);
  if (bindingId) authTarget.searchParams.set("bindingId", bindingId);
  const authUrl = `${authTarget.pathname}${authTarget.search}`;
  const cancelled =
    new URLSearchParams(location.search).get("login") === "cancelled";
  const autoLogin = !!bindingId && config.oidcEnabled !== false && !cancelled;
  const redirected = useRef(false);
  useEffect(() => {
    if (autoLogin && !redirected.current) {
      redirected.current = true;
      location.replace(authUrl);
    }
  }, [autoLogin, authUrl]);
  async function register(event: FormEvent) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const supabase = createClient(
        config.supabaseUrl!,
        config.supabasePublishableKey!,
        {
          auth: {
            persistSession: false,
            autoRefreshToken: false,
            detectSessionInUrl: false,
          },
        },
      );
      const { error } = await supabase.auth.signUp({
        email: email.trim(),
        password,
        options: {
          emailRedirectTo: `${window.location.origin}/`,
          data: { display_name: name.trim() },
        },
      });
      if (error) throw error;
      setPassword("");
      setSent(true);
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  if (autoLogin)
    return (
      <div className="boot">
        <Brand />
        <Loading label="Connecting your LoomAI account" />
      </div>
    );
  return (
    <div className="auth-page">
      <header>
        <Brand />
        {themeButton}
      </header>
      <main className="auth-layout">
        <section className="auth-intro">
          <span className="eyebrow">THE HUMAN SIDE OF AI</span>
          <h1>
            Good conversations
            <br />
            deserve a great handoff.
          </h1>
          <p>
            Your people, conversations, and action reviews.
            <br />
            One thoughtful workspace.
          </p>
          <div className="auth-principles">
            <span>
              <Headphones size={19} /> Human support
            </span>
            <span>
              <ShieldCheck size={19} /> Considered decisions
            </span>
          </div>
          <span className="auth-intro-footer">LoomAI Desk</span>
        </section>
        <section className="auth-form-wrap">
          <div className="auth-form">
            {sent ? (
              <>
                <span className="auth-symbol">
                  <MailCheck size={27} />
                </span>
                <h2>Check your inbox</h2>
                <p>
                  Follow the verification email sent to <strong>{email}</strong>
                  , then sign in to finish your agent profile.
                </p>
                <a className="button primary full" href={authUrl}>
                  Continue to sign in <ArrowRight size={17} />
                </a>
                <p className="caption">
                  Registration does not grant access to customer workspaces. An
                  owner assigns you after your account is verified.
                </p>
              </>
            ) : signup ? (
              <>
                <span className="eyebrow">JOIN YOUR TEAM</span>
                <h2>Create your Desk account</h2>
                <p>
                  Already registered?{" "}
                  <button
                    className="text-button"
                    onClick={() => {
                      setSignup(false);
                      setError("");
                    }}
                  >
                    Sign in
                  </button>
                </p>
                <form onSubmit={register}>
                  <label>
                    Full name
                    <input
                      autoComplete="name"
                      value={name}
                      onChange={(event) => setName(event.target.value)}
                      required
                      maxLength={120}
                    />
                  </label>
                  <label>
                    Email
                    <input
                      type="email"
                      autoComplete="email"
                      value={email}
                      onChange={(event) => setEmail(event.target.value)}
                      required
                    />
                  </label>
                  <label>
                    Password
                    <input
                      type="password"
                      autoComplete="new-password"
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                      required
                      minLength={8}
                      maxLength={256}
                    />
                  </label>
                  {error && <ErrorNotice message={error} />}
                  <button className="button primary full" disabled={busy}>
                    {busy ? "Creating account..." : "Create account"}
                    <UserPlus size={17} />
                  </button>
                </form>
                <p className="caption">
                  Verify your email before a workspace owner assigns you. Your
                  account uses LoomAI's shared identity provider.
                </p>
              </>
            ) : (
              <>
                <span className="auth-symbol">
                  <Headphones size={27} />
                </span>
                <span className="eyebrow">WELCOME TO YOUR DESK</span>
                <h2>
                  {emailReturn
                    ? "Continue your sign-in"
                    : "A little more human."}
                </h2>
                <p>
                  {emailReturn
                    ? "Your email link has returned to Desk. Complete secure sign-in to verify your identity and workspace access."
                    : "Sign in with your LoomAI account to pick up where your AI left off."}
                </p>
                {expired && (
                  <ErrorNotice message="Your session has expired. Sign in again to continue." />
                )}
                {cancelled && (
                  <ErrorNotice message="Sign-in was cancelled or could not be completed. You can try again." />
                )}
                {config.oidcEnabled === false ? (
                  <ErrorNotice message="Interactive sign-in is not configured for this Desk. Contact the service administrator." />
                ) : (
                  <a className="button primary full" href={authUrl}>
                    Sign in to LoomAI Desk
                    <ArrowRight size={17} />
                  </a>
                )}
                {canSignup && (
                  <div className="auth-signup">
                    New support agent?{" "}
                    <button
                      className="text-button"
                      onClick={() => setSignup(true)}
                    >
                      Create an account
                    </button>
                  </div>
                )}
                <div className="auth-trust">
                  <ShieldCheck size={16} />
                  <span>One identity. Only your assigned workspaces.</span>
                </div>
              </>
            )}
          </div>
        </section>
      </main>
      <footer className="auth-footer">
        Loom AI Labs <span>Thoughtfully connected.</span>
      </footer>
    </div>
  );
}

export function Unassigned({
  session,
  themeButton,
  onLogout,
  logoutError,
}: {
  session: Session;
  themeButton: ReactNode;
  onLogout: () => void;
  logoutError: string;
}) {
  const client = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [registered, setRegistered] = useState(
    session.registered ?? !!session.operator,
  );
  const [reference, setReference] = useState(
    session.operator?.agentReference ?? session.agentReference ?? "",
  );
  async function register(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const result = await post<{ agentReference?: string }>(
        "/api/desk/v1/agents/register",
        {},
      );
      setReference(result.agentReference ?? "");
      setRegistered(true);
      void client.invalidateQueries({ queryKey: ["session"] });
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-page">
      <header>
        <Brand />
        {themeButton}
      </header>
      <main className="unassigned">
        <span className="auth-symbol">
          <UserPlus size={28} />
        </span>
        <span className="eyebrow">YOUR DESK PROFILE</span>
        <h1>
          {registered ? "You're on the team. Almost." : "Make yourself known."}
        </h1>
        <p>
          {registered
            ? "Your profile is ready. A workspace owner can now assign you to a deployment using your verified email or agent reference."
            : "Finish your agent profile before a workspace owner assigns your support or review access."}
        </p>
        {session.emailVerified === false ? (
          <ErrorNotice message="Verify your email with the identity provider, then sign in again to finish registration." />
        ) : registered ? (
          <>
            <Notice>Registered as {session.operator?.email}</Notice>
            {reference && (
              <div className="agent-reference">
                <span>Agent reference</span>
                <code>{reference}</code>
              </div>
            )}
            <button
              className="button secondary"
              onClick={() =>
                void client.invalidateQueries({ queryKey: ["session"] })
              }
            >
              <Check size={16} />
              Check for assignments
            </button>
          </>
        ) : (
          <form onSubmit={register}>
            {session.operator?.email && (
              <p className="caption">
                Your verified identity: {session.operator.email}
              </p>
            )}
            <button className="button primary" disabled={busy}>
              {busy ? "Registering..." : "Finish registration"}
              <ArrowRight size={16} />
            </button>
          </form>
        )}
        {error && <ErrorNotice message={error} />}
        {logoutError && <ErrorNotice message={logoutError} />}
        <button className="text-button muted" onClick={onLogout}>
          Sign out
        </button>
      </main>
    </div>
  );
}
