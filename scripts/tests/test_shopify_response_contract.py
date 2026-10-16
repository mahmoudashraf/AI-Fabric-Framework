import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import threading
import tempfile
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch


SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
from shopify_response_contract import validate_response

SPEC = importlib.util.spec_from_file_location("shopify_evaluator", SCRIPTS / "evaluate-shopify-companion-answers.py")
EVALUATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EVALUATOR)


def answer(**overrides):
    return {
        "success": True, "type": "INFORMATION_PROVIDED", "conversationId": "chat-fixture",
        "answer": "Approved catalog answer.", "sources": [{"id": "product-1", "title": "Product One"}],
        **overrides,
    }


class ResponseContractTest(unittest.TestCase):
    def test_canonical_grounded_answer_passes(self):
        self.assertTrue(validate_response(answer(), True)["passed"])

    def test_http_success_cannot_hide_a_structured_error(self):
        for body in [
            answer(success=False), answer(type="ERROR"), answer(type=["INFORMATION_PROVIDED"]),
            answer(errorCode="ACCESS_DENIED"), answer(fallbackReason="PROVIDER_FAILED"),
            answer(result={"success": False}), answer(result={"sanitizedPayload": {"type": "ERROR"}}),
            answer(actions=[{"actionResult": {"success": False, "errorCode": "TOOL_FAILED"}}]),
            answer(actions=[{"actionResult": {"errorCode": "TOOL_FAILED"}}]),
            answer(actions=[{"errorCode": "TOOL_FAILED"}]),
            answer(actions=[{"errorCode": "TOOL_FAILED", "customerAccountAuthRequired": True}]),
            answer(actions=[{"errorCode": "CUSTOMER_ACCOUNT_AUTH_REQUIRED"}]),
            {"conversationId": "chat-fixture", "answer": "Approved catalog answer."},
        ]:
            with self.subTest(body=body):
                self.assertFalse(validate_response(body)["passed"])

    def test_required_grounding_rejects_empty_placeholder_or_count_only_evidence(self):
        for sources in [None, [], [{}], [None], ["product-1"], [{"score": 0.99}], [{"title": "No identifier"}]]:
            with self.subTest(sources=sources):
                report = validate_response(answer(sources=sources, metadata={"sourcesCount": 4}), True)
                self.assertFalse(report["passed"])
                self.assertFalse(report["checks"]["required_grounding_present"])

    def test_declared_grounding_path_is_enforced(self):
        body = answer(sources=[], ragResponse={"documents": [{"id": "policy-1"}]})
        self.assertTrue(validate_response(body, True, ["ragResponse.documents"])["passed"])
        self.assertFalse(validate_response(body, True, ["sources"])["passed"])

    def test_grounding_documents_inside_successful_action_are_supported(self):
        body = answer(sources=[], actions=[{"actionResult": {"success": True, "data": {"documents": [{"id": "live-product-1"}]}}}])
        self.assertTrue(validate_response(body, True, ["actions[].actionResult.data.documents"])["passed"])
        body["actions"][0]["actionResult"]["success"] = False
        self.assertFalse(validate_response(body, True)["passed"])

    def test_structured_capability_challenge_is_not_mistaken_for_executed_evidence(self):
        body = answer(sources=[], actions=[{"errorCode": "CUSTOMER_ACCOUNT_AUTH_REQUIRED", "customerAccountAuthRequired": True}])
        self.assertTrue(validate_response(body, False)["passed"])
        self.assertFalse(validate_response(body, True)["passed"])

    def test_unsupported_grounding_path_is_a_configuration_failure(self):
        for paths in [[], ["metadata.sourcesCount"], "sources", [{}]]:
            with self.subTest(paths=paths):
                self.assertFalse(validate_response(answer(), True, paths)["checks"]["grounding_contract_valid"])

    def test_invalid_grounding_requirement_cannot_disable_evidence_check(self):
        self.assertFalse(validate_response(answer(sources=[]), "true")["passed"])
        self.assertFalse(validate_response(answer(), None)["passed"])

    def test_ungrounded_clarification_does_not_need_documents(self):
        body = answer(type="CLARIFICATION_REQUIRED", sources=[])
        self.assertTrue(validate_response(body, False)["passed"])
        self.assertFalse(validate_response(body, True)["passed"])

    def test_evaluator_reports_structured_and_grounding_failures(self):
        for payload, category in [
            (answer(success=False), "structured_runtime_failure"),
            (answer(sources=[]), "required_grounding_missing"),
        ]:
            with self.subTest(category=category), patch.object(EVALUATOR, "post_json", return_value=(200, payload)):
                result = EVALUATOR.evaluate_query(
                    "https://bridge.example", "fixture.myshopify.com",
                    {"queryId": "grounding", "query": "Show catalog", "groundingRequired": True},
                    [], [], 3, "fixture",
                )
                self.assertFalse(result["passed"])
                self.assertEqual(category, result["failureCategory"])

    def test_evaluator_honors_query_specific_grounding_declaration(self):
        with patch.object(EVALUATOR, "post_json", return_value=(200, answer())):
            result = EVALUATOR.evaluate_query(
                "https://bridge.example", "fixture.myshopify.com",
                {"queryId": "specific-path", "query": "Show catalog", "groundingRequired": True, "groundingEvidencePaths": ["ragResponse.documents"]},
                [], [], 3, "fixture", ["sources"],
            )
            self.assertFalse(result["passed"])
            self.assertEqual("required_grounding_missing", result["failureCategory"])

    def test_contract_cli_rejects_structured_error_without_printing_answer(self):
        result = subprocess.run(
            [sys.executable, str(SCRIPTS / "shopify_response_contract.py")],
            input=json.dumps(answer(success=False, answer="Do not print this response.")),
            text=True, capture_output=True, timeout=5,
        )
        self.assertEqual(1, result.returncode)
        self.assertNotIn("Do not print", result.stdout + result.stderr)
        self.assertFalse(json.loads(result.stdout)["passed"])

    def test_review_kit_contains_the_verifiers_contract_dependency(self):
        with tempfile.TemporaryDirectory(prefix="shopify-review-kit-test-") as directory:
            result = subprocess.run(
                ["bash", str(SCRIPTS / "build-shopify-companion-review-kit.sh"), directory],
                capture_output=True, text=True, timeout=10,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            contract = Path(directory) / "scripts/shopify_response_contract.py"
            result = subprocess.run(
                [sys.executable, str(contract), "--grounding-required"],
                input=json.dumps(answer()), capture_output=True, text=True, timeout=5,
            )
            self.assertEqual(0, result.returncode, result.stderr)


class LiveVerifierTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.query_response = answer()
        cls.poc_response = answer()

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_args):
                pass

            def do_GET(self):
                body = {
                    "available": True, "billingTier": "ELITE", "chatFallbackEnabled": True,
                    "deploymentId": "dep-fixture", "bridgeQueryUrl": "/query", "bridgeSuggestionsUrl": "/suggestions",
                    "enabledSurfaces": ["max-mode"], "allowedConversationModes": ["executor"],
                }
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps(body).encode())

            def do_POST(self):
                self.rfile.read(int(self.headers.get("Content-Length", "0")))
                body = cls.poc_response if "/poc-widget/" in self.path else cls.query_response
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps(body).encode())

        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.worker = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.worker.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.worker.join(timeout=3)

    def run_verifier(self, query_response, *, poc_response=None, grounding_required="true"):
        type(self).query_response = query_response
        type(self).poc_response = poc_response or answer()
        origin = f"http://127.0.0.1:{self.server.server_port}"
        environment = {
            **os.environ,
            "SHOPIFY_BRIDGE_BASE_URL": origin, "SHOP_DOMAIN": "fixture.myshopify.com",
            "EXPECT_ENABLED_SURFACES": "max-mode", "EXPECT_ALLOWED_CONVERSATION_MODES": "executor",
            "EXPECT_BILLING_TIER": "ELITE", "EXPECT_CHAT_FALLBACK_ENABLED": "true",
            "MAX_WIDGET_GROUNDING_REQUIRED": grounding_required,
            "VERIFY_BROWSER": "false", "VERIFY_POC_AUTH_PATHS": "true" if poc_response is not None else "false",
            "PLATFORM_BASE_URL": origin, "PLATFORM_API_KEY": "fixture-only",
            "PLATFORM_SESSION_COOKIE_JAR": "",
            "MAX_WIDGET_AUTH_PATHS": "PLATFORM_PRIVATE", "MAX_WIDGET_AUTH_PATH_STRICT": "true",
        }
        return subprocess.run(
            ["bash", str(SCRIPTS / "verify-shopify-companion-max-widget-live.sh")],
            env=environment, text=True, capture_output=True, timeout=20,
        )

    def test_live_verifier_passes_grounded_answer(self):
        result = self.run_verifier(answer())
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_live_verifier_fails_http_200_error(self):
        result = self.run_verifier(answer(success=False, type="ERROR"))
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("live verification passed", result.stdout)

    def test_live_verifier_requires_grounding_for_product_recommendation(self):
        self.assertNotEqual(0, self.run_verifier(answer(sources=[])).returncode)

    def test_explicit_non_grounding_probe_still_requires_structured_success(self):
        self.assertEqual(0, self.run_verifier(answer(type="CLARIFICATION_REQUIRED", sources=[]), grounding_required="false").returncode)
        self.assertNotEqual(0, self.run_verifier(answer(success=False, sources=[]), grounding_required="false").returncode)

    def test_invalid_grounding_flag_fails_configuration(self):
        self.assertEqual(2, self.run_verifier(answer(), grounding_required="invalid").returncode)

    def test_poc_route_cannot_pass_structured_failure(self):
        self.assertNotEqual(0, self.run_verifier(answer(), poc_response=answer(success=False)).returncode)


if __name__ == "__main__":
    unittest.main()
