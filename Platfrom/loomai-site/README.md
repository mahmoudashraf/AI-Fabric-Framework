# Loom AI Labs Public Site

Static-first public portfolio for `loomai.pro`.

The site presents two open-source products with equal weight:

- AI Fabric Framework
- AI Fabric Chat UI

Live experiments are runnable engineering proof. Applied research records the
questions, implementation evidence, observations, and limitations behind that
work. Neither collection is presented as customer adoption or certification.

## Local Development

```bash
npm --prefix Platfrom/loomai-site install
npm --prefix Platfrom/loomai-site run dev
```

Local URL: `http://127.0.0.1:4321`

## Verification

```bash
npm --prefix Platfrom/loomai-site run verify
```

The browser smoke writes responsive screenshots to
`Platfrom/loomai-site/test-results/screenshots`.

## Container

Build from the repository root:

```bash
docker build \
  -f Platfrom/loomai-site/Dockerfile \
  -t loomai-public-site:local \
  .
```

The container listens on port `3000` and exposes `GET /health`.

## Coolify

- Build pack: Dockerfile
- Base directory: `/`
- Dockerfile location: `/Platfrom/loomai-site/Dockerfile`
- Exposed port: `3000`
- Health path: `/health`
- Branch: `Platform-V10`

No application secrets are required. `SOURCE_COMMIT`, when enabled by Coolify,
is surfaced through the health response.

Production resource:

- Project/environment: `loomai-platform` / `production`
- Application: `loomai-public-site`
- Application UUID: `t3r7unm08sh3tfatpadz7qky`
- Validation URL:
  `https://loomai-public-site.46.225.162.106.sslip.io`

The production Coolify webhook endpoint is intentionally behind the
control-plane firewall. GitHub push auto-deploy is therefore not part of the
current contract; use an explicit Coolify deployment after pushing a verified
site commit.

## Attach `loomai.pro`

Keep the sslip validation URL on the application while attaching the custom
domains.

1. In the `loomai-public-site` Coolify resource, set Domains to
   `https://loomai-public-site.46.225.162.106.sslip.io,https://loomai.pro,https://www.loomai.pro`.
2. Set the redirect preference to `non-www` so `www.loomai.pro` resolves to the
   canonical apex domain.
3. In Namecheap Advanced DNS, add `A` record `@` pointing to
   `46.225.162.106`.
4. Add `CNAME` record `www` pointing to `loomai.pro`.
5. Remove only conflicting parking, redirect, `A`, `AAAA`, or `CNAME` records
   for `@` and `www`. Leave `api`, `console`, `partners`, and
   `shopify-bridge` unchanged.
6. Wait for authoritative DNS to answer, then verify `/`, `/health`,
   `/sitemap.xml`, and one nested product route over HTTPS.

Do not add an apex `AAAA` record until the production proxy's IPv6 route has
been verified separately. Coolify provisions the Let's Encrypt certificate
after the records resolve to the production server; no application code change
or rebuild is required for the domain attachment.
