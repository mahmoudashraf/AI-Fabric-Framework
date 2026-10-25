#!/usr/bin/python3
"""Restricted SSH entry point for core-owned native readback and exact volume reclamation."""

import os
import re
import shlex
import subprocess
import sys

NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
RESOURCE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
PATH = re.compile(r"/[A-Za-z0-9._/-]+")


def allowed(argv):
    if not argv or any(not item for item in argv):
        return False
    if len(argv) == 3 and argv[:2] == ["test", "-d"]:
        path = argv[2]
        return bool(PATH.fullmatch(path) and not path.startswith("//") and os.path.normpath(path) == path)
    if len(argv) == 6 and argv[:5] == ["docker", "volume", "ls", "--quiet", "--filter"]:
        value = argv[5]
        return value.startswith("name=^") and value.endswith("$") and bool(NAME.fullmatch(value[6:-1]))
    if len(argv) == 5 and argv[:4] == ["docker", "volume", "rm", "-f"]:
        return bool(NAME.fullmatch(argv[4]))
    if argv[:4] == ["docker", "ps", "--quiet", "--filter"]:
        return allowed(argv[:2] + ["-a"] + argv[2:])
    if argv[:5] != ["docker", "ps", "-a", "--quiet", "--filter"]:
        return False
    if len(argv) == 6:
        return any(argv[5].startswith(prefix) and RESOURCE.fullmatch(argv[5][len(prefix):])
                   for prefix in ["label=coolify.applicationUuid=", "label=coolify.databaseUuid="])
    if len(argv) != 8 or argv[6] != "--filter":
        return False
    if argv[5] == "label=coolify.applicationId" and argv[7].startswith("name="):
        return bool(RESOURCE.fullmatch(argv[7][5:]))
    prefix = "label=com.docker.compose.project="
    return (argv[5] in ["label=coolify.applicationId", "label=coolify.databaseId"]
            and argv[7].startswith(prefix) and bool(RESOURCE.fullmatch(argv[7][len(prefix):])))


def main():
    try:
        argv = shlex.split(os.environ.get("SSH_ORIGINAL_COMMAND", ""), posix=True)
    except ValueError:
        return 64
    if not allowed(argv):
        return 64
    if argv[0] == "test":
        return 0 if os.path.isdir(argv[2]) else 1
    try:
        # No shell, environment inheritance, Docker socket proxy or arbitrary Docker subcommands.
        result = subprocess.run(["/usr/bin/docker", *argv[1:]], stdin=subprocess.DEVNULL,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=12,
                                env={"PATH": "/usr/bin:/bin", "HOME": "/nonexistent"}, check=False)
        if len(result.stdout) + len(result.stderr) > 4096:
            return 74
        sys.stdout.buffer.write(result.stdout)
        sys.stderr.buffer.write(result.stderr)
        return result.returncode if 0 <= result.returncode <= 255 else 70
    except (OSError, subprocess.TimeoutExpired):
        return 75


if __name__ == "__main__":
    sys.exit(main())
