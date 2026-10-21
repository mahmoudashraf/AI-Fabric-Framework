from contextlib import nullcontext, redirect_stderr, redirect_stdout
import io
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock

import retirement_host_read_only_adapter as adapter

ROOT = Path(__file__).resolve().parent
CONTAINER_COMMANDS = [
    "docker ps -a --quiet --filter label=coolify.applicationUuid=app-uuid",
    "docker ps -a --quiet --filter label=coolify.databaseUuid=db-uuid",
    "docker ps -a --quiet --filter label=coolify.applicationId --filter label=com.docker.compose.project=app-uuid",
    "docker ps -a --quiet --filter label=coolify.databaseId --filter label=com.docker.compose.project=db-uuid",
    "docker ps -a --quiet --filter label=coolify.applicationId --filter name=app-uuid",
]
VOLUME_COMMAND = b"docker volume ls --quiet --filter name=^runtime.data$"


class ColdStoreCountTests(unittest.TestCase):
    def test_exact_java_script_and_shell_quoted_command_contract(self):
        source = (ROOT.parents[3] / "Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RuntimeColdStoreReadback.java").read_text()
        expression = source.split("private static final String SCRIPT = ", 1)[1].split("\n    private final ", 1)[0]
        literal = r'"(?:[^"\\]|\\.)*"'
        literals = re.findall(literal, expression)
        self.assertRegex(re.sub(literal, '""', expression), r'^\s*""(?:\s*\+\s*""\s*)*;\s*$')
        java_script = "".join(json.loads(value) for value in literals)
        self.assertEqual(adapter.COLD_STORE_SCRIPT.encode("ascii"), java_script.encode("ascii"))
        quoted = "'" + java_script.replace("'", "'\"'\"'") + "'"
        expected = "docker exec db-uuid sh -lc " + quoted
        self.assertEqual(adapter.cold_store_command("db-uuid"), expected)
        result = subprocess.run(["/bin/sh", "-c", "printf '%s' " + quoted], check=True,
                                capture_output=True, timeout=3)
        self.assertEqual(result.stdout, java_script.encode("ascii"))
        self.assertLessEqual(len(adapter.cold_store_command("a" * 128).encode()), adapter.MAX_REQUEST_BYTES)

    def test_zero_and_positive_metadata_return_only_the_four_totals(self):
        for count in (0, 7, 9223372036854775807):
            counts = dict.fromkeys(adapter.COLD_STORE_TABLES, 0)
            counts["ai_specialist_execution"] = count
            docker = mock.Mock(return_value="\n" + json.dumps(counts) + "\n")
            code, output = adapter.execute(adapter.cold_store_command("db-uuid").encode(), docker)
            self.assertEqual(code, 0)
            self.assertEqual(json.loads(output), counts)
            docker.assert_called_once_with(["exec", "db-uuid", "sh", "-lc", adapter.COLD_STORE_SCRIPT])

    def test_changed_script_shell_uuid_or_extra_arguments_never_reach_docker(self):
        exact = adapter.cold_store_command("db-uuid")
        commands = [exact.replace("count(*)", "count(id)", 1), exact.replace("read_only=on", "read_only=off"),
                    exact.replace(" sh -lc ", " bash -lc "), exact.replace(" sh -lc ", " sh -c "),
                    exact + " extra", exact + "; id", exact.replace("docker exec ", "docker exec --privileged ", 1),
                    exact.replace("db-uuid", "db;id", 1), exact.replace("db-uuid", "$(id)", 1),
                    exact.replace("db-uuid", "db/path", 1), exact.replace("db-uuid", "-db", 1),
                    exact.replace("db-uuid", "a" * 129, 1), exact.replace("docker exec ", "docker  exec ", 1),
                    "docker exec db-uuid sh -lc 'psql -c SELECT'", "docker exec db-uuid psql -c SELECT"]
        for command in commands:
            docker = mock.Mock()
            with self.subTest(command=command), self.assertRaises(adapter.Denied):
                adapter.execute(command.encode(), docker)
            docker.assert_not_called()

    def test_malformed_extra_duplicate_and_count_types_fail_closed(self):
        empty = dict.fromkeys(adapter.COLD_STORE_TABLES, 0)
        valid = json.dumps(empty)
        outputs = ["", "not-json", "null", "[]", "{}", valid + "{}", "warning\n" + valid,
                   valid[:-1] + ',"extra":0}', valid[:-1] + ',"chat_sessions":0}', "x" * 4097]
        for value in (True, False, None, -1, 0.0, "0", [], {}, 9223372036854775808):
            outputs.append(json.dumps(dict(empty, chat_sessions=value)))
        for table in adapter.COLD_STORE_TABLES:
            missing = dict(empty)
            del missing[table]
            outputs.append(json.dumps(missing))
        for output in outputs:
            with self.subTest(output=output[:100]), self.assertRaises(adapter.Denied):
                adapter.execute(adapter.cold_store_command("db-uuid").encode(), mock.Mock(return_value=output))

    def test_process_failure_is_never_an_empty_count_result(self):
        with self.assertRaises(adapter.Denied):
            adapter.execute(adapter.cold_store_command("db-uuid").encode(), mock.Mock(side_effect=adapter.Denied()))


