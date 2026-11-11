package com.ai.fabric.platform.backend.mcp.connection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ai.fabric.platform.backend.secret.entity.*;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class McpOwnedSecretsTest {
    private final PlatformSecretRepository repository = mock(PlatformSecretRepository.class);
    private final McpOwnedSecrets service = new McpOwnedSecrets(repository);
    @Test void exactOwnershipAndTargetAreRequiredRegardlessOfSecretName() {
        var secret = owned(); when(repository.findById("CUSTOMER_KEY")).thenReturn(Optional.of(secret));
        assertThat(service.resolve("dep-one", "stage", "CUSTOMER_KEY")).isEqualTo("private-value");
        assertThatThrownBy(() -> service.resolve("dep-other", "stage", "CUSTOMER_KEY")).isInstanceOf(ResponseStatusException.class);
        secret.setTargetProfileId("stage");
        assertThat(service.resolve("dep-one", "stage", "CUSTOMER_KEY")).isEqualTo("private-value");
        assertThatThrownBy(() -> service.resolve("dep-one", "prod", "CUSTOMER_KEY")).isInstanceOf(ResponseStatusException.class);
        secret.setTargetProfileId(null); secret.setManagedByPlatform(true);
        assertThatThrownBy(() -> service.resolve("dep-one", "stage", "CUSTOMER_KEY")).isInstanceOf(ResponseStatusException.class);
        secret.setManagedByPlatform(false); secret.setScopeType(PlatformSecretScopeType.GLOBAL_PLATFORM);
        assertThatThrownBy(() -> service.resolve("dep-one", "stage", "CUSTOMER_KEY")).isInstanceOf(ResponseStatusException.class);
    }
    private static PlatformSecretEntity owned() {
        var s = new PlatformSecretEntity(); s.setName("CUSTOMER_KEY"); s.setDeploymentId("dep-one");
        s.setOwnerType(PlatformSecretOwnerType.DEPLOYMENT); s.setScopeType(PlatformSecretScopeType.DEPLOYMENT_OVERRIDE); s.setSecretValue("private-value"); return s;
    }
}
