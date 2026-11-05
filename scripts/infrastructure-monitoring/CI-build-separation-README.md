# Serving Image Build Separation (010.60)

CI builds on GitHub-hosted runners; an opt-in host-local updater adopts prebuilt
images through loopback Coolify. Nothing here installs a service, changes a live
app, opens a port or supplies host credentials to Actions. No customer runtime,
Platform core, Desk, Engage, AI configuration, collector or framework release
is changed. Main must bind **only existing hosted source-built demos/supporting
apps**, after converting those approved apps to Docker Image in Coolify.

## Catalogue

`build-catalogue.json` lists five local apps with repository-root context:

- `loomai-site`: `Platfrom/loomai-site/Dockerfile`.
- `partner-ui`: `Platfrom/partner-ui/Dockerfile` (provider-neutral server).
- `external-vehicle-provider-simulator`: its `deploy/container/Dockerfile`.
- `mcp-execution-gateway`: service `deploy/container/Dockerfile`.
- `shopify-bridge`: service `deploy/container/Dockerfile`, including merchant UI.

Public `Loom-AI-Labs/ai-fabric-framework` main supplies fifteen apps, using
`examples/real-apps/<app-id>/Dockerfile`:

- `agentic-ai-action-resolver`
- `ai-fabric-account-resolver`
- `ai-fabric-live-data-sync`
- `behavior-churn-signals`
- `chat-capabilities-demo`
- `conversation-handover-demo`
- `deployment-knowledge-guard`
- `document-ingestion-workbench`
- `ecommerce-store`
- `incident-investigation-room`
- `mcp-operations-assistant`
- `mcp-operations-reference-server` (required live-demo companion)
- `privacy-first-customer-facing-support`
- `retrieval-connector-boundary-lab`
- `tenant-knowledge-portal`

Action resolver, deployment knowledge guard, incident room and MCP assistant use
the framework repository root. The other eleven use `examples/real-apps`.
Dockerfiles were verified at `inspectedSha`, without editing the sibling repo.
Catalogue membership permits **image publication**, not deployment or app creation.
Do not add bindings for missing, stopped or not-yet-hosted demos.

The simulator uses the distinct image `loomai-serving-vehicle-simulator`. The
existing dependency publisher retains `loomai-external-vehicle-provider-simulator`
and bare SHA tags, so it cannot overwrite this workflow's images/channels.
Its independent builds still consume CI resources; main can separately retire
that overlapping simulator trigger when its existing consumers have migrated.

## Publication

```text
ghcr.io/<publishing-owner>/<catalogue-image>:<source-sha>-<app-id>
ghcr.io/<publishing-owner>/<catalogue-image>:<app-id>-current
```

SHA tags are write-once by workflow convention; GHCR itself does not enforce
immutability. Restrict package writes and retain rollback digests. The mutable
channel is a carbon copy of the immutable manifest/index, verified by digest.
OCI labels are fixed at build time: `source` is the canonical GitHub source URL,
`revision` the source SHA, `ref.name` the expected branch and `title` the app ID.
Build arguments carry only source metadata, never runtime configuration.

- Pushes on `Platform-V11` select affected local input paths, including deletions
  and renames. Root `.dockerignore` affects all five. Workflow/catalogue edits
  or unavailable push bases conservatively check all local images.
- Dispatch accepts one exact app ID or `all`. `framework_sha=main` requests
  current main. Any explicit full lowercase framework SHA is **immutable-only**,
  even if it happens to equal current main. It never advances a current channel.
- Both framework and local sources are polled every fifteen minutes (7/22/37/52 UTC). This
  repairs a source push replaced in GitHub's pending concurrency queue by a later,
  unrelated push; it checks missing image/channel identities, not forced rebuilds. The
  schedule uses the `Platform-V11` catalogue. GitHub requires this workflow on
  default `main` for schedule/dispatch availability; dispatch using `Platform-V11`.
  Other dispatch branches are deliberately refused.
- Source SHA is the latest commit touching declared inputs, not an unrelated
  scheduler commit. All framework apps conservatively share the entire examples
  reactor as input; root-context Dockerfiles also watch root `.dockerignore`.
- Manifest reads distinguish absence from authentication/network failure. Only
  explicit missing-manifest responses permit building. Existing immutable images
  are reused. A missing/stale channel alone creates a manifest-promotion job,
  **not a rebuild**. A second lookup protects the immutable tag before building.
- Channel promotion rechecks the requested source branch head. An advanced head
  leaves the channel unchanged; the next source poll or fresh local-app
  dispatch/push can repair it. Historical requests cannot promote channels.
- Global workflow concurrency plus `max-parallel: 2` bounds this workflow to two
  builds. GitHub can replace pending runs; this polls current source, not every
  historical commit. Existing publishers remain independently bounded.
- Recipe changes do not overwrite old tags. Images lacking required labels are
  rejected by the updater; publish a new actual Dockerfile/input source commit
  rather than rewriting an existing immutable tag.

