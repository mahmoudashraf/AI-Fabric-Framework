# First-Party UI Visit Counts

Hidden page-view events from the public websites and product portals go directly
to Supabase. There is no analytics dashboard, visible counter, banner, customer
configuration change, Java backend change or runtime/framework dependency.

## Data Flow

`first-party UI -> Supabase ui-visits Edge Function -> private hourly counters`

The existing Supabase project is `xazkenhomhtpejjjqtsy`. Public collector:

`https://xazkenhomhtpejjjqtsy.supabase.co/functions/v1/ui-visits`

The browser sends exactly `{ "page": "/deployments", "viewport": "desktop" }`.
The function derives the site/hostname from an allowed HTTPS Origin. The server's
clock supplies the UTC hour, then an atomic increment updates one aggregate row.
There are no visitor-level event records or unique-visitor/session estimates.

## Coverage

- `ai-fabric.dev` and `www.ai-fabric.dev`: AI Fabric website, public docs/demos.
- `loomai.pro` and `www.loomai.pro`: public website and dealership demo pages.
- `console.loomai.pro`, `desk.loomai.pro`, `partners.loomai.pro` and
  `shopify-bridge.loomai.pro`: their frontend pages only.
- `engage.loomai.pro`, `engage-canary.loomai.pro` and one-level
  `<slug>.apps.loomai.pro` addresses: shared Engage frontend.
- App-subdomain visits are grouped as `apps.loomai.pro`; individual customer
  slugs are not stored. API hosts, AI runtime routes, staging sslip addresses,
  localhost and customer-owned domains are not counted. New first-party hosts
  require an explicit policy entry and the same frontend snippet.

All UIs include the same self-hosted `/assets/loomai-ui-visits.js` file. There is
no remote analytics SDK. Public-site CSP adds only the exact collector project
origin to `connect-src`; script policy, authentication and API routes stay intact.

## Minimization And Limits

- Only fixed page categories, hostname group, viewport category and UTC hour are
  stored. Nested paths/resource IDs, query strings, hashes, page titles, account
  IDs, emails, forms, chat contents, cookies, user agents and referrers are not
  read into events. Requests omit credentials and referrer.
- No tracking cookies, local/session storage, persistent visitor IDs, cross-site
  identity linking or fingerprinting. DNT/GPC stop collection. Hidden-tab initial
  views are delayed until visible. Query-only URL changes do not count again.
- SPA navigation and normal page loads are counted. Same-path notifications
  within a loaded page are deduplicated in memory. Reloads are new views.
- Blockers, privacy settings, offline clients and disabled JavaScript cause
  undercounting. Origins can be spoofed by non-browser clients; counts are not
  authenticated business activity, billing, security or capacity evidence.
- Body size is bounded, page labels have fixed cardinality, and a counter caps
  at 100,000 per host/page/viewport/hour. This is not comprehensive bot protection
  or a guarantee against invocation costs.
- Analytics failure is silent, non-blocking, and never retried automatically.
  Supabase may retain normal platform/network logs independently; this code does
  not persist IP addresses or request contents. Minimization is not a legal
  compliance certification.
- Hourly aggregates expire after 90 days via the dedicated daily Supabase Cron
  job `loomai-ui-visits-retention`. No existing jobs/data/auth settings change.

## Read Statistics In Supabase Only