class ReadOnlyCommandTests(unittest.TestCase):
    def test_all_five_existing_container_read_forms_return_actual_ids_or_absence(self):
        for command in CONTAINER_COMMANDS:
            for output in ["", "abcdef123456\n", "abcdef123456\n123456abcdef\n"]:
                docker = mock.Mock(return_value=output)
                with self.subTest(command=command, present=bool(output)):
                    self.assertEqual(adapter.execute((command + "\n").encode(), docker), (0, output.rstrip("\n")))
                    docker.assert_called_once_with(command.split(" ")[1:])

    def test_volume_read_is_exact_and_regex_dots_are_escaped(self):
        for value in ["", "runtime.data\n"]:
            docker = mock.Mock(return_value=value)
            self.assertEqual(adapter.execute(VOLUME_COMMAND, docker), (0, value.rstrip("\n")))
            docker.assert_called_once_with(["volume", "ls", "--quiet", "--filter", r"name=^runtime\.data$"])

    def test_mutation_shell_unscoped_and_alternate_commands_are_all_rejected(self):
        commands = [b"docker volume rm -f runtime.data", b"docker volume rm runtime.data",
                    b"docker volume prune -f", b"docker system prune -a -f", b"docker container prune -f",
                    b"docker stop abcdef123456", b"docker rm abcdef123456", b"docker kill abcdef123456",
                    b"docker exec abcdef123456 id", b"docker inspect abcdef123456", b"docker logs abcdef123456",
                    b"docker ps -a", b"docker volume ls --quiet", b"docker ps -a --quiet --filter name=app-uuid",
                    b"docker --host tcp://remote ps -a", b"sh -c id", b"sudo docker ps -a", b"id",
                    b"docker volume ls --quiet --filter name=runtime", b"docker volume ls --quiet --filter name=^.*$",
                    b"docker ps -a --quiet --filter label=coolify.applicationUuid=app;id",
                    b"docker ps -a --quiet --filter label=coolify.databaseId --filter name=db-uuid",
                    b"docker ps -a --quiet --filter label=coolify.applicationId --filter name=^app-uuid",
                    b"docker volume ls --quiet --filter name=^runtime.data$;id"]
        for command in commands:
            docker = mock.Mock()
            with self.subTest(command=repr(command)), self.assertRaises(adapter.Denied):
                adapter.execute(command, docker)
            docker.assert_not_called()

    def test_ascii_and_single_command_whitespace_boundary_is_strict(self):
        for command in [b"", b"\n", b" " + VOLUME_COMMAND, VOLUME_COMMAND + b" ",
                        VOLUME_COMMAND.replace(b" ", b"  ", 1), VOLUME_COMMAND.replace(b" ", b"\t", 1),
                        VOLUME_COMMAND + b"\nid", VOLUME_COMMAND + b"\n\n", VOLUME_COMMAND + b"\x00",
                        VOLUME_COMMAND + b"\r\n", VOLUME_COMMAND + b"\xff", b"x" * 1025]:
            with self.subTest(command=repr(command)), self.assertRaises(adapter.Denied):
                adapter.parse_command(command)

    def test_unsafe_resource_names_are_rejected(self):
        for value in ["", "app.uuid", "-app", "app/uuid", "app$(id)", "a" * 129]:
            with self.subTest(value=value), self.assertRaises(adapter.Denied):
                adapter.parse_command(("docker ps -a --quiet --filter label=coolify.applicationUuid=" + value).encode())

    def test_only_strict_descendant_retained_paths_are_accepted(self):
        docker, checker = mock.Mock(), mock.Mock(return_value=True)
        command = b"test -d /data/coolify/applications/app-uuid/documents"
        self.assertEqual(adapter.execute(command, docker, checker), (0, ""))
        checker.assert_called_once_with("/data/coolify/applications/app-uuid/documents")
        checker.return_value = False
        self.assertEqual(adapter.execute(command, docker, checker), (1, ""))
        docker.assert_not_called()
        for path in ["/", "/data/coolify", "/data/coolify-other/a", "/data/coolify/../root",
                     "/data/coolify/a/../b", "/data/coolify/a//b", "/data/coolify/a/./b",
                     "/data/coolify/a/", "/srv/loomai/documents", "/data/coolify/a b", "//data/coolify/a"]:
            with self.subTest(path=path), self.assertRaises(adapter.Denied):
                adapter.parse_command(("test -d " + path).encode())

    def test_failed_malformed_or_oversized_docker_output_is_not_absence(self):
        for output in ["not-an-id\n", "\n", " \n", "abcdef123456\n\n", "x" * 4097]:
            with self.subTest(output_size=len(output)), self.assertRaises(adapter.Denied):
                adapter.execute(CONTAINER_COMMANDS[0].encode(), mock.Mock(return_value=output))
        for output in ["runtime.data\nother\n", "runtimeXdata\n", "\n", "runtime.data \n"]:
            with self.assertRaises(adapter.Denied):
                adapter.execute(VOLUME_COMMAND, mock.Mock(return_value=output))
        with self.assertRaises(adapter.Denied):
            adapter.execute(VOLUME_COMMAND, mock.Mock(side_effect=adapter.Denied()))

    def test_docker_presence_is_never_hidden_or_repaired(self):
        docker = mock.Mock(return_value="runtime.data\n")
        self.assertEqual(adapter.execute(VOLUME_COMMAND, docker), (0, "runtime.data"))
        with self.assertRaises(adapter.Denied):
            adapter.execute(b"docker volume rm -f runtime.data", docker)
        self.assertEqual(docker.call_count, 1)


