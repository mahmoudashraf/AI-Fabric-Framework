#!/usr/bin/env python3
"""
Signal Replay – replays captured behavior signals against a running ingestion endpoint.

Examples:
  python scripts/signal-replay.py --source ./samples/signals.jsonl
  python scripts/signal-replay.py --source ./samples/batch.json --batch-size 25 --endpoint http://localhost:8080/api/ai-behavior/signals
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


def load_events(source: Path):
    if not source.exists():
        sys.exit(f"Signal file {source} does not exist")
    text = source.read_text(encoding="utf-8").strip()
    if not text:
        return []
    if source.suffix.lower() in (".jsonl", ".ndjson"):
        return [json.loads(line) for line in text.splitlines() if line.strip()]
    payload = json.loads(text)
    if isinstance(payload, dict):
        return [payload]
    return list(payload)


def post_json(url: str, payload: dict | list):
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST"
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        body = response.read()
        return response.status, body.decode("utf-8")


def replay(events, endpoint: str, batch_size: int, dry_run: bool):
    total = len(events)
    if total == 0:
        print("No signals found to replay")
        return

    print(f"Replaying {total} signals to {endpoint} (batch-size={batch_size})")
    successes = 0
    failures = 0
    for i in range(0, total, batch_size):
        chunk = events[i:i + batch_size]
        path = endpoint if len(chunk) == 1 else f"{endpoint}/batch"
        payload = chunk[0] if len(chunk) == 1 else {"events": chunk}
        if dry_run:
            print(f"[dry-run] Would POST {len(chunk)} signal(s) to {path}")
            continue
        try:
            status, body = post_json(path, payload)
            successes += len(chunk)
            print(f"[{time.strftime('%H:%M:%S')}] Sent {len(chunk)} signal(s) -> {status} {body}")
        except urllib.error.HTTPError as exc:
            failures += len(chunk)
            print(f"HTTP error for chunk starting at index {i}: {exc.code} {exc.read().decode('utf-8')}")
        except Exception as exc:  # pragma: no cover - helper script
            failures += len(chunk)
            print(f"Failed to send chunk starting at index {i}: {exc}")

    print(f"Replay complete: {successes} succeeded, {failures} failed")


def main():
    parser = argparse.ArgumentParser(description="Replay captured behavior signals.")
    parser.add_argument("--source", type=Path, required=True, help="Path to JSON/JSONL/NDJSON signal file")
    parser.add_argument("--endpoint", default="http://localhost:8080/api/ai-behavior/signals",
                        help="Base ingestion endpoint")
    parser.add_argument("--batch-size", type=int, default=1, help="Signals sent per request")
    parser.add_argument("--dry-run", action="store_true", help="Print requests without sending")
    args = parser.parse_args()

    events = load_events(args.source)
    replay(events, args.endpoint.rstrip("/"), max(1, args.batch_size), args.dry_run)


if __name__ == "__main__":
    main()
