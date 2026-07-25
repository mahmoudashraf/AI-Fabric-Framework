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
