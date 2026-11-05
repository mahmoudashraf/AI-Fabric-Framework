#!/usr/bin/env python3
"""Fixed, host-local resource observation. No provider mutations or arbitrary commands."""

import argparse
import datetime
import json
import math
import os
from pathlib import Path
import re
import resource
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import urllib.request
import urllib.parse


MAX_RESOURCES = 200
MAX_BATCH_BYTES = 262144
INSPECT = (
    '{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Config.Image}},'
    '"providerId":{{json (index .Config.Labels "coolify.name")}},'
    '"providerType":{{json (index .Config.Labels "coolify.type")}},'
    '"resourceName":{{json (index .Config.Labels "coolify.resourceName")}},'
    '"pid":{{json .State.Pid}},"startedAt":{{json .State.StartedAt}},'
    '"state":{{json .State.Status}},"health":{{if index .State "Health"}}{{json (index .State "Health").Status}}{{else}}null{{end}},'
    '"oomKilled":{{json .State.OOMKilled}},"restartCount":{{json .RestartCount}},'
    '"memoryLimit":{{json .HostConfig.Memory}},"memoryReservation":{{json .HostConfig.MemoryReservation}},'
    '"memorySwapLimit":{{json .HostConfig.MemorySwap}},"nanoCpus":{{json .HostConfig.NanoCpus}},'
    '"cpuQuota":{{json .HostConfig.CpuQuota}},"cpuPeriod":{{json .HostConfig.CpuPeriod}},'
    '"cpusetCpus":{{json .HostConfig.CpusetCpus}}}'
)


def read_text(path):
    return Path(path).read_text(encoding="utf-8").strip()


def counters(path, colon=False):
    result = {}
    for line in read_text(path).splitlines():
        parts = line.replace(":", " ").split() if colon else line.split()
        if len(parts) >= 2:
            try:
                result[parts[0]] = int(parts[1])
            except ValueError:
                pass
    return result


def delta(current, previous, key, elapsed):
    if previous is None or elapsed <= 0 or key not in current or key not in previous:
        return None
    value = current[key] - previous[key]
    return value / elapsed if value >= 0 else None


def memory_bytes(value):
    part = value.split("/")[0].strip()
    match = re.fullmatch(r"([0-9.]+)(GiB|MiB|KiB|GB|MB|kB|B)", part)
    if not match:
        raise ValueError("MEMORY_UNIT_UNSUPPORTED")
    factors = {"GiB": 2**30, "MiB": 2**20, "KiB": 2**10, "GB": 10**9,
               "MB": 10**6, "kB": 1000, "B": 1}
    return round(float(match[1]) * factors[match[2]])


def role(name, image):
    if image.rsplit("/", 1)[-1].split(":", 1)[0] in ["postgres", "pgvector"]:
        return "database"
    for marker, result in [("vectorization", "vectorization-worker"), ("connector", "connector"),
                           ("ai-fabric-runtime", "runtime"), ("weaviate", "vector"),
                           ("milvus", "vector"), ("qdrant", "vector"), ("postgres", "database"),
                           ("buildkit", "build"), ("loomai-platform", "platform"),
                           ("loomai-desk", "desk"), ("standalone-ai-workspace", "engage")]:
        if marker in name or marker in image:
            return result
    return "shared" if name.startswith("coolify") else "unmapped"


def docker(args):
    process = subprocess.run(["/usr/bin/docker", *args], stdin=subprocess.DEVNULL,
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20,
                             env={"PATH": "/usr/bin:/bin", "HOME": "/nonexistent"}, check=False)
    if process.returncode != 0 or len(process.stdout) > 2 * 1024 * 1024:
        raise RuntimeError("DOCKER_READ_UNAVAILABLE")
    return process.stdout.decode("utf-8")


def cgroup(pid):
    try:
        line = next(line for line in read_text(f"/proc/{pid}/cgroup").splitlines() if line.startswith("0::"))
        relative = line.split("::", 1)[1]
        base = Path("/sys/fs/cgroup") / relative.lstrip("/")
        base.resolve().relative_to(Path("/sys/fs/cgroup"))
        return base
    except (OSError, StopIteration, ValueError):
        return None


