package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsProperties;
import com.ai.fabric.platform.backend.deployment.operations.CoreReleaseAuthorizationStore;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.text;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.*;

/** Server configuration selects new contracts; a release always retains its recorded contract. */
@Service
public class ProviderUsageModeResolver {
    public enum Mode { TEAM_AUTHORIZED, CUSTOMER_KEY, FUNDED }

    private final DeploymentOperationsProperties properties;
    private final JdbcTemplate jdbc;
    private final CoreReleaseAuthorizationStore authorizations;

    public ProviderUsageModeResolver(DeploymentOperationsProperties properties, JdbcTemplate jdbc,
                                    CoreReleaseAuthorizationStore authorizations) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.authorizations = authorizations;
    }

    public String resolve(DeploymentEntity deployment, String targetProfileId) {
        if (deployment == null) throw invalid();
        text(deployment.getId(), 64); text(targetProfileId, 64);
        if (hasAllocation(deployment.getId(), targetProfileId)) return Mode.FUNDED.name();
        var selected = mode(properties.getProviderUsageMode());
        if (selected == Mode.TEAM_AUTHORIZED && !properties.getTeamAuthorizedTargets().contains(targetProfileId))
            throw denied("PROVIDER_USAGE_TEAM_TARGET_NOT_AUTHORIZED");
        requireSupported(selected);
        return selected.name();
    }

    Optional<String> forRelease(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        var authorization = ProviderUsageAuthorizedReleaseReader.coreAuthorization(authorizations, release.getId());
        if (authorization.isEmpty()) return hasAllocation(deployment.getId(), release.getTargetProfileId())
            ? Optional.of(Mode.FUNDED.name()) : Optional.empty();
        var record = authorization.get();
        record.requireScope(deployment, release);
        var pinned = mode(record.providerUsageMode());
        requireSupported(pinned);
        if (pinned != Mode.FUNDED && hasAllocation(deployment.getId(), release.getTargetProfileId()))
            throw conflict("PROVIDER_USAGE_FUNDED_MODE_REQUIRED");
        return Optional.of(pinned.name());
    }

    private boolean hasAllocation(String deploymentId, String targetProfileId) {
        return !jdbc.query("SELECT approval_id FROM provider_usage_allocations WHERE deployment_id=? AND target_profile_id=?",
            (rs, row) -> rs.getString(1), deploymentId, targetProfileId).isEmpty();
    }

    static Mode mode(String value) {
        try { return Mode.valueOf(value); }
        catch (IllegalArgumentException | NullPointerException error) { throw conflict("PROVIDER_USAGE_MODE_INVALID"); }
    }

    private static void requireSupported(Mode mode) {
        if (mode == Mode.CUSTOMER_KEY) throw conflict("PROVIDER_USAGE_CUSTOMER_KEY_NOT_SUPPORTED");
    }
}
