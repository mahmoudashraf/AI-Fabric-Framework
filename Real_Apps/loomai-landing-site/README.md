# LoomAI Landing Site

Claim-safe product-suite landing package for:

- `loomai.pro` - LoomAI products page for Companion, Thinker, Resolver, and the product platform.
- `partners.loomai.pro` - partner application page focused on helping clients launch LoomAI products.

The service is intentionally self-contained and does not expose Platform, Coolify, deployment IDs, or secret internals. Host-aware routing serves the partner page at `/` when the host starts with `partners.`, and also at `/partners`.

## Run

```bash
npm --prefix Real_Apps/loomai-landing-site run start
```

Default URL: `http://127.0.0.1:4177`

## Verify

```bash
npm --prefix Real_Apps/loomai-landing-site run smoke
```

## Creative Redesign Handoff

Use [`docs/CLAUDE_REDESIGN_HANDOFF.md`](docs/CLAUDE_REDESIGN_HANDOFF.md) when handing this package to Claude or another design assistant. It describes the merchant/product landing page, partner landing page, functional contracts, form bindings, routes, claim rules, and verification commands that must remain intact during a redesign.

## Runtime Configuration

All values are optional and public unless explicitly marked as server-side only.

| Variable | Purpose |
| --- | --- |
| `PORT` | HTTP port. Defaults to `4177`. |
| `LOOMAI_DEMO_URL` | Merchant demo CTA target. |
| `LOOMAI_PRIVATE_INSTALL_URL` | Private/design-partner install CTA target. |
| `LOOMAI_PARTNER_SIGN_IN_URL` | Partner sign-in CTA target. |
| `LOOMAI_DOCS_URL` | Docs link target. |
| `LOOMAI_STATUS_URL` | Status link target. |
| `LOOMAI_LEADS_FILE` | Server-side JSONL lead sink. Defaults to `/tmp/loomai-landing-leads.jsonl`; use `disabled` to disable local persistence. |
| `LOOMAI_LEAD_WEBHOOK_URL` | Server-side optional webhook for lead forwarding. |
| `LOOMAI_LEAD_WEBHOOK_TOKEN` | Server-side optional bearer token for the webhook. Never exposed to the browser. |

## Deployment

Build from the repository root:

```bash
docker build -f Real_Apps/loomai-landing-site/Dockerfile -t loomai-landing-site .
```

For production lead capture, configure either a mounted persistent `LOOMAI_LEADS_FILE` path or a Platform-owned `LOOMAI_LEAD_WEBHOOK_URL`. Do not place third-party provider credentials in browser runtime config.

Current staging deployment:

- Merchant page: `https://loomai-landing.46.224.145.148.sslip.io/`
- Partner page: `https://partners.loomai-landing.46.224.145.148.sslip.io/`