class DirectoryTests(unittest.TestCase):
    def test_directory_descriptors_reject_symlinks_and_missing_is_not_present(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as directory:
            base = Path(directory) / "trusted-coolify"
            base.mkdir()
            (base / "documents").mkdir()
            (base / "ordinary-file").touch()
            (base / "alias").symlink_to(base / "documents", target_is_directory=True)
            outside = Path(directory) / "outside"
            outside.mkdir()
            (base / "escape").symlink_to(outside, target_is_directory=True)
            with mock.patch.object(adapter, "TRUSTED_DATA_ROOT", str(base)), \
                    mock.patch.object(adapter, "check_trusted_directory"):
                self.assertTrue(adapter.directory_present(str(base / "documents")))
                self.assertFalse(adapter.directory_present(str(base / "missing")))
                self.assertFalse(adapter.directory_present(str(base / "ordinary-file")))
                for path in [base / "alias", base / "alias" / "child", base / "escape"]:
                    with self.subTest(path=path.name), self.assertRaises(adapter.Denied):
                        adapter.directory_present(str(path))

    def test_trusted_base_symlink_and_symlink_swap_before_open_are_rejected(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as directory:
            base = Path(directory) / "base"
            base.mkdir()
            (base / "documents").mkdir()
            alias = Path(directory) / "alias"
            alias.symlink_to(base, target_is_directory=True)
            with mock.patch.object(adapter, "TRUSTED_DATA_ROOT", str(alias)), \
                    mock.patch.object(adapter, "check_trusted_directory"), self.assertRaises(adapter.Denied):
                adapter.directory_present(str(alias / "documents"))
            real_open = os.open

            def swap_before_open(path, flags, **kwargs):
                if path == "documents":
                    (base / "documents").rename(base / "original-documents")
                    (base / "documents").symlink_to(base / "original-documents", target_is_directory=True)
                return real_open(path, flags, **kwargs)

            with mock.patch.object(adapter, "TRUSTED_DATA_ROOT", str(base)), \
                    mock.patch.object(adapter, "check_trusted_directory"), \
                    mock.patch.object(adapter.os, "open", side_effect=swap_before_open), self.assertRaises(OSError):
                adapter.directory_present(str(base / "documents"))

    def test_trusted_ancestors_must_be_protected_root_owned_directories(self):
        def info(mode, uid=0):
            return types.SimpleNamespace(st_mode=mode, st_uid=uid)
        adapter.check_trusted_directory(info(stat.S_IFDIR | 0o755))
        for value in [info(stat.S_IFDIR | 0o775), info(stat.S_IFDIR | 0o777),
                      info(stat.S_IFDIR | 0o755, 1001), info(stat.S_IFLNK | 0o755), info(stat.S_IFREG | 0o644)]:
            with self.assertRaises(adapter.Denied):
                adapter.check_trusted_directory(value)


class ProcessBoundaryTests(unittest.TestCase):
    def synthetic_process(self, script, seconds=1):
        real_popen = subprocess.Popen
        seen = []

        def fixture(command, **kwargs):
            seen.append((command, kwargs))
            return real_popen([sys.executable, "-I", "-c", script], **kwargs)

        with mock.patch.object(adapter, "REQUEST_SECONDS", seconds):
            docker = adapter.LocalDocker()
        with mock.patch.object(adapter.subprocess, "Popen", side_effect=fixture):
            output = docker(["volume", "ls", "--quiet", "--filter", "name=^fixture$"])
        return output, seen

    def test_fixed_local_daemon_clean_environment_and_no_input(self):
        with mock.patch.dict(os.environ, {"DOCKER_HOST": "tcp://forbidden", "DOCKER_CONFIG": "/forbidden", "DOCKER_CONTEXT": "forbidden"}):
            output, seen = self.synthetic_process("print('fixture')")
        self.assertEqual(output, "fixture\n")
        command, options = seen[0]
        self.assertEqual(command[:5], ["/usr/bin/docker", "--config", "/etc/loomai-retirement-read-only/docker",
                                       "--host", "unix:///var/run/docker.sock"])
        self.assertEqual(options["env"], {"PATH": "/usr/bin:/bin", "HOME": "/", "LC_ALL": "C"})
        self.assertEqual(options["stdin"], subprocess.DEVNULL)
        self.assertEqual(options["stderr"], subprocess.DEVNULL)
        self.assertTrue(options["start_new_session"])

    def test_bounded_output_timeout_exit_and_ascii_errors_fail_closed(self):
        for script, seconds in [("print('x' * 5000)", 1), ("import time; time.sleep(1)", 0.05),
                                ("import sys; print('synthetic-private-error'); sys.exit(1)", 1),
                                ("import sys; sys.stdout.buffer.write(bytes([255]))", 1)]:
            with self.subTest(script=script), self.assertRaises(adapter.Denied):
                self.synthetic_process(script, seconds)

    def test_error_output_is_fixed_and_never_echoes_command_or_diagnostics(self):
        stdout, stderr = io.StringIO(), io.StringIO()
        stdin = types.SimpleNamespace(buffer=io.BytesIO(b"synthetic-private-command\n"))
        with mock.patch.object(sys, "argv", ["adapter"]), mock.patch.object(sys, "stdin", stdin), \
                mock.patch.object(os, "geteuid", return_value=0), \
                mock.patch.object(adapter, "bounded_request", return_value=nullcontext()), \
                redirect_stdout(stdout), redirect_stderr(stderr):
            self.assertEqual(adapter.main(), 70)
        self.assertEqual(stdout.getvalue(), "")
        self.assertEqual(stderr.getvalue(), "RETIREMENT_READ_ONLY_DENIED\n")

    def test_nonroot_and_extra_arguments_are_rejected_before_input_or_process(self):
        for uid, arguments in [(1001, ["adapter"]), (0, ["adapter", "--repair"])]:
            with mock.patch.object(os, "geteuid", return_value=uid), \
                    mock.patch.object(sys, "argv", arguments), \
                    mock.patch.object(adapter, "LocalDocker") as docker, redirect_stderr(io.StringIO()):
                self.assertEqual(adapter.main(), 70)
                docker.assert_not_called()


class HostPolicyTests(unittest.TestCase):
    def load(self, raw, **changes):
        info = types.SimpleNamespace(st_mode=stat.S_IFREG | 0o600, st_uid=0, st_nlink=1, st_size=len(raw))
        for key, value in changes.items():
            setattr(info, key, value)
        parent = types.SimpleNamespace(st_mode=stat.S_IFDIR | 0o750, st_uid=0)
        with mock.patch.object(adapter.os, "lstat", return_value=parent), \
                mock.patch.object(adapter.os, "open", return_value=17) as opened, \
                mock.patch.object(adapter.os, "fstat", return_value=info), \
                mock.patch.object(adapter.os, "read", return_value=raw), \
                mock.patch.object(adapter.os, "close") as closed:
            value = adapter.load_host_policy()
        opened.assert_called_once_with(adapter.HOST_POLICY_PATH, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
        closed.assert_called_once_with(17)
        return value

    def test_explicit_root_or_service_data_owner_is_accepted_without_widening_ancestors(self):
        for uid in [0, 9999]:
            raw = ('{"schemaVersion":"loomai-retirement-read-only-host/v1","dataRootOwnerUid":%d}' % uid).encode()
            self.assertEqual(self.load(raw)["dataRootOwnerUid"], uid)
            info = types.SimpleNamespace(st_mode=stat.S_IFDIR | 0o750, st_uid=uid)
            adapter.check_trusted_directory(info, uid)
            if uid != 0:
                with self.assertRaises(adapter.Denied):
                    adapter.check_trusted_directory(info)

    def test_unsafe_policy_metadata_or_size_fails_closed(self):
        raw = b'{"schemaVersion":"loomai-retirement-read-only-host/v1","dataRootOwnerUid":9999}'
        for changes in [{"st_uid":9999}, {"st_mode":stat.S_IFREG | 0o644}, {"st_mode":stat.S_IFLNK | 0o600},
                        {"st_nlink":2}, {"st_size":0}, {"st_size":4097}, {"st_size":len(raw)+1}]:
            with self.subTest(changes=changes), self.assertRaises(adapter.Denied):
                self.load(raw, **changes)

    def test_untrusted_policy_parent_is_rejected_before_open(self):
        for mode, uid in [(stat.S_IFDIR | 0o777, 0), (stat.S_IFDIR | 0o750, 9999), (stat.S_IFLNK | 0o750, 0)]:
            parent = types.SimpleNamespace(st_mode=mode, st_uid=uid)
            with mock.patch.object(adapter.os, "lstat", return_value=parent), \
                    mock.patch.object(adapter.os, "open") as opened, self.assertRaises(adapter.Denied):
                adapter.load_host_policy()
            opened.assert_not_called()

    def test_duplicate_unknown_boolean_negative_or_wrong_schema_policy_is_rejected(self):
        for raw in [b'{}', b'[]',
                    b'{"schemaVersion":"other","dataRootOwnerUid":9999}',
                    b'{"schemaVersion":"loomai-retirement-read-only-host/v1","dataRootOwnerUid":true}',
                    b'{"schemaVersion":"loomai-retirement-read-only-host/v1","dataRootOwnerUid":-1}',
                    b'{"schemaVersion":"loomai-retirement-read-only-host/v1","dataRootOwnerUid":2147483648}',
                    b'{"schemaVersion":"loomai-retirement-read-only-host/v1","dataRootOwnerUid":9999,"extra":1}',
                    b'{"schemaVersion":"loomai-retirement-read-only-host/v1","dataRootOwnerUid":0,"dataRootOwnerUid":9999}']:
            with self.subTest(raw=raw), self.assertRaises(adapter.Denied):
                self.load(raw)


if __name__ == "__main__":
    unittest.main()
