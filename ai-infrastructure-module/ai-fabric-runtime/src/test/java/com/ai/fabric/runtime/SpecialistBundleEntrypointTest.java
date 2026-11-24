package com.ai.fabric.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecialistBundleEntrypointTest {
    @TempDir Path directory;

    @Test
    void downloadsExactBytesWithoutPuttingBundleInProviderEnvironment() throws Exception {
        var bytes = ("{\"test\":\"" + "x".repeat(64000) + "\"}").getBytes();
        assertThat(run(bytes, Map.of()).exitCode()).isZero();
        assertThat(Files.readString(directory.resolve("loaded"))).isEqualTo(Base64.getEncoder().encodeToString(bytes));
    }

    @Test
    void workflowBundleUsesTheSameVerifiedBoundedTransport() throws Exception {
        var bytes = "{\"workflow\":true}".getBytes();
        assertThat(run(bytes, Map.of(), "LOOMAI_WORKFLOW_ASSISTANCE").exitCode()).isZero();
        assertThat(Files.readString(directory.resolve("loaded"))).isEqualTo(Base64.getEncoder().encodeToString(bytes));
    }

    @Test
    void workflowBundleCannotBypassHashSizeOrTransportChecks() throws Exception {
        assertThat(run("{}".getBytes(), Map.of("LOOMAI_WORKFLOW_ASSISTANCE_BUNDLE_HASH", "sha256:" + "a".repeat(64)), "LOOMAI_WORKFLOW_ASSISTANCE").exitCode()).isNotZero();
        assertThat(run("x".repeat(65537).getBytes(), Map.of(), "LOOMAI_WORKFLOW_ASSISTANCE").exitCode()).isNotZero();
        assertThat(run("{}".getBytes(), Map.of("LOOMAI_WORKFLOW_ASSISTANCE_BUNDLE_URL", "http://insecure.example"), "LOOMAI_WORKFLOW_ASSISTANCE").exitCode()).isNotZero();
        assertThat(Files.exists(directory.resolve("loaded"))).isFalse();
    }

    @Test
    void rejectsHashMismatchOversizeEmptyInsecureAndFailedDownloadsBeforeJava() throws Exception {
        byte[] content = "{\"test\":true}".getBytes();
        assertThat(run(content, Map.of("LOOMAI_SPECIALIST_ASSISTANCE_BUNDLE_HASH", "sha256:" + "a".repeat(64))).exitCode()).isNotZero();
        assertThat(run("x".repeat(65537).getBytes(), Map.of()).exitCode()).isNotZero();
        assertThat(run(new byte[0], Map.of()).exitCode()).isNotZero();
        assertThat(run(content, Map.of("LOOMAI_SPECIALIST_ASSISTANCE_BUNDLE_URL", "http://insecure.example")).exitCode()).isNotZero();
        assertThat(run(content, Map.of("TEST_DOWNLOAD_FAIL", "true")).exitCode()).isNotZero();
        assertThat(run(content, Map.of("LOOMAI_SPECIALIST_ASSISTANCE_BUNDLE_BASE64", "both")).exitCode()).isNotZero();
        assertThat(Files.exists(directory.resolve("loaded"))).isFalse();
    }

    private Result run(byte[] content, Map<String,String> overrides) throws Exception {
        return run(content, overrides, "LOOMAI_SPECIALIST_ASSISTANCE");
    }

    private Result run(byte[] content, Map<String,String> overrides, String prefix) throws Exception {
        Path input = directory.resolve("input"); Files.write(input, content);
        script("id", "echo 10001");
        script("curl", "[ \"${TEST_DOWNLOAD_FAIL:-false}\" != true ] || exit 22\nwhile [ $# -gt 0 ]; do if [ \"$1\" = --output ]; then shift; cp \"$TEST_INPUT\" \"$1\"; exit 0; fi; shift; done\nexit 1");
        script("sha256sum", "echo \"$TEST_DIGEST  $1\"");
        script("java", "printf '%s' \"${" + prefix + "_BUNDLE_BASE64:-}\" > \"$TEST_LOADED\"");
        Path entrypoint = Path.of("docker-entrypoint.sh").toAbsolutePath();
        var builder = new ProcessBuilder("sh", entrypoint.toString());
        var env = builder.environment();
        env.put("PATH", directory + ":" + env.get("PATH"));
        env.put("TEST_INPUT", input.toString()); env.put("TEST_LOADED", directory.resolve("loaded").toString());
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        env.put("TEST_DIGEST", digest);
        for (String candidate : new String[] {"LOOMAI_SPECIALIST_ASSISTANCE", "LOOMAI_WORKFLOW_ASSISTANCE"}) {
            env.remove(candidate + "_BUNDLE_URL"); env.remove(candidate + "_BUNDLE_BASE64"); env.remove(candidate + "_BUNDLE_HASH");
        }
        env.put(prefix + "_BUNDLE_URL", "https://platform.example/signed-artifact");
        env.put(prefix + "_BUNDLE_BASE64", "");
        env.put(prefix + "_BUNDLE_HASH", "sha256:" + digest);
        env.put("AI_CONFIG_DEFAULT_FILE", ""); env.putAll(overrides);
        var process = builder.redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        return new Result(process.waitFor(), output);
    }

    private void script(String name, String body) throws Exception {
        Path file = directory.resolve(name); Files.writeString(file, "#!/bin/sh\nset -eu\n" + body + "\n");
        assertThat(file.toFile().setExecutable(true)).isTrue();
    }
    private record Result(int exitCode, String output) { }
}
