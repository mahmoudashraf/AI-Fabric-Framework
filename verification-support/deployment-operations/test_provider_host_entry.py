import importlib.util
from pathlib import Path
import shlex
import unittest

spec = importlib.util.spec_from_file_location("provider_host_entry", Path(__file__).with_name("provider-host-entry.py"))
entry = importlib.util.module_from_spec(spec)
spec.loader.exec_module(entry)


class HostCommandBoundaryTest(unittest.TestCase):
    def test_exact_native_readback_and_reclamation_commands(self):
        for command in [
            "docker ps --quiet --filter label=coolify.applicationUuid=abc123",
            "docker ps -a --quiet --filter label=coolify.databaseUuid=abc123",
            "docker ps --quiet --filter label=coolify.applicationId --filter label=com.docker.compose.project=abc123",
            "docker ps -a --quiet --filter label=coolify.applicationId --filter name=abc123",
            "docker volume ls --quiet --filter 'name=^volume_123$'",
            "docker volume rm -f volume_123",
            "test -d /var/lib/example/data",
        ]:
            with self.subTest(command=command):
                self.assertTrue(entry.allowed(shlex.split(command)))

    def test_no_shell_container_execution_global_cleanup_or_command_injection(self):
        for command in [
            "sh -c id", "id", "sudo docker ps", "docker ps", "docker ps --all",
            "docker system prune -a", "docker volume prune -f", "docker run alpine id",
            "docker exec container sh", "docker volume rm -f --help", "docker volume rm -f other volume",
            "docker volume rm -f 'volume;id'", "docker volume ls --quiet --filter 'name=.*'",
            "docker ps --quiet --filter 'label=coolify.applicationUuid=.*'",
            "docker ps --quiet --filter label=coolify.applicationUuid=abc123 --format '{{json .}}'",
            "test -d /etc/../root", "test -d //root", "test -d /root/", "",
        ]:
            with self.subTest(command=command):
                self.assertFalse(entry.allowed(shlex.split(command)))


if __name__ == "__main__":
    unittest.main()
