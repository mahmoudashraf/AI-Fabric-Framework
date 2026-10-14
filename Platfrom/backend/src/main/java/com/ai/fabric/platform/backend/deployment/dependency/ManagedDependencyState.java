package com.ai.fabric.platform.backend.deployment.dependency;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Internal durable state. Public projections deliberately omit secret names and values. */
public class ManagedDependencyState {
    public List<Node> nodes = new ArrayList<>();
    public List<Operator> operators = new ArrayList<>();
    public Map<String, Command> commands = new LinkedHashMap<>();
    public Map<String, String> callbacks = new LinkedHashMap<>();
    public List<String> origins = new ArrayList<>();
    public Map<String, String> targetValues = new LinkedHashMap<>();
    public Map<String, String> targetSecretReferences = new LinkedHashMap<>();
    public long configurationRevision;
    public long effectiveConfigurationRevision = -1;
    public List<String> retiredOperatorSecretNames = new ArrayList<>();
    public String retirementFingerprint;

    public static class Node {
        public String slot;
        public String serviceRef;
        public String accountId;
        public String bindingId;
        public String applicationUuid;
        public String databaseUuid;
        public String baseUrl;
        public String status = "PLANNED";
        public boolean applicationCreateIntent;
        public boolean databaseCreateIntent;
        public boolean initialized;
        public boolean disabled;
        public long provisionedConfigurationRevision = -1;
        public Map<String, String> secrets = new LinkedHashMap<>();
        public Map<String, String> volumes = new LinkedHashMap<>();
    }
    public static class Operator {
        public String id;
        public String displayName;
        public String slot;
        public String secretName;
        public String status = "ENROLLED";
        public int credentialRevision = 1;
        public boolean delivered;
        public Instant effectiveAt;
    }
    public record Command(String fingerprint, String resultId, Instant acceptedAt, String actorId, String actorRole) {}
}