```sh
gh workflow run publish-serving-app-images.yml --ref Platform-V11 -f app=partner-ui -f framework_sha=main
gh workflow run publish-serving-app-images.yml --ref Platform-V11 -f app=all -f framework_sha=main
gh workflow run publish-serving-app-images.yml --ref Platform-V11 -f app=conversation-handover-demo -f framework_sha=<full-sha>
```

## Credentials

The only Actions secret referenced is automatic `GITHUB_TOKEN`: planner
`contents: read`/`packages: read`, publisher `contents: read`/`packages: write`.
Grant the workflow repository package access where needed. Keep **all packages
private**, including public-source demo images. Check visibility/access after
first publication; public-source OCI labels are provenance, not permission.

The host uses its existing root Docker GHCR login (`/root/.docker`) and Coolify
registry credentials, with a dedicated `read:packages` classic PAT and package
read/SSO access as required. No GHCR password is put in this config or Actions.
The Coolify API token stays only in the root-owned `0600` local config. Use the
narrowest supported app/team permissions for read, image update and deployment.
No SSH keys, Coolify tokens, runtime secrets or host management ports enter CI.
Docker build records, summaries and check annotations are disabled; no config/log
artifact upload is added. Ordinary compiler output must not contain secrets.
See [GHCR authentication](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).

## Host Updater

The installed Coolify API's build-pack enum does not include `dockerimage` for
PATCH conversions. Initial source-to-image conversion is therefore a one-time
operator configuration change using Coolify's own Application model, with a
private before snapshot and native source/health verification. Do not modify the
Coolify API code or create replacement apps just to change build mode. Keep
existing UUIDs, domains, volumes, environment values, health checks and limits.
Disable the old source-push deployment trigger only when the image updater is
installed as its replacement; otherwise each push can deploy the previous image
before CI publishes the new one. The updater never performs this conversion.

File-backed H2/Lucene demos must use single-instance cold replacement. Enable
Coolify's consistent container-name setting; on the **first** conversion, stop
the old timestamp-named container explicitly by its exact owned application
label before starting the new one. Otherwise two JVMs can contend for the same
database/index file. Keep the original volume and image for rollback. Never
delete a database or disable file locking to make this work. Verify native
container health separately after a cold deployment finishes.

The fixed config path is `/etc/loomai/serving-images.json`, root-owned `0600`,
regular file, not a symlink. Its complete schema is:

```json
{
  "coolifyUrl": "http://127.0.0.1:8000",
  "apiKey": "<host-local-Coolify-token>",
  "apps": [
    {
      "appUuid": "<existing-approved-Coolify-UUID>",
      "image": "ghcr.io/mahmoudashraf/loomai-partner-ui",
      "channel": "partner-ui-current",
      "sourceRepository": "https://github.com/mahmoudashraf/AI-Fabric-Framework",
      "branch": "Platform-V11",
      "appId": "partner-ui"
    }
  ]
}
```

This is a schema example, not a deployable binding. Never commit a filled config.
Use canonical `https://github.com/Loom-AI-Labs/ai-fabric-framework` and branch
`main` for framework bindings. Images must have the exact catalogue basename;
the GHCR owner must be the actual publishing owner. Configured UUIDs, source,
branch, channel and app IDs are validated; no discovery or app creation occurs.

The updater performs these bounded actions, sequentially:

1. Require the existing UUID to be a running `dockerimage` application already
   configured for the exact approved image repository. Source builds, stopped
   apps, other repositories and unknown queue schemas are refused.
   Installed API responses can redact numeric `id`: after validating the app
   UUID and image binding, resolve it from that exact app's authenticated
   `GET /deployments/applications/<uuid>?skip=0&take=1` response. Exactly one
   latest job with a positive `application_id` is required; empty/invalid history
   fails closed. Identity must remain consistent across mutation readbacks.
   This adds no config field, command fallback or mutation permission.
2. Read the active Coolify deployment queue and skip a busy app. Queue reads,
   not truncated historical pages, establish whether that app is idle.
3. Fresh-pull its current channel using existing Docker login. Validate OCI
   source/revision/branch/app ID and an exact repository digest. Derive
   `<revision>-<app-id>`, pull it and require identical digest and image ID.
   Missing images, ambiguous digests, wrong labels or failed pulls stop the app;
   the host never builds or publishes an image to repair absence.
4. If the configured immutable tag is already current, do not redeploy. Otherwise
   re-read app/queue after pulling, durably record intent, PATCH **only**
   `docker_registry_image_name` and `docker_registry_image_tag`, verify readback,
   and request one pull-only deployment with `force=false`.
5. Persist the returned deployment UUID and check its app identity/status on
   later ticks. A finished queue job closes the receipt; failure/cancellation
   requires review, not an automatic retry. Live product qualification is still
   separate from a successful Coolify job.

