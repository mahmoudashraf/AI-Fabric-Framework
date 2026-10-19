#!/usr/bin/python3
"""Read-only, fixed-command host metadata verifier. No operation manifests or repairs."""

import contextlib
import json
import os
from pathlib import Path
import posixpath
import re
import selectors
import signal
import stat
import subprocess
import sys
import time

MAX_REQUEST_BYTES = 1024
MAX_OUTPUT_BYTES = 4096
REQUEST_SECONDS = 8
PROCESS_SECONDS = 3
TRUSTED_DATA_ROOT = "/data/coolify"
HOST_POLICY_PATH = "/etc/loomai-retirement-read-only/host-policy.json"
RESOURCE_UUID = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]{0,127}\Z", re.ASCII)
VOLUME_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,127}\Z", re.ASCII)
ABSOLUTE_PATH = re.compile(r"/[A-Za-z0-9._/-]+\Z", re.ASCII)
CONTAINER_ID = re.compile(r"[0-9a-f]{12,64}\Z", re.ASCII)


class Denied(Exception):
    pass


def require(condition):
    if not condition:
        raise Denied()


def matches(pattern, value):
    return isinstance(value, str) and pattern.fullmatch(value) is not None


def retained_path_allowed(path):
    return (matches(ABSOLUTE_PATH, path) and path.startswith(TRUSTED_DATA_ROOT + "/")
            and posixpath.normpath(path) == path)


def parse_command(raw):
    require(isinstance(raw, bytes) and len(raw) <= MAX_REQUEST_BYTES + 1)
    if raw.endswith(b"\n"):
        raw = raw[:-1]
    require(0 < len(raw) <= MAX_REQUEST_BYTES and all(32 <= value <= 126 for value in raw))
    command = raw.decode("ascii").split(" ")
    require(all(command))
    if len(command) == 3 and command[:2] == ["test", "-d"]:
        require(retained_path_allowed(command[2]))
        return "directory", command[2], None
    ps_prefix = ["docker", "ps", "-a", "--quiet", "--filter"]
    if len(command) == 6 and command[:5] == ps_prefix:
        for kind in ("application", "database"):
            prefix = "label=coolify." + kind + "Uuid="
            if command[5].startswith(prefix) and matches(RESOURCE_UUID, command[5][len(prefix):]):
                return "containers", command[1:], None
    if len(command) == 8 and command[:5] == ps_prefix and command[6] == "--filter":
        owner, identity = command[5], command[7]
        compose_prefix = "label=com.docker.compose.project="
        if owner in ("label=coolify.applicationId", "label=coolify.databaseId"):
            if identity.startswith(compose_prefix) and matches(RESOURCE_UUID, identity[len(compose_prefix):]):
                return "containers", command[1:], None
        if owner == "label=coolify.applicationId" and identity.startswith("name="):
            if matches(RESOURCE_UUID, identity[len("name="):]):
                return "containers", command[1:], None
    volume_prefix = ["docker", "volume", "ls", "--quiet", "--filter"]
    if len(command) == 6 and command[:5] == volume_prefix:
        value = command[5]
        require(value.startswith("name=^") and value.endswith("$"))
        name = value[6:-1]
        require(matches(VOLUME_NAME, name))
        # Docker interprets name filters as regexes; exact names containing dots must stay exact.
        return "volume", ["volume", "ls", "--quiet", "--filter", "name=^" + re.escape(name) + "$"], name
    raise Denied()


def check_trusted_directory(info, expected_uid=0):
    require(stat.S_ISDIR(info.st_mode) and info.st_uid == expected_uid and not info.st_mode & 0o022)


def load_host_policy():
    for parent in Path(HOST_POLICY_PATH).parents:
        check_trusted_directory(os.lstat(parent))
    flags = os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC
    descriptor = os.open(HOST_POLICY_PATH, flags)
    try:
        info = os.fstat(descriptor)
        require(stat.S_ISREG(info.st_mode) and info.st_uid == 0 and info.st_nlink == 1
                and stat.S_IMODE(info.st_mode) == 0o600 and 0 < info.st_size <= 4096)
        raw = os.read(descriptor, 4097)
        require(len(raw) == info.st_size)
    finally:
        os.close(descriptor)

    def unique_fields(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result)
            result[key] = value
        return result

    policy = json.loads(raw.decode("ascii"), object_pairs_hook=unique_fields)
    require(isinstance(policy, dict) and set(policy) == {"schemaVersion", "dataRootOwnerUid"}
            and policy["schemaVersion"] == "loomai-retirement-read-only-host/v1"
            and type(policy["dataRootOwnerUid"]) is int
            and 0 <= policy["dataRootOwnerUid"] <= 2147483647)
    return policy


