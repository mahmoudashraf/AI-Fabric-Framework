# Dealership AI Live Quality Check

This verification harness drives the deployed dealership experience through
the same public browser integration used by customers:

- route: `https://loomai.pro/demos/dealership-ai`;
- integration: `public-runtime-anonymous`;
- UI mode: `executor`;
- UI position: `search`;
- viewport: `390x844`; and
- one continuous iterative conversation.

It runs current-stock action queries, contextual follow-ups, comparison,
semantic RAG, empty-action-to-RAG fallback, unsupported-policy honesty, and a
governed write-intent check. All seven queries remain in one runtime-owned
conversation. The final check never confirms the action, so the default matrix
creates no dealership lead or other application write.

Run from the public-site directory:

```bash
node --version # requires Node.js 22.12 or newer
npm ci
npm run quality:dealership-live
```

The JSON report is written to
`Platfrom/loomai-site/test-results/dealership-quality/latest.json` and printed
to stdout. The default is diagnostic: quality failures produce
`NEEDS_IMPROVEMENT` but do not return a failing process code. Use strict mode
for a release candidate whose assertions have become stable:

```bash
DEALERSHIP_QUALITY_STRICT=true npm run quality:dealership-live
```

Useful overrides:

```bash
DEALERSHIP_QUALITY_ORIGIN=https://loomai.pro \
DEALERSHIP_QUALITY_HEADLESS=false \
DEALERSHIP_QUALITY_TIMEOUT_MS=120000 \
npm run quality:dealership-live
```

The harness changes no deployment, prompt, framework, runtime environment, or
provider data. Recommendations in the report are advisory and must be judged
against the immutable deployment lifecycle before implementation.

The widget's `features.debug` flag only shows an inspector for the raw request
and response already received by the browser. The harness captures that raw
network exchange directly, then records the useful debug fields without
turning on a different conversation mode or changing the deployed widget.

The report distinguishes two kinds of iteration:

- conversation iteration, where later user turns retain the same
  `conversationId` and receive bounded history; and
- read-action planner iteration, which is a deployment policy choice such as
  `SINGLE_PASS` or bounded `ITERATIVE` planning.

That distinction keeps UI mode selection stable. Deployment version v8 now
proves bounded `ITERATIVE` planning with `maxIterations=2` and
`RAG_IF_ACTIONS_INSUFFICIENT` while the browser remains on executor/search.
The post-canary empty-result failure is therefore framework evidence, not
permission for the browser or this harness to switch modes or mutate live
configuration.
