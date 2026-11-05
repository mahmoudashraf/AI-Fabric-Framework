import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("host_collector", Path(__file__).with_name("host_collector.py"))
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


class HostCollectorTest(unittest.TestCase):
    def test_units_and_counter_reset_do_not_invent_rates(self):
        self.assertEqual(collector.memory_bytes("1.5GiB / 2GiB"), 1610612736)
        self.assertEqual(collector.memory_bytes("512MiB / 1GiB"), 536870912)
        self.assertIsNone(collector.delta({"x": 4}, {"x": 8}, "x", 60))
        self.assertIsNone(collector.delta({"x": 4}, None, "x", 60))
        self.assertEqual(collector.delta({"x": 120}, {"x": 60}, "x", 60), 1)

    def test_configuration_requires_private_file_and_fixed_https_ingestion(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory) / "config.json"
            good = {"serverId": "staging", "url": "https://metrics.example/internal/v1/telemetry/samples", "ingestKey": "x" * 64}
            p.write_text(json.dumps(good)); p.chmod(0o600)
            self.assertEqual(collector.load_config(p)["serverId"], "staging")
            for change in [{"url": "http://metrics.example/internal/v1/telemetry/samples"},
                           {"url": "https://user:password@metrics.example/internal/v1/telemetry/samples"},
                           {"url": "https://metrics.example/internal/v1/deployment-operations/assess"},
                           {"url": "https://metrics.example/internal/v1/telemetry/samples?token=secret"},
                           {"command": "docker rm all"}, {"ingestKey": "short"}]:
                p.write_text(json.dumps(good | change))
                with self.assertRaises(ValueError): collector.load_config(p)
            p.write_text(json.dumps(good)); p.chmod(0o644)
            with self.assertRaises(ValueError): collector.load_config(p)

    def test_inspect_projection_never_reads_env_logs_or_full_labels(self):
        self.assertNotIn(".Config.Env", collector.INSPECT)
        self.assertNotIn(".State.Health.Log", collector.INSPECT)
        self.assertNotIn("{{json .Config.Labels}}", collector.INSPECT)
        self.assertIn('index .Config.Labels "coolify.name"', collector.INSPECT)
        self.assertIn('if index .State "Health"', collector.INSPECT)

    def test_stopped_resource_is_zero_and_unknown_limit_is_not_reserved_memory(self):
        data = {"id": "a" * 64, "name": "/db", "image": "postgres:16", "providerId": "db-id", "providerType": "database",
                "resourceName": "database", "state": "exited", "pid": 0, "startedAt": "2026-11-05T00:00:00Z",
                "health": None, "memoryLimit": 536870912, "memoryReservation": 0, "memorySwapLimit": 536870912,
                "nanoCpus": 250000000, "cpuQuota": 0, "cpuPeriod": 0, "cpusetCpus": "", "restartCount": 0, "oomKilled": False}
        row, raw = collector.resource_snapshot(data, {}, {}, 100)
        self.assertEqual(row["memoryWorkingSetBytes"], 0)
        self.assertEqual(row["cpuCoresUsed"], 0)
        self.assertEqual(row["memoryLimitBytes"], 536870912)
        self.assertEqual(row["cpuQuota"], .25)
        self.assertIsNone(row["cpuThrottledPeriodsPercent"])

    def test_cgroup_values_are_measured_and_incarnation_reset_is_not_negative(self):
        data = {"id": "a" * 64, "name": "/app", "image": "runtime:1", "providerId": "app", "providerType": "application",
                "resourceName": "ai-fabric-runtime-dep-a", "state": "running", "pid": 1, "startedAt": "2026-11-05T00:00:00Z",
                "health": "healthy", "memoryLimit": 1024, "memoryReservation": 512, "memorySwapLimit": 1024,
                "nanoCpus": 1000000000, "cpuQuota": 0, "cpuPeriod": 0, "cpusetCpus": "7", "restartCount": 1, "oomKilled": False}
        with patch.object(collector, "cgroup", return_value=Path("/cgroup")), \
             patch.object(collector, "read_text", return_value="1000"), \
             patch.object(collector, "counters", side_effect=[{"inactive_file": 100}, {"usage_usec": 2000000, "nr_periods": 10, "nr_throttled": 1}]):
            row, _ = collector.resource_snapshot(data, {}, {data["id"]: {"time": 90, "startedAt": data["startedAt"], "cpu": {"usage_usec": 1000000, "nr_periods": 5, "nr_throttled": 0}}}, 100)
        self.assertEqual(row["memoryWorkingSetBytes"], 900)
        self.assertEqual(row["cpuCoresUsed"], .1)
        self.assertEqual(row["cpuThrottledPeriodsPercent"], 20)


if __name__ == "__main__":
    unittest.main()