Open [the project SQL Editor](https://supabase.com/dashboard/project/xazkenhomhtpejjjqtsy/sql/new):

```sql
-- Daily site totals.
select day_utc, site, hostname, sum(views) as views
from loomai_web_analytics.daily_visits
group by day_utc, site, hostname
order by day_utc desc, views desc;

-- Page categories, with no private paths.
select * from loomai_web_analytics.daily_visits
order by day_utc desc, hostname, views desc;

-- Coarse viewport counts.
select site, viewport, sum(views) as views
from loomai_web_analytics.hourly_visits
where hour_bucket >= now() - interval '7 days'
group by site, viewport order by site, viewport;
```

The schema is not exposed through the public Data API. Tables use RLS and grant
no access to `anon`/`authenticated`; the write RPC permits only `service_role`.
The public Edge Function accepts events but has no read/statistics endpoint.
Project administrators can inspect the aggregates in Supabase Studio. No project
service key or management token is shipped in any frontend.

The AI Fabric website previously had an unrelated `usePageTracking` writer
against an older Supabase project. Its call is removed to avoid duplicate/raw
agent/referrer collection. Historical data is not deleted or migrated. Existing
historical article-counter read components are unchanged and do not display
these new cross-product statistics.

## Maintenance And Verification

Edit `client/entry.mjs` and `supabase/functions/ui-visits/policy.mjs`, then:

```sh
node web-analytics/scripts/sync-client.mjs /absolute/path/to/aifabric/public
node --test web-analytics/tests/ui-visits.test.mjs
CHECK_UI_VISITS_CLIENT=true node web-analytics/scripts/sync-client.mjs /absolute/path/to/aifabric/public
```

Generated frontend copies are committed so each repository/image is standalone.
Build each changed frontend normally. Do not rebuild/release the AI framework or
change a customer runtime/deployment merely to add this script.

With a privately supplied `SUPABASE_ACCESS_TOKEN`, deploy only this function:

```sh
supabase functions deploy ui-visits --workdir web-analytics \
  --project-ref xazkenhomhtpejjjqtsy --use-api --no-verify-jwt
```

Apply only this directory's migrations, not another application's full migration
history. JWT verification is intentionally off for this new anonymous event
collector, not for any existing function or authentication endpoint. Origin/
payload checks limit its purpose; they are not user authentication. The function
uses its platform-provided service credential solely for the restricted write RPC.

Rollback collection by removing the snippet or disabling only `ui-visits`.
Retain recorded aggregates unless deletion is explicitly requested. Removing
collection must never affect sign-in, chat, support or application navigation.

## Hosted Qualification: 2026-10-30

- Supabase `ui-visits` version 1 is ACTIVE. Both scoped migrations are applied;
  the dedicated 90-day retention job is active. Valid events increment private
  counters; unknown origins, extra fields and public reads are rejected. Direct
  anonymous RPC calls are denied, and anonymous/authenticated roles have no
  schema or write-function access.
- Analytics source `aa5a92e2743a92b7cbebc51565233bdb3b55d81c` is pushed on
  `Platform-V11`. Production public site, Console, Partners, Shopify Bridge,
  shared Engage and Desk all serve the exact client asset. Console, Engage and
  Desk images were first qualified on staging. Existing service settings,
  routes, authentication and customer deployment assignments were preserved.
- Real browser loads/navigation produced aggregate rows for `loomai.pro`,
  `console.loomai.pro`, `desk.loomai.pro`, `partners.loomai.pro`,
  `shopify-bridge.loomai.pro`, `engage-canary.loomai.pro` and the grouped
  `apps.loomai.pro`. Console query parameters and individual app slugs are absent
  from the stored rows. These initial counts are verification traffic, not
  evidence of organic visitors or unique people.
- Eight analytics tests plus the public-site production-server regression pass;
  all seven affected frontend builds pass, and seven mirrored client assets
  match. Native rollout health, source and asset checks pass. No Java source,
  AI runtime, framework version or customer binding changed. Screenshots of the
  unchanged product UI are in ignored `output/ui-visits/`.
- **AI Fabric website publication is verified.** After the owner published the
  site, `ai-fabric.dev` serves the exact client asset from source `213fdf8`
  (SHA256 `dcc23dcb990bdcf83e46fe812102eaa5035fd8dec2ecea6667c3964059d613a9`).
  The published HTML includes the tracker; a real browser load and Course link
  navigation produced private Supabase counters for `/` and `/course` without
  browser errors or visible analytics UI. This closes the earlier 404/publisher
  gap. These two initial views are verification traffic, not organic visitors.

Private deployment receipts live under
`~/.codex/private/loomai-ui-visits/receipts/`; they are not public build inputs.
Disabling collection is independent of the pending integration-support proposal.

Primary references: [Supabase function deployment](https://supabase.com/docs/guides/functions/deploy),
[public-function authentication](https://supabase.com/docs/guides/functions/auth),
[database function privileges](https://supabase.com/docs/guides/database/functions),
[Supabase Cron](https://supabase.com/docs/guides/cron).
