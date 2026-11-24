"""Private bounded JSON-lines worker. Inference belongs to the parent runtime, never provider SDKs."""

import asyncio
import contextlib
import importlib.metadata
import json
import math
import os
import sys

MAX_LINE_BYTES = 1_048_576
MAX_CALLS = 24
ENGINE = "ragas-0.4.4-v1"


def deny_network(event, _args):
    if event in {"socket.connect", "socket.connect_ex", "socket.getaddrinfo", "socket.bind"}:
        raise PermissionError("Worker network access is disabled")


def restrict_linux_network():
    if sys.platform != "linux":
        return
    import ctypes
    import errno

    class Argument(ctypes.Structure):
        _fields_ = [("arg", ctypes.c_uint), ("op", ctypes.c_int),
                    ("datum_a", ctypes.c_uint64), ("datum_b", ctypes.c_uint64)]

    library = ctypes.CDLL("libseccomp.so.2", use_errno=True)
    library.seccomp_init.argtypes = [ctypes.c_uint32]
    library.seccomp_init.restype = ctypes.c_void_p
    library.seccomp_syscall_resolve_name.argtypes = [ctypes.c_char_p]
    library.seccomp_syscall_resolve_name.restype = ctypes.c_int
    library.seccomp_rule_add_array.argtypes = [ctypes.c_void_p, ctypes.c_uint32, ctypes.c_int,
                                               ctypes.c_uint, ctypes.POINTER(Argument)]
    library.seccomp_rule_add_array.restype = ctypes.c_int
    library.seccomp_load.argtypes = [ctypes.c_void_p]
    library.seccomp_load.restype = ctypes.c_int
    library.seccomp_release.argtypes = [ctypes.c_void_p]
    context = library.seccomp_init(0x7FFF0000)  # SCMP_ACT_ALLOW; rules deny native Internet socket access.
    if not context:
        raise RuntimeError("Worker network isolation unavailable")
    try:
        action = 0x00050000 | errno.EPERM
        for name, arguments in ((b"connect", []), (b"socket", [Argument(0, 4, 2, 0)]),
                                (b"socket", [Argument(0, 4, 10, 0)])):
            syscall = library.seccomp_syscall_resolve_name(name)
            values = (Argument * len(arguments))(*arguments) if arguments else None
            if syscall < 0 or library.seccomp_rule_add_array(context, action, syscall, len(arguments), values) != 0:
                raise RuntimeError("Worker network isolation unavailable")
        # libseccomp sets no_new_privs before loading; child threads/processes inherit this filter.
        if library.seccomp_load(context) != 0:
            raise RuntimeError("Worker network isolation unavailable")
    finally:
        library.seccomp_release(context)


def configure_process():
    os.environ["RAGAS_DO_NOT_TRACK"] = "true"
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["HF_DATASETS_OFFLINE"] = "1"
    restrict_linux_network()
    sys.addaudithook(deny_network)
    if sys.platform == "linux":
        import resource
        resource.setrlimit(resource.RLIMIT_CPU, (110, 110))
        resource.setrlimit(resource.RLIMIT_NOFILE, (64, 64))
        resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
        resource.setrlimit(resource.RLIMIT_AS, (2_147_483_648, 2_147_483_648))


def receive():
    raw = sys.stdin.buffer.readline(MAX_LINE_BYTES + 1)
    if not raw or len(raw) > MAX_LINE_BYTES or not raw.endswith(b"\n"):
        raise ValueError("Invalid protocol frame")
    return json.loads(raw)


def send(value):
    raw = json.dumps(value, allow_nan=False, separators=(",", ":"))
    if len(raw.encode("utf-8")) > MAX_LINE_BYTES:
        raise ValueError("Protocol frame exceeds its bound")
    sys.stdout.write(raw + "\n")
    sys.stdout.flush()


class Bridge:
    def __init__(self):
        self.calls = 0

    def invoke(self, kind, **payload):
        self.calls += 1
        if self.calls > MAX_CALLS:
            raise ValueError("Metric call allowance exhausted")
        send({"type": kind, "id": self.calls, **payload})
        reply = receive()
        if reply.get("type") != "result" or reply.get("id") != self.calls:
            raise ValueError("Inference reply does not match the requested operation")
        return reply["value"]