Only these loopback API paths exist in the client:
`GET /applications/<uuid>`, `GET /deployments`, bounded
`GET /deployments/applications/<uuid>?skip=0&take=1` for redacted identity,
`GET /deployments/applications/<uuid>?skip=0&take=20` for uncertain-deploy review,
`GET /deployments/<uuid>`, image-only `PATCH /applications/<uuid>` and
`GET /deploy?uuid=<uuid>&force=false` (all under `/api/v1`). Redirects, proxies,
arbitrary URLs/paths, env/domain/settings writes, create/delete, force builds
and Docker run/build/exec are not supported. The installed Coolify version must
support that **GET** deploy contract; newer upstream uses POST. A 405 fails
closed with a receipt, never an invented method fallback. Verify compatibility
before activation; see [Coolify deployment API](https://coolify.io/docs/api/endpoints/deployments/deploy-by-tag-or-uuid).

Bounds: at most 25 explicitly configured apps, one pull at a time, two seconds
per entire HTTP exchange, 30 seconds per Docker registry command, one MiB response
bounds and a ten-minute total tick. A file lock prevents overlap. The five-minute
timer is low priority; its CPU/IO settings limit the worker, not the Docker daemon.

### Uncertain Outcomes

Root-private `/var/lib/loomai-serving-images/state.json` stores only bounded
image identity/mutation receipts, not tokens or runtime config. Atomic fsync'd
intent precedes PATCH/deploy, including crashes. Never erase receipts simply to
force a retry.

- Lost PATCH/readback: the next tick reads the app. Exact positive readback marks
  it ready; a later tick revalidates the current channel before requesting deploy.
  A nonmatching app/tag or changed channel holds for operator review. PATCH is
  never blindly replayed.
- Lost deploy response: later ticks read app/queue/history, but cannot infer a
  deployment UUID/target from absence of a busy job. They hold for operator
  review and never repeat the deploy request. Known UUIDs are read back directly.
- To resolve a held receipt, pause the timer, inspect native Coolify app/history
  and running image identity privately, complete or roll back the reviewed action,
  then reconcile only that app's local receipt after proving no in-flight action
  remains. Preserve other receipts. No automatic rollback/recovery platform exists.

Logs contain only catalogue app ID and fixed result codes, not HTTP/Docker
responses, credentials, environments or user configuration. Review-required
outcomes make the service fail visibly while retaining receipts for the next tick.

## Rollout (Main Owner Only)

1. Publish CI on `Platform-V11` and default `main`, run one-app dispatch, verify
   private package visibility, immutable/channel digest equality and OCI labels.
   Confirm root Docker and Coolify can pull that private image.
2. Record exact existing hosted UUID/environment/domain/source/image, volumes,
   ports, health checks, limits and runtime variables privately. Convert only
   that reviewed source-built app to Docker Image on the **same UUID**, using its
   immutable tag. Preserve all other configuration and qualify pull-only deploy.
   Keep source-build auto-deploy until the replacement canary is ready, then
   disable competing build/deploy triggers for migrated apps. Do not create or
   enable any missing demo just because CI publishes its image.
3. Install reviewed files on the host (commands below are **not executed here**).
   Create the root-private config through existing credential handling, not a
   command containing a token. Do not add runtime environments to it.

```sh
sudo install -d -o root -g root -m 0755 /usr/local/lib/loomai /etc/loomai
sudo install -d -o root -g root -m 0700 /var/lib/loomai-serving-images
sudo install -o root -g root -m 0644 scripts/infrastructure-monitoring/serving_image_updater.py scripts/infrastructure-monitoring/build-catalogue.json /usr/local/lib/loomai/
sudo install -o root -g root -m 0644 scripts/infrastructure-monitoring/loomai-serving-images.service scripts/infrastructure-monitoring/loomai-serving-images.timer /etc/systemd/system/
sudo /usr/bin/python3 -B /usr/local/lib/loomai/serving_image_updater.py
```

4. The unflagged command is check-only: it may pull/inspect image cache but never
   changes Coolify. After a successful canary and explicit approval, enable the
   timer below; its service explicitly includes `--apply`.

```sh
sudo systemctl daemon-reload
sudo systemctl enable --now loomai-serving-images.timer
```

5. Verify a later CI current-channel update is adopted once on the correct app,
   with no host build and unchanged configuration. Check native image/health and
   live product routes; record before/after host build load. No hosted acceptance,
   resource saving or full 010.60 closure is claimed by these local tests.
6. Rollback: stop the timer, wait for/inspect pending deployment receipts, restore
   the previously qualified immutable image on the same UUID and use pull-only
   deploy. Preserve data/configuration. Do not re-enable serving-host builds as
   fallback. A historical-SHA dispatch publishes an image but never moves current.

## Verification

```sh
node --test scripts/infrastructure-monitoring/build-catalogue.test.mjs
VERIFY_FRAMEWORK_SOURCE=1 node --test scripts/infrastructure-monitoring/build-catalogue.test.mjs
python3 -B -m unittest discover -s scripts/infrastructure-monitoring -p test_serving_image_updater.py -v
```

Tests execute the actual CI selector/promotion with fake registry/GitHub ports,
validate local COPY contexts and optionally all fifteen upstream Dockerfiles.
Python tests use fake HTTP/Docker ports only, covering authenticated loopback
paths, root file modes, labels/digests, busy/no-op/check-only behavior, readback
failures, durable intent, lost responses and no blind replay. CI runs both
offline suites without access to any hosted configuration or management token.
