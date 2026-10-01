import type {
  MaxModeCurrentPageAttachmentConfig,
  MaxModeCurrentPageContent,
  MaxModeHostAttachment,
} from "@/config";

export const CURRENT_PAGE_ATTACHMENT_TYPE = "current-page";
export const DEFAULT_CURRENT_PAGE_MAX_CHARS = 1800;
export const MAX_CURRENT_PAGE_MAX_CHARS = 20000;

const DEFAULT_EXCLUDE_SELECTORS = [
  "script",
  "style",
  "noscript",
  "template",
  "nav",
  "footer",
  "form",
  "[hidden]",
  "[aria-hidden='true']",
  "[data-loomai-ignore]",
  "#max-mode-widget-shadow-host",
];

export interface CurrentPageAttachmentData {
  id: string;
  title: string;
  content: string;
  url?: string;
  metadata: {
    attachmentKind: "CURRENT_PAGE";
    pageTitle: string;
    capturedAt: string;
    capturedCharacters: number;
    originalCharacters: number;
    truncated: boolean;
  };
  /** Used only by the widget to invalidate stale page context after navigation. */
  _locationFingerprint: string;
}

export function resolveCurrentPageMaxChars(config?: MaxModeCurrentPageAttachmentConfig): number {
  const configured = Number(config?.maxChars);
  if (!Number.isFinite(configured) || configured <= 0) {
    return DEFAULT_CURRENT_PAGE_MAX_CHARS;
  }
  return Math.min(Math.max(Math.floor(configured), 1), MAX_CURRENT_PAGE_MAX_CHARS);
}

export function currentLocationFingerprint(): string {
  if (typeof window === "undefined") return "server";
  return stableHash(window.location.href);
}

export function isCurrentPageAttachment(item: MaxModeHostAttachment | undefined | null): boolean {
  return item?.type === CURRENT_PAGE_ATTACHMENT_TYPE;
}

export async function captureCurrentPageAttachment(
  config?: MaxModeCurrentPageAttachmentConfig,
): Promise<MaxModeHostAttachment> {
  const maxChars = resolveCurrentPageMaxChars(config);
  const provided = await config?.contentProvider?.();
  const pageContent = provided ?? extractCurrentPageContent(config);
  const title = normalizeInlineText(pageContent.title) || "Current page";
  const normalizedText = normalizePageText(pageContent.text);

  if (!normalizedText) {
    throw new Error("No readable page text was found.");
  }

  const bounded = truncateAtWordBoundary(normalizedText, maxChars);
  const safeUrl = sanitizePageUrl(pageContent.url || defaultPageUrl());
  const locationFingerprint = currentLocationFingerprint();
  const data: CurrentPageAttachmentData = {
    id: `current-page-${stableHash(`${safeUrl || "page"}|${title}`)}`,
    title,
    content: bounded.text,
    ...(safeUrl ? { url: safeUrl } : {}),
    metadata: {
      attachmentKind: "CURRENT_PAGE",
      pageTitle: title,
      capturedAt: new Date().toISOString(),
      capturedCharacters: bounded.text.length,
      originalCharacters: normalizedText.length,
      truncated: bounded.truncated,
    },
    _locationFingerprint: locationFingerprint,
  };

  return { type: CURRENT_PAGE_ATTACHMENT_TYPE, data };
}

function extractCurrentPageContent(config?: MaxModeCurrentPageAttachmentConfig): MaxModeCurrentPageContent {
  if (typeof document === "undefined") {
    throw new Error("Current-page capture is only available in a browser.");
  }

  const root = resolveContentRoot(config?.rootSelector);
  const selectors = [...DEFAULT_EXCLUDE_SELECTORS, ...(config?.excludeSelectors || [])]
    .map((selector) => selector.trim())
    .filter(Boolean);
  const text = extractVisibleText(root, selectors);
  const heading = document.querySelector("h1")?.textContent;

  return {
    title: document.title || heading || "Current page",
    text,
    url: defaultPageUrl(),
  };
}

function resolveContentRoot(rootSelector?: string): Element {
  if (rootSelector?.trim()) {
    try {
      const configuredRoot = document.querySelector(rootSelector.trim());
      if (configuredRoot) return configuredRoot;
    } catch {
      console.warn(`[MaxMode] Ignoring invalid currentPageAttachment.rootSelector: ${rootSelector}`);
    }
  }

  return document.querySelector("main, [role='main'], article") || document.body || document.documentElement;
}

function extractVisibleText(root: Element, excludedSelectors: string[]): string {
  const chunks: string[] = [];
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      const parent = node.parentElement;
      if (!parent || !normalizeInlineText(node.textContent)) {
        return NodeFilter.FILTER_REJECT;
      }
      if (isExcluded(parent, excludedSelectors) || isVisuallyHidden(parent)) {
        return NodeFilter.FILTER_REJECT;
      }
      return NodeFilter.FILTER_ACCEPT;
    },
  });

  let node = walker.nextNode();
  while (node) {
    const value = normalizeInlineText(node.textContent);
    if (value) chunks.push(value);
    node = walker.nextNode();
  }
  return chunks.join(" ");
}

function isExcluded(element: Element, selectors: string[]): boolean {
  return selectors.some((selector) => {
    try {
      return Boolean(element.closest(selector));
    } catch {
      console.warn(`[MaxMode] Ignoring invalid current-page exclusion selector: ${selector}`);
      return false;
    }
  });
}

function isVisuallyHidden(element: Element): boolean {
  if (typeof window === "undefined" || typeof window.getComputedStyle !== "function") return false;
  const style = window.getComputedStyle(element);
  return style.display === "none" || style.visibility === "hidden";
}

function defaultPageUrl(): string | undefined {
  if (typeof document === "undefined" || typeof window === "undefined") return undefined;
  const canonical = document.querySelector<HTMLLinkElement>("link[rel='canonical']")?.href;
  return canonical || window.location.href;
}

function sanitizePageUrl(value?: string): string | undefined {
  if (!value || typeof window === "undefined") return undefined;
  try {
    const parsed = new URL(value, window.location.href);
    parsed.username = "";
    parsed.password = "";
    parsed.search = "";
    parsed.hash = "";
    return parsed.toString();
  } catch {
    return undefined;
  }
}

function normalizePageText(value: unknown): string {
  if (typeof value !== "string") return "";
  return value
    .replace(/\r\n?/g, "\n")
    .split("\n")
    .map((line) => normalizeInlineText(line))
    .filter(Boolean)
    .join("\n");
}

function normalizeInlineText(value: unknown): string {
  return typeof value === "string" ? value.replace(/\s+/g, " ").trim() : "";
}

function truncateAtWordBoundary(text: string, maxChars: number): { text: string; truncated: boolean } {
  if (text.length <= maxChars) return { text, truncated: false };
  const candidate = text.slice(0, maxChars + 1);
  const boundary = candidate.lastIndexOf(" ");
  const bounded = candidate.slice(0, boundary >= Math.floor(maxChars * 0.75) ? boundary : maxChars).trimEnd();
  return { text: bounded, truncated: true };
}

function stableHash(value: string): string {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(36);
}
