import type { DeskConfig } from "./types";

export type IdentityMethod = "email" | "recovery" | "verify" | "register";

export function identityPortalLink(
  config: DeskConfig,
  method: IdentityMethod,
): string | undefined {
  if (!config.identityPortalUrl) return undefined;
  try {
    const url = new URL(config.identityPortalUrl);
    const secure =
      url.protocol === "https:" ||
      (url.protocol === "http:" &&
        ["localhost", "127.0.0.1"].includes(url.hostname));
    if (!secure || url.username || url.password || url.search || url.hash)
      return undefined;
    // Only the server-configured portal and a fixed method are navigable.
    url.searchParams.set("method", method);
    return url.href;
  } catch {
    return undefined;
  }
}
