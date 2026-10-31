import { KeyRound } from "lucide-react";
import { identityPortalLink } from "./identityPortal";
import type { DeskConfig, Session } from "./types";

export function Account({
  config,
  session,
}: {
  config: DeskConfig;
  session: Session;
}) {
  const recovery = identityPortalLink(config, "recovery");
  const reference = session.operator?.agentReference ?? session.agentReference;
  return (
    <>
      <dl className="account-details">
        <div>
          <dt>Email</dt>
          <dd>{session.operator?.email ?? "Not available"}</dd>
        </div>
        <div>
          <dt>Agent reference</dt>
          <dd>{reference ?? "Not available"}</dd>
        </div>
      </dl>
      {recovery ? (
        <a className="button secondary" href={recovery}>
          <KeyRound size={16} /> Change password
        </a>
      ) : (
        <p className="caption">
          Account management is not configured. Contact the service
          administrator.
        </p>
      )}
    </>
  );
}
