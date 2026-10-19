from contextlib import nullcontext, redirect_stderr, redirect_stdout
import io
import os
from pathlib import Path
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


if __name__ == "__main__":
    unittest.main()
