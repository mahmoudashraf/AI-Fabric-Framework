package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.retirement.ProviderHostCommandRunner;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementFailure;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * One four-table snapshot, not a retirement or worker-fencing certificate. The caller must already
 * validate native database ownership. A preflight may precede quiescence; authoritative cold recovery
 * additionally requires explicit consent and independent writer/ingress quiescence.
 * Pending indexing, budget and connector work is neither inspected nor discarded by this utility.
 */
@Component
public class RuntimeColdStoreReadback {
    private static final int MAX_OUTPUT_CHARACTERS = 4096;
    private static final Pattern UUID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");
    private static final List<String> TABLES = List.of("chat_sessions", "ai_conversation_control",
        "ai_conversation_orchestration_state", "ai_specialist_execution");
    private static final String SCRIPT = "exec env PGOPTIONS='-c default_transaction_read_only=on' "
        + "psql -X -qAt -v ON_ERROR_STOP=1 --username=\"${POSTGRES_USER:?}\" --dbname=\"${POSTGRES_DB:?}\" "
        + "--command=\"SELECT json_build_object("
        + "'chat_sessions', (SELECT count(*) FROM public.chat_sessions), "
        + "'ai_conversation_control', (SELECT count(*) FROM public.ai_conversation_control), "
        + "'ai_conversation_orchestration_state', (SELECT count(*) FROM public.ai_conversation_orchestration_state), "
        + "'ai_specialist_execution', (SELECT count(*) FROM public.ai_specialist_execution));\"";
    private final ProviderHostCommandRunner runner;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Autowired
    public RuntimeColdStoreReadback(ProviderHostCommandRunner runner, ObjectMapper mapper) {
        this(runner, mapper, Clock.systemUTC());
    }
    RuntimeColdStoreReadback(ProviderHostCommandRunner runner, ObjectMapper mapper, Clock clock) {
        this.runner = runner; this.mapper = mapper; this.clock = clock;
    }

    public Proof requireEmpty(TargetIntent intent, String serverUuid, String databaseUuid) {
        if (intent == null || !safeUuid(serverUuid)) throw failure("SCOPE_INVALID");
        var command = command(databaseUuid);
        var result = runner.run(intent, serverUuid, command);
        if (result == null || result.exitCode() != 0) throw failure("READBACK_FAILED");
        var counts = counts(result.output());
        Instant observedAt = clock.instant();
        return new Proof(databaseUuid, observedAt, counts,
            reference(intent, serverUuid, databaseUuid, observedAt, counts));
    }

    /** Each argument except the validated UUID is a constant; OpenSSH joins remote arguments. */
    public static List<String> command(String databaseUuid) {
        if (!safeUuid(databaseUuid)) throw failure("SCOPE_INVALID");
        return List.of("docker", "exec", databaseUuid, "sh", "-lc", "'" + SCRIPT.replace("'", "'\"'\"'") + "'");
    }
    public static boolean permits(List<String> command) {
        return command != null && command.size() == 6 && safeUuid(command.get(2))
            && command(command.get(2)).equals(command);
    }
    static String fixedScript() { return SCRIPT; }

    private Map<String, Long> counts(String output) {
        if (output == null || output.isBlank() || output.length() > MAX_OUTPUT_CHARACTERS) throw failure("OUTPUT_INVALID");
        JsonNode value;
        try (JsonParser parser = mapper.getFactory().createParser(output)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            value = mapper.readTree(parser);
            if (parser.nextToken() != null) throw failure("OUTPUT_INVALID");
        } catch (TargetRetirementFailure failure) { throw failure; }
        catch (Exception invalid) { throw failure("OUTPUT_INVALID"); }
        if (value == null || !value.isObject() || value.size() != TABLES.size()) throw failure("OUTPUT_INVALID");
        var names = new java.util.HashSet<String>(); value.fieldNames().forEachRemaining(names::add);
        if (!names.equals(Set.copyOf(TABLES))) throw failure("OUTPUT_INVALID");
        var result = new java.util.LinkedHashMap<String, Long>();
        for (String table : TABLES) {
            var count = value.get(table);
            if (!count.isIntegralNumber() || !count.canConvertToLong() || count.longValue() < 0) throw failure("OUTPUT_INVALID");
            result.put(table, count.longValue());
        }
        if (result.values().stream().anyMatch(count -> count != 0)) throw failure("NOT_EMPTY");
        return Map.copyOf(result);
    }
    private String reference(TargetIntent intent, String server, String database, Instant at, Map<String, Long> counts) {
        String canonical = String.join("\n", "owned-postgres-empty-store-v1", intent.name(), server, database, at.toString(),
            TABLES.stream().map(table -> table + "=" + counts.get(table)).collect(java.util.stream.Collectors.joining("\n")));
        try {
            return "owned-postgres-empty-store-v1:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
    private static boolean safeUuid(String value) { return value != null && UUID.matcher(value).matches(); }
    private static TargetRetirementFailure failure(String reason) {
        return new TargetRetirementFailure("OWNED_POSTGRES_EMPTY_STORE_" + reason);
    }
    public record Proof(String databaseUuid, Instant observedAt, Map<String, Long> counts, String reference) {
        public Proof { counts = Map.copyOf(counts); }
    }
}
