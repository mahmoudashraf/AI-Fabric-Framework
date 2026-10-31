import { useRef, useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ImagePlus, KeyRound, Save, Trash2 } from "lucide-react";
import { api, errorMessage } from "./api";
import { Avatar, ErrorNotice, Notice } from "./components";
import { identityPortalLink } from "./identityPortal";
import { prepareProfilePhoto } from "./profilePhoto";
import type { DeskConfig, Session } from "./types";

export function Account({
  config,
  session,
}: {
  config: DeskConfig;
  session: Session;
}) {
  const client = useQueryClient();
  const operator = session.operator;
  const [name, setName] = useState(operator?.displayName ?? "");
  const [photo, setPhoto] = useState<string | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const recovery = identityPortalLink(config, "recovery");
  const reference = session.operator?.agentReference ?? session.agentReference;
  const preview = photo === undefined ? operator?.avatarUrl : photo;
  const dirty = name !== operator?.displayName || photo !== undefined;
  async function choosePhoto(file?: File) {
    if (!file) return;
    setProcessing(true); setError(""); setSaved(false);
    try { setPhoto(await prepareProfilePhoto(file)); }
    catch (error) { setError(errorMessage(error)); }
    finally { setProcessing(false); if (input.current) input.current.value = ""; }
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (!operator || busy || processing) return;
    setBusy(true); setError(""); setSaved(false);
    try {
      const updated = await api<NonNullable<Session["operator"]>>("/api/desk/v1/profile", {
        method: "PUT",
        body: JSON.stringify({ displayName: name.trim(),
          ...(photo === null ? { removePhoto: true } : photo ? { photoDataUrl: photo } : {}) }),
      });
      client.setQueryData<Session>(["session"], current => current ? { ...current, operator: updated } : current);
      setName(updated.displayName); setPhoto(undefined); setSaved(true);
      void client.invalidateQueries({ queryKey: ["session"] });
      void client.invalidateQueries({ queryKey: ["binding"] });
    } catch (error) { setError(errorMessage(error)); }
    finally { setBusy(false); }
  }
  return (
    <>
      {operator && <form className="agent-profile-form" onSubmit={save}>
        <div className="profile-photo-row">
          <div className="profile-photo-preview" aria-label="Profile photo preview">
            <Avatar name={name || "Agent"} src={preview} tone="teal" />
          </div>
          <div>
            <div className="profile-photo-actions">
              <button type="button" className="button secondary" disabled={busy || processing} onClick={() => input.current?.click()}>
                <ImagePlus size={16} />{processing ? "Processing..." : "Upload photo"}
              </button>
              {preview && <button type="button" className="icon-button" aria-label="Remove photo" title="Remove photo" disabled={busy || processing} onClick={() => { setPhoto(null); setSaved(false); }}>
                <Trash2 size={17} />
              </button>}
            </div>
            <p className="caption">JPG, PNG or WebP. Up to 5 MB.</p>
          </div>
          <input ref={input} type="file" hidden aria-label="Agent photo" accept="image/jpeg,image/png,image/webp" disabled={busy || processing} onChange={event => { void choosePhoto(event.target.files?.[0]); }} />
        </div>
        <label>Display name
          <input required maxLength={160} autoComplete="nickname" value={name} disabled={busy} onChange={event => { setName(event.target.value); setSaved(false); }} />
        </label>
        {error && <ErrorNotice message={error} />}
        {saved && <Notice>Profile saved.</Notice>}
        <button className="button primary" disabled={busy || processing || !dirty || !name.trim()}>
          <Save size={16} />{busy ? "Saving..." : "Save profile"}
        </button>
      </form>}
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
