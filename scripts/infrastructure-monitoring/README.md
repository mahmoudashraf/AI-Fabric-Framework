# Host Resource Monitoring (010.60)

Read-only observations for the existing Operations service and the Platform
administrator's **Servers & Resources** page. Telemetry is not a deployment
admission dependency and does not change customer quotas.

## Components

- `host_collector.py`: Python standard-library collector, approximately once a
  minute, using Linux `/proc`, cgroup v2 and a fixed Docker inspect projection.
- `loomai-server-metrics.service` / `.timer`: bounded host-local observation.
- Operations `/internal/v1/telemetry`: isolated ingest/read credentials and
  history in the existing environment's Operations PostgreSQL database.
- Platform `/api/admin/infrastructure/monitoring`: administrator-only read
  facade. The browser never receives telemetry credentials.

No container environment, logs, complete labels, customer messages, SQL contents
or arbitrary command execution is collected. The Docker socket remains a
privileged host boundary: install only this reviewed script, owned by root.

## Installation

Install the Python file at `/usr/local/lib/loomai/host_collector.py` and the units
in `/etc/systemd/system/`. Create `/var/lib/loomai-metrics` mode `0700` and
`/etc/loomai/server-metrics.json` owned by root, mode `0600`:

```json
{
  "serverId": "staging",
  "url": "https://metrics.example/internal/v1/telemetry/samples",
  "ingestKey": "<independent-host-scoped-random-secret>"
}
```

Use `python3 /usr/local/lib/loomai/host_collector.py --observe` before enabling
delivery. This emits a real snapshot without writing state or sending a sample.
Once the Operations source is configured, enable the timer and start the service:

```sh
systemctl daemon-reload
systemctl enable --now loomai-server-metrics.timer
systemctl start loomai-server-metrics.service
```

The service is capped at 96 MiB and 10% of one CPU. Delivery is HTTPS only;
redirects are rejected. Explicit loopback HTTP exists only for local fixtures.
Collection/delivery failure leaves old observations stale and does not stop a
customer deployment. Do not manually send multiple distinct samples in one
minute: Operations retains one distinct observation per host/UTC minute.

Expose **only** `/internal/v1/telemetry` on the metrics HTTPS route; retain the
path prefix. Do not accidentally publish the Operations lifecycle namespace.
For Coolify path routing, disable Strip Prefix for this application, then verify
the actual Traefik router rule and an outside-prefix unrouted response. The
current Coolify catch-all returns `503 no available server`, not application JSON.

## Platform Configuration

Set `PLATFORM_INFRASTRUCTURE_MONITORING_ENABLED=true` and
`PLATFORM_INFRASTRUCTURE_MONITORING_SOURCES_JSON` to a private array:

```json
[
  {
    "id": "staging",
    "displayName": "Staging host",
    "environment": "staging",
    "url": "https://metrics.example",
    "readKey": "<independent-read-only-random-secret>",
    "providerServerUuid": "<trusted-coolify-server-uuid>"
  }
]
```

The source ID must match the Operations server ID. Provider ownership is mapped
from Platform's existing server/resource handles, never asserted by a host label
or a collector-supplied customer ID. Unknown/ambiguous resources remain unmapped.
No credential belongs in a repository, screenshot, frontend environment or URL.

## Interpreting Measurements

- Host CPU is percent of all logical threads; container CPU uses core-equivalents.
- Working-set memory is cgroup usage minus inactive file cache, not a reservation.
- Memory ceilings/reservations and CPU quota/affinity are distinct settings.
- Occupied swap is not current swap activity. Missing rates are unknown, not zero.
- First samples and recreated containers may lack counter-derived rates.
- History starts at installation. Missing buckets are gaps, not synthetic history.

The collector reports at most 200 containers, prioritizing running containers and
marking larger inventories partial. Operations retains raw samples for 24 hours
and five-minute aggregates for seven days. Queries and response sizes are bounded.

## Scheduling and JVM Qualification

An explicit target-profile `runtimeLimitsCpuSet` / `connectorLimitsCpuSet` empty
string clears an old CPU pin. An omitted value does not manage that setting.
Existing CPU quotas, memory ceilings and `ActiveProcessorCount=1` are retained.
Initial-heap candidates are runtime `-Xms64m -Xmx768m` and connector
`-Xms32m -Xmx512m`. A lower `Xms` is not a smaller memory ceiling, an autoscaler
or a promise of a specific customer count. Qualify startup, warm concurrent chat,
indexing and handover before adopting a profile; retain the larger profile for
workloads requiring it. Revert through an ordinary reviewed target configuration.

## Verification and Build Separation

```sh
python3 -m unittest discover -s scripts/infrastructure-monitoring -p 'test_*.py'
```

See [CI build separation](CI-build-separation-README.md) for off-host image builds.
See the Operations README for telemetry authentication, migrations and retention.
The execution record under 010.60 records actual hosted qualification separately
from implementation and fixture tests.
