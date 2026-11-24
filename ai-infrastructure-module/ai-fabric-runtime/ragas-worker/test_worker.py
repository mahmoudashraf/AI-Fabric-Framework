import asyncio
import importlib.metadata
import unittest
import subprocess
import sys
import json
import threading
import worker


class ScriptedBridge:
    """Offline inference fixtures; the real installed Ragas metric implementations calculate scores."""
    def __init__(self):
        self.calls = []

    def invoke(self, kind, **payload):
        self.calls.append((kind, payload))
        if kind == "embed":
            return [1.0, 0.0]
        schema = payload["schema"]["title"]
        return {
            "StatementGeneratorOutput": {"statements": ["Returns take 30 days.", "Returns are free."]},
            "ClaimDecompositionOutput": {"claims": ["Returns take 30 days.", "Returns are free."]},
            "NLIStatementOutput": {"statements": [
                {"statement": "Returns take 30 days.", "reason": "Supported.", "verdict": 1},
                {"statement": "Returns are free.", "reason": "Not supported.", "verdict": 0}]},
            "AnswerRelevanceOutput": {"question": "What is the return window?", "noncommittal": 0},
            "ContextPrecisionOutput": {"reason": "Contains the policy.", "verdict": 1},
            "ContextRecallOutput": {"classifications": [
                {"statement": "30 days.", "reason": "Present.", "attributed": 1}]},
        }[schema]


class WorkerTests(unittest.TestCase):
    sample = {"question": "What is the return window?", "answer": "Returns take 30 days. Returns are free.",
              "evidence": [{"id": "chunk", "sourceId": "policy", "content": "Returns are accepted within 30 days."}],
              "referenceAnswer": "30 days."}

    def test_pinned_real_ragas_metrics_and_all_subcalls(self):
        self.assertEqual(importlib.metadata.version("ragas"), "0.4.4")
        bridge = ScriptedBridge()
        result = asyncio.run(worker.evaluate(self.sample, bridge))
        self.assertEqual(result["providerId"], "ragas")
        self.assertEqual(result["rubricVersion"], worker.ENGINE)
        self.assertEqual(result["metrics"]["faithfulness"]["score"], 0.5)
        self.assertAlmostEqual(result["metrics"]["relevance"]["score"], 1.0)
        self.assertAlmostEqual(result["metrics"]["contextPrecision"]["score"], 1.0)
        self.assertAlmostEqual(result["metrics"]["contextRecall"]["score"], 1.0)
        self.assertAlmostEqual(result["metrics"]["referenceAgreement"]["score"], 0.5)
        self.assertEqual(sum(kind == "generate" for kind, _ in bridge.calls), 11)
        self.assertEqual(sum(kind == "embed" for kind, _ in bridge.calls), 4)

    def test_missing_reference_is_not_scored_instead_of_fabricated_zero(self):
        result = asyncio.run(worker.evaluate({**self.sample, "referenceAnswer": ""}, ScriptedBridge()))
        for metric in ("contextPrecision", "contextRecall", "referenceAgreement"):
            self.assertEqual(result["metrics"][metric]["status"], "NOT_SCORED")
            self.assertIsNone(result["metrics"][metric]["score"])

    def test_empty_answer_and_evidence_make_no_model_calls(self):
        bridge = ScriptedBridge()
        result = asyncio.run(worker.evaluate({**self.sample, "answer": "", "evidence": []}, bridge))
        self.assertFalse(bridge.calls)
        self.assertTrue(all(metric["status"] == "NOT_SCORED" for metric in result["metrics"].values()))

    def test_malformed_model_output_does_not_trigger_repair_or_custom_judge(self):
        class InvalidBridge(ScriptedBridge):
            def invoke(self, kind, **payload):
                self.calls.append((kind, payload))
                return {"unrelated": "not a metric result"}
        bridge = InvalidBridge()
        with self.assertRaises(Exception):
            asyncio.run(worker.evaluate(self.sample, bridge))
        self.assertEqual(len(bridge.calls), 1)

    def test_worker_network_policy_rejects_external_calls(self):
        for event in ("socket.connect", "socket.getaddrinfo", "socket.bind"):
            with self.assertRaises(PermissionError):
                worker.deny_network(event, ())

    def test_real_worker_protocol_runs_all_metrics_through_the_parent(self):
        bridge = ScriptedBridge()
        process = subprocess.Popen([sys.executable, "-I", "-B", worker.__file__], stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True,
                                   env={"LANG": "C.UTF-8", "RAGAS_DO_NOT_TRACK": "true", "OPENBLAS_NUM_THREADS": "1", "OMP_NUM_THREADS": "1"})
        timer = threading.Timer(45, process.kill)
        timer.start()
        try:
            process.stdin.write(json.dumps({"type": "evaluate", "sample": self.sample}) + "\n")
            process.stdin.flush()
            while True:
                line = process.stdout.readline(worker.MAX_LINE_BYTES + 1)
                self.assertTrue(line, "Worker stopped without a result")
                message = json.loads(line)
                if message["type"] == "complete":
                    self.assertEqual(message["result"]["metrics"]["faithfulness"]["score"], 0.5)
                    self.assertEqual(message["modelCalls"], 15)
                    break
                self.assertIn(message["type"], {"generate", "embed"})
                payload = {key: value for key, value in message.items() if key not in {"type", "id"}}
                value = bridge.invoke(message["type"], **payload)
                process.stdin.write(json.dumps({"type": "result", "id": message["id"], "value": value}) + "\n")
                process.stdin.flush()
            self.assertEqual(process.wait(timeout=5), 0)
        finally:
            timer.cancel()
            if process.poll() is None:
                process.kill()
                process.wait(timeout=5)
            process.stdin.close()
            process.stdout.close()

    @unittest.skipUnless(sys.platform == "linux", "Production syscall filter is Linux-only")
    def test_native_socket_calls_cannot_bypass_the_python_audit_hook(self):
        result = subprocess.run([sys.executable, "-B", "-c", """
import ctypes, errno, worker
worker.restrict_linux_network()
libc = ctypes.CDLL(None, use_errno=True)
for family in (2, 10):
    assert libc.socket(family, 1, 0) == -1
    assert ctypes.get_errno() == errno.EPERM
# Asyncio may still use an unnamed local socket pair; it cannot connect to an endpoint.
import asyncio
asyncio.run(asyncio.sleep(0))
"""], cwd=__import__("pathlib").Path(__file__).parent, capture_output=True, timeout=10)
        self.assertEqual(result.returncode, 0, "Linux worker isolation check failed")


if __name__ == "__main__":
    unittest.main()