def host_snapshot(previous, now):
    memory = {key: value * 1024 for key, value in counters("/proc/meminfo", True).items()}
    cpu = [int(x) for x in read_text("/proc/stat").splitlines()[0].split()[1:9]]
    vm = counters("/proc/vmstat")
    network = {"rx": 0, "tx": 0}
    for line in read_text("/proc/net/dev").splitlines()[2:]:
        name, values = line.split(":", 1)
        if name.strip() == "lo" or name.strip().startswith(("veth", "docker", "br-")):
            continue
        parts = values.split()
        network["rx"] += int(parts[0]); network["tx"] += int(parts[8])
    raw = {"time": now, "cpu": cpu, "vm": vm, "network": network}
    elapsed = now - previous["time"] if previous else 0
    utilization = steal = None
    if previous:
        old_cpu = previous["cpu"]
        total = sum(cpu) - sum(old_cpu)
        idle = cpu[3] + cpu[4] - old_cpu[3] - old_cpu[4]
        if total > 0 and all(current >= old for current, old in zip(cpu, old_cpu)):
            utilization = round(100 * (total - idle) / total, 3)
            steal = round(100 * (cpu[7] - old_cpu[7]) / total, 3)
    disk = shutil.disk_usage("/")
    fs = os.statvfs("/")
    pages = os.sysconf("SC_PAGE_SIZE")
    incoming = delta(vm, previous["vm"] if previous else None, "pswpin", elapsed)
    outgoing = delta(vm, previous["vm"] if previous else None, "pswpout", elapsed)
    loads = os.getloadavg()
    host = {"cpuThreads": os.cpu_count(), "cpuUsagePercent": utilization,
            "load1": loads[0], "load5": loads[1], "load15": loads[2],
            "memoryTotalBytes": memory["MemTotal"], "memoryAvailableBytes": memory["MemAvailable"],
            "swapTotalBytes": memory["SwapTotal"], "swapUsedBytes": memory["SwapTotal"] - memory["SwapFree"],
            "swapInBytesPerSecond": None if incoming is None else incoming * pages,
            "swapOutBytesPerSecond": None if outgoing is None else outgoing * pages,
            "diskTotalBytes": disk.total, "diskFreeBytes": disk.free,
            "inodesTotal": fs.f_files, "inodesFree": fs.f_favail,
            "networkRxBytesPerSecond": delta(network, previous["network"] if previous else None, "rx", elapsed),
            "networkTxBytesPerSecond": delta(network, previous["network"] if previous else None, "tx", elapsed),
            "cpuStealPercent": steal}
    return host, raw


def resource_snapshot(inspected, stats, previous, now):
    identifier = inspected["id"]
    observed = stats.get(identifier[:12], {})
    running = inspected["state"] == "running"
    previous_row = previous.get(identifier)
    base = cgroup(inspected["pid"]) if running else None
    current_cpu = None
    working = None
    cores = throttled = None
    if base:
        try:
            memory = int(read_text(base / "memory.current"))
            memory_stat = counters(base / "memory.stat")
            working = max(0, memory - memory_stat.get("inactive_file", 0))
            current_cpu = counters(base / "cpu.stat")
            if previous_row and previous_row.get("startedAt") == inspected["startedAt"]:
                elapsed = now - previous_row["time"]
                usage = delta(current_cpu, previous_row.get("cpu"), "usage_usec", elapsed)
                cores = None if usage is None else usage / 1_000_000
                old = previous_row.get("cpu") or {}
                periods = current_cpu.get("nr_periods", 0) - old.get("nr_periods", 0)
                count = current_cpu.get("nr_throttled", 0) - old.get("nr_throttled", 0)
                if periods > 0 and 0 <= count <= periods:
                    throttled = 100 * count / periods
        except OSError:
            pass
    if working is None and observed.get("MemUsage"):
        working = memory_bytes(observed["MemUsage"])
    if cores is None and observed.get("CPUPerc"):
        cores = float(observed["CPUPerc"].rstrip("%")) / 100
    if not running:
        working = cores = 0
    provider_id = inspected.get("providerId")
    if provider_id and not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}", provider_id):
        provider_id = None
    provider_type = {"application": "APPLICATION", "database": "DATABASE"}.get(inspected.get("providerType"), "UNMAPPED")
    name = inspected.get("resourceName") or inspected["name"].lstrip("/")
    quota = inspected["nanoCpus"] / 1_000_000_000 if inspected["nanoCpus"] > 0 else None
    if quota is None and inspected["cpuQuota"] > 0 and inspected["cpuPeriod"] > 0:
        quota = inspected["cpuQuota"] / inspected["cpuPeriod"]
    if quota is None:
        quota = 0  # The inspected Docker configuration explicitly has no CPU quota.
    row = {"resourceId": identifier, "name": name[:180], "image": inspected["image"][:300],
           "providerResourceId": provider_id, "providerKind": provider_type,
           "role": role(name, inspected["image"]), "startedAt": inspected["startedAt"],
           "state": inspected["state"], "health": inspected["health"],
           "memoryWorkingSetBytes": working, "memoryLimitBytes": inspected["memoryLimit"],
           "memoryReservationBytes": inspected["memoryReservation"],
           "memorySwapLimitBytes": inspected["memorySwapLimit"], "cpuCoresUsed": cores,
           "cpuQuota": quota, "cpusetCpus": inspected["cpusetCpus"],
           "cpuThrottledPeriodsPercent": throttled, "restartCount": inspected["restartCount"],
           "oomKilled": inspected["oomKilled"]}
    return row, {"startedAt": inspected["startedAt"], "time": now, "cpu": current_cpu}


