# Public Homepage Kit Cards

Source: `852bc5614ed0ee55919acf2fa4b0025fa271a47f`.
Serving-image CI: `42106691452`, success, only `loomai-site` published.
Production image-only rollout: finished, native image healthy.

Verified from the worktree, a clean source archive and the live public site:

- Astro check, production build, content graph and 28 static route checks.
- Homepage and `/experiences`, three cards and three correct Console detail links.
- Dealership offer isolation, Human Desk portraits and small companion icons.
- Nine preview formats on each catalogue route, Escape/close/focus behavior.
- Five viewport widths (320,390,768,1280,1536) in light/dark mode.
- Local photos/font, aligned desktop cards and mobile preview bounds.
- Existing public theme/navigation regression; light remains the default.
- Console detail destinations returned HTTP200. No AI or business action ran.
- Other production application image tags unchanged.

`local/` and `live/` contain public-only screenshots and browser geometry reports.
The native image qualification is authoritative for the new revision; the health
payload also retained an older build-commit field, recorded explicitly rather
than misreported as the source of the running image.

Reproduce the focused check from `Platfrom/loomai-site` with Node22:

```sh
node scripts/kit-cards-browser-smoke.mjs
SITE_ORIGIN=https://loomai.pro node scripts/kit-cards-browser-smoke.mjs
```

The runner is read-only apart from local browser theme storage and ordinary site
visit analytics. All previews are illustrative and submit no enquiries/tickets.