async def evaluate(sample, bridge):
    # Imports may initialize logging; stdout is exclusively the typed protocol.
    with contextlib.redirect_stdout(sys.stderr):
        from ragas.llms.base import InstructorBaseRagasLLM
        from ragas.embeddings.base import BaseRagasEmbedding
        from ragas.metrics.collections import Faithfulness, AnswerRelevancy, ContextPrecision, ContextRecall, FactualCorrectness

    if importlib.metadata.version("ragas") != "0.4.4":
        raise ValueError("Unqualified Ragas version")

    class RuntimeLLM(InstructorBaseRagasLLM):
        def generate(self, prompt, response_model):
            value = bridge.invoke("generate", prompt=prompt, schema=response_model.model_json_schema())
            return response_model.model_validate(value)

        async def agenerate(self, prompt, response_model):
            return self.generate(prompt, response_model)

    class RuntimeEmbeddings(BaseRagasEmbedding):
        def embed_text(self, text, **_kwargs):
            return bridge.invoke("embed", text=text)

        async def aembed_text(self, text, **kwargs):
            return self.embed_text(text, **kwargs)

        async def aembed_texts(self, texts, **kwargs):
            # Serial calls retain deterministic accounting and one parent request at a time.
            return [self.embed_text(text, **kwargs) for text in texts]

    llm, embeddings = RuntimeLLM(), RuntimeEmbeddings()
    question, answer = sample["question"], sample["answer"]
    contexts = [item["content"] for item in sample["evidence"]]
    reference = sample.get("referenceAnswer") or ""
    metrics = {}

    async def score(name, operation, definition):
        result = await operation
        value = float(result.value)
        if not math.isfinite(value):
            metrics[name] = {"status": "NOT_SCORED", "score": None, "explanation": definition + "; metric returned no finite score."}
        elif value < 0 or value > 1:
            raise ValueError("Metric score is outside its declared range")
        else:
            metrics[name] = {"status": "SCORED", "score": value, "explanation": definition}

    if answer.strip() and contexts:
        await score("faithfulness", Faithfulness(llm=llm).ascore(user_input=question, response=answer, retrieved_contexts=contexts),
                    "Ragas 0.4.4 Faithfulness: supported atomic claims divided by generated claims.")
    else:
        metrics["faithfulness"] = {"status": "NOT_SCORED", "score": None, "explanation": "An answer and retrieved excerpts are required."}
    if answer.strip():
        await score("relevance", AnswerRelevancy(llm=llm, embeddings=embeddings, strictness=3).ascore(user_input=question, response=answer),
                    "Ragas 0.4.4 AnswerRelevancy, strictness 3, using the configured runtime embedding provider.")
    else:
        metrics["relevance"] = {"status": "NOT_SCORED", "score": None, "explanation": "An answer is required."}
    if reference.strip() and contexts:
        await score("contextPrecision", ContextPrecision(llm=llm).ascore(user_input=question, reference=reference, retrieved_contexts=contexts),
                    "Ragas 0.4.4 ContextPrecision against the supplied reference; reference review remains a caller responsibility.")
        await score("contextRecall", ContextRecall(llm=llm).ascore(user_input=question, retrieved_contexts=contexts, reference=reference),
                    "Ragas 0.4.4 ContextRecall against the supplied reference, not a completeness guarantee without one.")
    else:
        for name in ("contextPrecision", "contextRecall"):
            metrics[name] = {"status": "NOT_SCORED", "score": None, "explanation": "A reference answer and retrieved excerpts are required."}
    if answer.strip() and reference.strip():
        await score("referenceAgreement", FactualCorrectness(llm=llm, mode="f1", atomicity="low", coverage="low")
                    .ascore(response=answer, reference=reference),
                    "Ragas 0.4.4 FactualCorrectness F1, low atomicity/coverage, against the supplied reference; not independent truth verification.")
    else:
        metrics["referenceAgreement"] = {"status": "NOT_SCORED", "score": None, "explanation": "An answer and reference are required."}
    return {"providerId": "ragas", "rubricVersion": ENGINE, "metrics": metrics}


def main():
    configure_process()
    command = receive()
    if command.get("type") != "evaluate" or set(command) != {"type", "sample"}:
        raise ValueError("Only evaluation is supported")
    bridge = Bridge()
    result = asyncio.run(evaluate(command["sample"], bridge))
    send({"type": "complete", "result": result, "modelCalls": bridge.calls})


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # Exceptions can contain excerpts or provider response text. Neither belongs in protocol diagnostics.
        send({"type": "failed", "code": "RAGAS_EVALUATION_FAILED"})
        sys.exit(1)
