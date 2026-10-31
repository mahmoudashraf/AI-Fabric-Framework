package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.desk.DeskBindingRepository;
import com.ai.fabric.platform.backend.desk.DeskServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class SupportTeamService {
    private final SupportRequesterService requesters;
    private final SupportSettings settings;
    private final DeskBindingRepository bindings;
    private final DeploymentTargetProfileRepository targets;
    private final DeskServiceClient desk;

    public SupportTeamService(SupportRequesterService requesters, SupportSettings settings,
        DeskBindingRepository bindings, DeploymentTargetProfileRepository targets, DeskServiceClient desk) {
        this.requesters = requesters; this.settings = settings; this.bindings = bindings;
        this.targets = targets; this.desk = desk;
    }

    public Team team() {
        var access = access();
        JsonNode roster = desk.get(access.connection(), access.path());
        if (roster == null || !roster.isArray()) throw invalidRoster();
        List<Profile> profiles = new ArrayList<>();
        for (JsonNode item : roster) {
            String ref = item.path("profileRef").asText();
            String name = item.path("displayName").asText();
            if (!validRef(ref) || name.isBlank() || name.length() > 160 || !item.path("photoAvailable").isBoolean()) {
                throw invalidRoster();
            }
            boolean hasPhoto = item.path("photoAvailable").booleanValue();
            // Never forward Desk's private avatar URL, identity fields or email-derived names.
            profiles.add(new Profile(ref, name.contains("@") ? "Support agent" : name, hasPhoto));
        }
        return new Team(List.copyOf(profiles));
    }

    public byte[] photo(String profileRef) {
        var access = access();
        if (!validRef(profileRef)) throw SupportPolicy.error(HttpStatus.NOT_FOUND, "SUPPORT_TEAM_PHOTO_NOT_FOUND");
        return desk.getPhoto(access.connection(), access.path() + "/" + profileRef + "/photo");
    }

    private Access access() {
        // This is shared LoomAI support, but each reader must still have active customer membership.
        requesters.current();
        var fixed = settings.binding();
        var binding = bindings.findById(settings.deskBindingId()).orElseThrow(SupportPolicy::unavailable);
        if (!"ENABLED".equals(binding.getStatus()) || !fixed.customerId().equals(binding.getCustomerId())
            || !fixed.tenantId().equals(binding.getTenantId()) || !fixed.deploymentId().equals(binding.getDeploymentId())
            || !fixed.targetProfileId().equals(binding.getTargetProfileId())) throw SupportPolicy.unavailable();
        var target = targets.findById(fixed.targetProfileId()).filter(value -> value.isActive())
            .orElseThrow(SupportPolicy::unavailable);
        return new Access(desk.connection(target.getEnvironmentName()), "/api/desk/admin/v1/bindings/"
            + URLEncoder.encode(binding.getId(), StandardCharsets.UTF_8) + "/support-team");
    }

    private static boolean validRef(String ref) { return ref != null && ref.matches("team-[a-f0-9]{64}"); }
    private static org.springframework.web.server.ResponseStatusException invalidRoster() {
        return SupportPolicy.error(HttpStatus.BAD_GATEWAY, "SUPPORT_TEAM_RESPONSE_INVALID");
    }
    private record Access(DeskServiceClient.Connection connection, String path) { }
    public record Profile(String agentReference, String displayName, boolean photoAvailable) { }
    public record Team(List<Profile> agents) { }
}
