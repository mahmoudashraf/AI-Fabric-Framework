package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SshProviderHostCommandRunner implements ProviderHostCommandRunner {
    private static final int MAX_OUTPUT_CHARACTERS = 4096;
    private static final java.util.regex.Pattern SAFE_USERNAME =
        java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_-]{0,31}");
    private static final java.util.regex.Pattern SAFE_ADDRESS =
        java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9.:-]{0,252}");
    private static final java.util.regex.Pattern SAFE_VOLUME =
        java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    private static final java.util.regex.Pattern SAFE_PATH =
        java.util.regex.Pattern.compile("/[A-Za-z0-9._/-]+");
    private static final java.util.regex.Pattern SAFE_RESOURCE_UUID =
        java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");

    private final ProviderHostVolumeReclamationProperties properties;
    private final CustomerSelfServiceProperties customerProperties;
    private final PlatformSecretService secrets;

    public SshProviderHostCommandRunner(ProviderHostVolumeReclamationProperties properties,
                                        CustomerSelfServiceProperties customerProperties,
                                        PlatformSecretService secrets) {
        this.properties = properties;
        this.customerProperties = customerProperties;
        this.secrets = secrets;
    }

    @Override
    public Result run(TargetIntent intent, String expectedServerUuid, List<String> command) {
        if (!properties.enabled()) throw new TargetRetirementFailure("PROVIDER_HOST_VERIFICATION_DISABLED");
        var trusted = customerProperties.trustedServers().get(intent);
        var host = properties.hosts().get(intent);
        if (trusted == null || host == null || blank(trusted.uuid()) || blank(trusted.address())
            || !trusted.uuid().equals(expectedServerUuid) || blank(host.username())
            || !SAFE_USERNAME.matcher(host.username()).matches()
            || !SAFE_ADDRESS.matcher(trusted.address()).matches()
            || host.effectivePort() < 1 || host.effectivePort() > 65535
            || blank(host.privateKeySecretName()) || blank(host.knownHostsSecretName())
            || !safeCommand(command)) {
            throw new TargetRetirementFailure("PROVIDER_HOST_VERIFICATION_CONFIGURATION_INVALID");
        }
        String privateKey = secrets.resolveSecret(host.privateKeySecretName());
        String knownHosts = secrets.resolveSecret(host.knownHostsSecretName());
        if (blank(privateKey) || blank(knownHosts)) {
            throw new TargetRetirementFailure("PROVIDER_HOST_VERIFICATION_CREDENTIAL_MISSING");
        }

        Path directory = null;
        Process process = null;
        try {
            directory = Files.createTempDirectory("loomai-provider-host-");
            setPermissions(directory, "rwx------");
            Path keyFile = directory.resolve("identity");
            Path knownHostsFile = directory.resolve("known_hosts");
            writePrivateKey(keyFile, privateKey);
            Files.writeString(knownHostsFile, knownHosts, StandardCharsets.UTF_8);
            setPermissions(knownHostsFile, "rw-------");

            List<String> invocation = new ArrayList<>(List.of(
                "ssh", "-F", "/dev/null", "-i", keyFile.toString(),
                "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes",
                "-o", "StrictHostKeyChecking=yes", "-o", "PasswordAuthentication=no",
                "-o", "KbdInteractiveAuthentication=no", "-o", "ConnectTimeout=10",
                "-o", "UserKnownHostsFile=" + knownHostsFile,
                "-p", Integer.toString(host.effectivePort()),
                "--", host.username() + "@" + trusted.address()
            ));
            invocation.addAll(command);
            ProcessBuilder builder = new ProcessBuilder(invocation).redirectErrorStream(true);
            process = builder.start();
            boolean finished = process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TargetRetirementFailure("PROVIDER_HOST_VERIFICATION_TIMEOUT");
            }
            String output = new String(process.getInputStream().readNBytes(MAX_OUTPUT_CHARACTERS + 1), StandardCharsets.UTF_8);
            if (output.length() > MAX_OUTPUT_CHARACTERS) {
                throw new TargetRetirementFailure("PROVIDER_HOST_VERIFICATION_OUTPUT_INVALID");
            }
            return new Result(process.exitValue(), output.strip());
        } catch (TargetRetirementFailure error) {
            throw error;
        } catch (IOException error) {
            throw new TargetRetirementFailure("PROVIDER_HOST_VERIFICATION_UNAVAILABLE");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new TargetRetirementFailure("PROVIDER_HOST_VERIFICATION_INTERRUPTED");
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            delete(directory);
        }
    }

    static void writePrivateKey(Path keyFile, String privateKey) throws IOException {
        if (blank(privateKey)) {
            throw new TargetRetirementFailure("PROVIDER_HOST_VERIFICATION_CREDENTIAL_MISSING");
        }
        // Managed-secret storage trims the terminal LF required by OpenSSH private-key parsing.
        Files.writeString(keyFile, privateKey.endsWith("\n") ? privateKey : privateKey + "\n",
            StandardCharsets.UTF_8);
        setPermissions(keyFile, "rw-------");
    }

    private static void setPermissions(Path path, String permissions) throws IOException {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
    }

    private static void delete(Path directory) {
        if (directory == null) return;
        try (var entries = Files.walk(directory)) {
            entries.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static boolean safeCommand(List<String> command) {
        if (command == null || command.stream().anyMatch(SshProviderHostCommandRunner::blank)) return false;
        if (com.ai.fabric.platform.backend.deployment.service.RuntimeColdStoreReadback.permits(command)) return true;
        if (command.size() == 3 && "test".equals(command.get(0)) && "-d".equals(command.get(1))) {
            String path = command.get(2);
            return SAFE_PATH.matcher(path).matches() && !path.startsWith("//")
                && java.nio.file.Path.of(path).normalize().toString().equals(path);
        }
        if (command.size() == 6 && command.subList(0, 5)
            .equals(List.of("docker", "volume", "ls", "--quiet", "--filter"))) {
            String filter = command.get(5);
            if (!filter.startsWith("name=^") || !filter.endsWith("$")) return false;
            return SAFE_VOLUME.matcher(filter.substring(6, filter.length() - 1)).matches();
        }
        if (safeContainerReadback(command)) return true;
        return command.size() == 5 && command.subList(0, 4)
            .equals(List.of("docker", "volume", "rm", "-f"))
            && SAFE_VOLUME.matcher(command.get(4)).matches();
    }

    private static boolean safeContainerReadback(List<String> command) {
        if (command.size() >= 5 && command.subList(0, 4).equals(List.of("docker", "ps", "--quiet", "--filter"))) {
            var normalized = new ArrayList<>(command);
            normalized.add(2, "-a");
            return safeContainerReadback(normalized);
        }
        List<String> prefix = List.of("docker", "ps", "-a", "--quiet", "--filter");
        if (command.size() == 6 && command.subList(0, 5).equals(prefix)) {
            return currentOwnerFilter(command.get(5));
        }
        if (command.size() != 8 || !command.subList(0, 5).equals(prefix)
            || !"--filter".equals(command.get(6))) return false;
        String owner = command.get(5);
        String identity = command.get(7);
        if ("label=coolify.applicationId".equals(owner) && identity.startsWith("name=")) {
            return SAFE_RESOURCE_UUID.matcher(identity.substring("name=".length())).matches();
        }
        if (!("label=coolify.applicationId".equals(owner)
            || "label=coolify.databaseId".equals(owner))
            || !identity.startsWith("label=com.docker.compose.project=")) return false;
        return SAFE_RESOURCE_UUID.matcher(identity.substring("label=com.docker.compose.project=".length())).matches();
    }

    private static boolean currentOwnerFilter(String filter) {
        for (String type : List.of("application", "database")) {
            String prefix = "label=coolify." + type + "Uuid=";
            if (filter.startsWith(prefix)) {
                return SAFE_RESOURCE_UUID.matcher(filter.substring(prefix.length())).matches();
            }
        }
        return false;
    }
}