def directory_present(path, data_root_owner_uid=0):
    require(retained_path_allowed(path))
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC
    descriptor = os.open("/", flags)
    base_depth = len(Path(TRUSTED_DATA_ROOT).parts) - 1
    try:
        check_trusted_directory(os.fstat(descriptor))
        for index, component in enumerate(Path(path).parts[1:]):
            try:
                info = os.stat(component, dir_fd=descriptor, follow_symlinks=False)
            except FileNotFoundError:
                require(index >= base_depth)
                return False
            require(not stat.S_ISLNK(info.st_mode))
            if not stat.S_ISDIR(info.st_mode):
                require(index >= base_depth)
                return False
            child = os.open(component, flags, dir_fd=descriptor)
            try:
                if index < base_depth:
                    check_trusted_directory(os.fstat(child),
                                            data_root_owner_uid if index == base_depth - 1 else 0)
            except Exception:
                os.close(child)
                raise
            os.close(descriptor)
            descriptor = child
        return True
    finally:
        os.close(descriptor)


class LocalDocker:
    def __init__(self):
        self.deadline = time.monotonic() + REQUEST_SECONDS

    def __call__(self, arguments):
        require(self.deadline > time.monotonic())
        invocation = ["/usr/bin/docker", "--config", "/etc/loomai-retirement-read-only/docker",
                      "--host", "unix:///var/run/docker.sock"] + arguments
        process = subprocess.Popen(invocation, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                   stderr=subprocess.DEVNULL, close_fds=True, start_new_session=True,
                                   env={"PATH": "/usr/bin:/bin", "HOME": "/", "LC_ALL": "C"})
        output = bytearray()
        deadline = min(self.deadline, time.monotonic() + PROCESS_SECONDS)
        try:
            with selectors.DefaultSelector() as selector:
                selector.register(process.stdout, selectors.EVENT_READ)
                while selector.get_map():
                    remaining = deadline - time.monotonic()
                    require(remaining > 0)
                    events = selector.select(remaining)
                    require(events)
                    for key, _ in events:
                        chunk = os.read(key.fileobj.fileno(), 8192)
                        if not chunk:
                            selector.unregister(key.fileobj)
                            continue
                        output.extend(chunk)
                        require(len(output) <= MAX_OUTPUT_BYTES)
            require(deadline > time.monotonic())
            require(process.wait(timeout=deadline - time.monotonic()) == 0)
            try:
                return output.decode("ascii", errors="strict")
            except UnicodeError:
                raise Denied() from None
        finally:
            if process.poll() is None:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
            process.wait()
            process.stdout.close()


def execute(raw, docker, directory_check=directory_present):
    kind, arguments, name = parse_command(raw)
    if kind == "directory":
        return (0 if directory_check(arguments) else 1), ""
    output = docker(arguments)
    require(isinstance(output, str) and len(output.encode("ascii")) <= MAX_OUTPUT_BYTES)
    rows = output.splitlines()
    if kind == "containers":
        require(all(matches(CONTAINER_ID, row) for row in rows))
        result = "\n".join(rows)
    else:
        require(rows == [] or rows == [name])
        result = name if rows else ""
    require(len(result.encode("ascii")) + (1 if result else 0) <= MAX_OUTPUT_BYTES)
    return 0, result


@contextlib.contextmanager
def bounded_request():
    def timeout(_signal, _frame):
        raise Denied()
    previous = signal.signal(signal.SIGALRM, timeout)
    signal.setitimer(signal.ITIMER_REAL, REQUEST_SECONDS)
    try:
        yield
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        signal.signal(signal.SIGALRM, previous)


def main():
    try:
        require(len(sys.argv) == 1 and os.geteuid() == 0)
        with bounded_request():
            policy = load_host_policy()
            raw = sys.stdin.buffer.read(MAX_REQUEST_BYTES + 2)
            code, output = execute(raw, LocalDocker(),
                                   lambda path: directory_present(path, policy["dataRootOwnerUid"]))
            if output:
                sys.stdout.write(output + "\n")
            return code
    except Exception:
        sys.stderr.write("RETIREMENT_READ_ONLY_DENIED\n")
        return 70


if __name__ == "__main__":
    sys.exit(main())
