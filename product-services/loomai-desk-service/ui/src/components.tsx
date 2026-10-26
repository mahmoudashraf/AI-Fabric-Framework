import {
  useEffect,
  useId,
  useRef,
  type ButtonHTMLAttributes,
  type ReactNode,
} from "react";
import {
  AlertCircle,
  ArrowLeft,
  Check,
  Inbox,
  LoaderCircle,
  X,
  type LucideIcon,
} from "lucide-react";

export function IconButton({
  icon: Icon,
  label,
  className = "",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  icon: LucideIcon;
  label: string;
}) {
  return (
    <button
      type="button"
      className={`icon-button ${className}`}
      aria-label={label}
      title={label}
      {...props}
    >
      <Icon size={18} strokeWidth={1.7} />
    </button>
  );
}
export function Status({ value }: { value: string }) {
  const tone = [
    "ACTIVE",
    "APPROVED",
    "CORRECTED",
    "COMPLETED",
    "ENABLED",
  ].includes(value)
    ? "teal"
    : [
          "WAITING",
          "PENDING",
          "CLAIM_PENDING",
          "WAITING_FOR_REVIEW",
          "WAITING_FOR_INFORMATION",
          "DECIDING",
        ].includes(value)
      ? "amber"
      : ["FAILED", "REJECTED", "EXPIRED"].includes(value)
        ? "coral"
        : "neutral";
  return (
    <span className={`status ${tone}`}>
      <span className="status-dot" />
      {humanize(value)}
    </span>
  );
}
export const humanize = (value: string) =>
  value
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/^./, (c) => c.toUpperCase());
export function initials(name = "") {
  return (
    name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0])
      .join("")
      .toUpperCase() || "?"
  );
}
export function Avatar({
  name,
  small = false,
  tone = "neutral",
}: {
  name: string;
  small?: boolean;
  tone?: string;
}) {
  return (
    <span
      aria-hidden="true"
      className={`avatar ${small ? "small" : ""} ${tone}`}
    >
      {initials(name)}
    </span>
  );
}
export function dateTime(value?: string) {
  return value
    ? new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(value))
    : "Not supplied";
}
export function relativeTime(value: string) {
  const minutes = Math.max(
    0,
    Math.floor((Date.now() - new Date(value).getTime()) / 60000),
  );
  return minutes < 1
    ? "Now"
    : minutes < 60
      ? `${minutes}m`
      : minutes < 1440
        ? `${Math.floor(minutes / 60)}h`
        : `${Math.floor(minutes / 1440)}d`;
}
export function ErrorNotice({
  message,
  retry,
  compact = false,
}: {
  message: string;
  retry?: () => void;
  compact?: boolean;
}) {
  return (
    <div role="alert" className={`notice error ${compact ? "compact" : ""}`}>
      <AlertCircle size={17} />
      <span>{message}</span>
      {retry && (
        <button type="button" className="text-button" onClick={retry}>
          Retry
        </button>
      )}
    </div>
  );
}
export function Empty({
  title,
  text,
  icon: Icon = Inbox,
  children,
}: {
  title: string;
  text: string;
  icon?: LucideIcon;
  children?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <span className="empty-icon">
        <Icon size={28} strokeWidth={1.4} />
      </span>
      <h2>{title}</h2>
      <p>{text}</p>
      {children}
    </div>
  );
}
export function Loading({ label = "Loading workspace" }: { label?: string }) {
  return (
    <div className="loading" role="status">
      <LoaderCircle size={20} className="spin" />
      <span>{label}</span>
    </div>
  );
}
export function Modal({
  title,
  onClose,
  children,
  footer,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    dialog.current?.showModal();
    return () => dialog.current?.close();
  }, []);
  return (
    <dialog
      aria-labelledby={titleId}
      ref={dialog}
      className="dialog"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <header>
        <h2 id={titleId}>{title}</h2>
        <IconButton icon={X} label="Close dialog" onClick={onClose} />
      </header>
      <div className="dialog-content">{children}</div>
      {footer && <footer>{footer}</footer>}
    </dialog>
  );
}
export function MobileBack({ onClick }: { onClick: () => void }) {
  return (
    <IconButton
      className="mobile-back"
      icon={ArrowLeft}
      label="Back to queue"
      onClick={onClick}
    />
  );
}
export function Notice({ children }: { children: ReactNode }) {
  return (
    <div className="notice success" role="status">
      <Check size={17} />
      <span>{children}</span>
    </div>
  );
}

// Structured, safe text only: no HTML or executable links from provider payloads.
export function DataFields({
  value,
  depth = 0,
}: {
  value: unknown;
  depth?: number;
}) {
  if (value === null || value === undefined)
    return <span className="muted">Not supplied</span>;
  if (typeof value !== "object")
    return (
      <span className="field-value">
        {typeof value === "boolean" ? (value ? "Yes" : "No") : String(value)}
      </span>
    );
  if (depth > 5) return <span className="muted">Nested content omitted</span>;
  const entries = Array.isArray(value)
    ? value.map((item, i) => [String(i + 1), item] as const)
    : Object.entries(value);
  return (
    <dl className={`data-fields ${depth ? "nested" : ""}`}>
      {entries.map(([key, item]) => (
        <div key={key}>
          <dt>
            {key.replaceAll("_", " ").replace(/([a-z])([A-Z])/g, "$1 $2")}
          </dt>
          <dd>
            <DataFields value={item} depth={depth + 1} />
          </dd>
        </div>
      ))}
    </dl>
  );
}