def collect(config, old):
    begin = time.monotonic()
    now = time.time()
    boot = read_text("/proc/sys/kernel/random/boot_id")
    previous = old if old.get("bootId") == boot else {}
    host, raw_host = host_snapshot(previous.get("host"), now)
    ids = docker(["ps", "-aq"]).split()
    complete = len(ids) <= MAX_RESOURCES
    # Running resources are first so historical stopped containers do not displace live observations.
    running = docker(["ps", "-q"]).split()
    ids = list(dict.fromkeys(running + ids))[:MAX_RESOURCES]
    inspected = [json.loads(line) for line in docker(["inspect", "--format", INSPECT, *ids]).splitlines()] if ids else []
    stats = {row["ID"]: row for row in [json.loads(line) for line in docker(["stats", "--no-stream", "--format", "{{json .}}"] ).splitlines()]} if running else {}
    rows = []; raw_resources = {}
    for item in inspected:
        row, state = resource_snapshot(item, stats, previous.get("resources", {}), now)
        rows.append(row); raw_resources[item["id"]] = state
    payload = {"schemaVersion": "1", "serverId": config["serverId"], "bootId": boot,
               "observedAt": datetime.datetime.fromtimestamp(now, datetime.timezone.utc).isoformat(),
               "collectionDurationMs": round((time.monotonic() - begin) * 1000), "complete": complete,
               "source": "HOST_NATIVE", "host": host, "resources": rows}
    return payload, {"bootId": boot, "host": raw_host, "resources": raw_resources}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def load_config(path):
    permissions = path.stat()
    if permissions.st_uid != os.geteuid() or stat.S_IMODE(permissions.st_mode) & 0o077:
        raise ValueError("CONFIG_PERMISSIONS_REQUIRED")
    value = json.loads(path.read_text(encoding="utf-8"))
    if set(value) - {"serverId", "url", "ingestKey", "stateFile", "allowLoopbackHttp"}:
        raise ValueError("CONFIG_FIELDS_UNSUPPORTED")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}", value.get("serverId", "")):
        raise ValueError("SERVER_ID_REQUIRED")
    key = value.get("ingestKey", "")
    if not 32 <= len(key) <= 512 or any(ord(c) < 33 or ord(c) > 126 for c in key):
        raise ValueError("INGEST_KEY_REQUIRED")
    parsed = urllib.parse.urlsplit(value.get("url", ""))
    if parsed.username or parsed.password or parsed.query or parsed.fragment or not parsed.hostname:
        raise ValueError("FIXED_METRICS_ENDPOINT_REQUIRED")
    if parsed.scheme != "https" and not (value.get("allowLoopbackHttp") is True and parsed.scheme == "http"
                                         and parsed.hostname in ["127.0.0.1", "::1", "localhost"]):
        raise ValueError("HTTPS_REQUIRED")
    if parsed.path.rstrip("/") != "/internal/v1/telemetry/samples":
        raise ValueError("INGEST_ROUTE_REQUIRED")
    return value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, default=Path("/etc/loomai/server-metrics.json"))
    parser.add_argument("--observe", action="store_true", help="Read-only snapshot with no delivery or state write.")
    args = parser.parse_args()
    try:
        config = load_config(args.config)
        state_path = Path(config.get("stateFile", "/var/lib/loomai-metrics/counters.json"))
        try:
            old = json.loads(state_path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            old = {}
        sample, state = collect(config, old)
        encoded = json.dumps(sample, separators=(",", ":"), allow_nan=False).encode("utf-8")
        if len(encoded) > MAX_BATCH_BYTES:
            raise ValueError("METRICS_BATCH_TOO_LARGE")
        if args.observe:
            print(encoded.decode("utf-8"))
            return 0
        request = urllib.request.Request(config["url"], data=encoded,
                                         headers={"Content-Type": "application/json", "Authorization": "Bearer " + config["ingestKey"]},
                                         method="POST")
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=8) as response:
            if response.status not in [200, 201, 202, 204]:
                raise RuntimeError("METRICS_DELIVERY_REJECTED")
        state_path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        fd, name = tempfile.mkstemp(dir=state_path.parent)
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                json.dump(state, handle, allow_nan=False)
            os.replace(name, state_path)
        finally:
            if os.path.exists(name): os.unlink(name)
        own = resource.getrusage(resource.RUSAGE_SELF)
        children = resource.getrusage(resource.RUSAGE_CHILDREN)
        print(json.dumps({"status": "DELIVERED", "serverId": sample["serverId"], "observedAt": sample["observedAt"],
                          "resources": len(sample["resources"]), "complete": sample["complete"],
                          "collectorPeakRssBytes": own.ru_maxrss * 1024,
                          "childPeakRssBytes": children.ru_maxrss * 1024,
                          "processCpuSeconds": own.ru_utime + own.ru_stime,
                          "childCpuSeconds": children.ru_utime + children.ru_stime}))
        return 0
    except Exception as error:
        # Do not print request objects, provider output or configuration containing credentials.
        print(json.dumps({"status": "FAILED", "reason": type(error).__name__}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
