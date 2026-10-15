package com.ai.fabric.runtime.lifecycle.retirement;

import ai.fabric.rag.VectorDatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RuntimeTargetIndexRetirementServiceTest {

    @Test void clearsOnlyAttestedEntityTypesAndReplaysDurableReceipt() throws Exception {
        var fixture = revokedFixture();
        VectorDatabaseService vectors = mock(VectorDatabaseService.class);
        when(vectors.supportsClearByEntityType()).thenReturn(true);
        when(vectors.adminDiagnostics()).thenReturn(Map.of(
            "provider", "qdrant", "scopeType", "COLLECTION_PREFIX", "scopePrefix", "dep-123-staging"));
        when(vectors.getVectorCountByEntityType("dealer-vehicle")).thenReturn(7L, 0L);
        when(vectors.getVectorCountByEntityType("document")).thenReturn(3L, 0L);
        when(vectors.clearVectorsByEntityType("dealer-vehicle")).thenReturn(7L);
        when(vectors.clearVectorsByEntityType("document")).thenReturn(3L);
        RuntimeRetirementTopology topology = topology(fixture, "dep-123-staging");
        var service = service(fixture, topology, vectors);
        var command = new IndexRetirementCommand(fixture.binding(2), topology.fingerprint());

        IndexRetirementReadback applied = service.retire(command);
        IndexRetirementReadback replay = service.retire(command);

        assertThat(applied.complete()).isTrue();
        assertThat(applied.removedVectors()).isEqualTo(10);
        assertThat(applied.countsByEntityType()).containsOnlyKeys("dealer-vehicle", "document")
            .containsValues(0L);
        assertThat(replay.replay()).isTrue();
        verify(vectors).clearVectorsByEntityType("dealer-vehicle");
        verify(vectors).clearVectorsByEntityType("document");
        verify(vectors, never()).clearVectors();
    }

    @Test void refusesDeletionBeforeAuthorityRevocationOrWhenProviderScopeDrifts() throws Exception {
        var fixture = new RuntimeRetirementTestFixture();
        RuntimeRetirementTopology topology = topology(fixture, "dep-123-staging");
        VectorDatabaseService vectors = mock(VectorDatabaseService.class);
        when(vectors.supportsClearByEntityType()).thenReturn(true);
        when(vectors.adminDiagnostics()).thenReturn(Map.of(
            "provider", "qdrant", "scopeType", "COLLECTION_PREFIX", "scopePrefix", "foreign"));
        var service = service(fixture, topology, vectors);
        var initial = new IndexRetirementCommand(fixture.binding(0), topology.fingerprint());

        assertThatThrownBy(() -> service.retire(initial))
            .hasMessageContaining("TARGET_INDEX_RETIREMENT_BINDING_CHANGED");

        fixture.service.command(fixture.command(1, 0, CommandType.BEGIN_DRAIN));
        fixture.service.command(fixture.command(2, 1, CommandType.RECORD_AUTHORITY_REVOCATION));
        var revoked = new IndexRetirementCommand(fixture.binding(2), topology.fingerprint());
        assertThatThrownBy(() -> service.retire(revoked))
            .hasMessageContaining("TARGET_INDEX_PROVIDER_SCOPE_CHANGED");
        verify(vectors, never()).clearVectorsByEntityType(anyString());
        verify(vectors, never()).clearVectors();
    }

    @Test void refusesTopologyDriftAndUnsupportedClearCapability() throws Exception {
        var fixture = revokedFixture();
        RuntimeRetirementTopology topology = topology(fixture, "dep-123-staging");
        VectorDatabaseService vectors = mock(VectorDatabaseService.class);
        when(vectors.supportsClearByEntityType()).thenReturn(false);
        var service = service(fixture, topology, vectors);

        assertThatThrownBy(() -> service.retire(new IndexRetirementCommand(
            fixture.binding(2), "f".repeat(64))))
            .hasMessageContaining("TARGET_INDEX_TOPOLOGY_CHANGED");
        assertThatThrownBy(() -> service.retire(new IndexRetirementCommand(
            fixture.binding(2), topology.fingerprint())))
            .hasMessageContaining("TARGET_INDEX_RECLAMATION_UNSUPPORTED");
    }

    private RuntimeRetirementTestFixture revokedFixture() {
        var fixture = new RuntimeRetirementTestFixture();
        fixture.service.command(fixture.command(1, 0, CommandType.BEGIN_DRAIN));
        fixture.service.command(fixture.command(2, 1, CommandType.RECORD_AUTHORITY_REVOCATION));
        return fixture;
    }

    private RuntimeTargetIndexRetirementService service(RuntimeRetirementTestFixture fixture,
        RuntimeRetirementTopology topology, VectorDatabaseService vectors) {
        return new RuntimeTargetIndexRetirementService(fixture.identity, fixture.service, topology,
            vectors, fixture.jdbc, fixture.mapper, new DataSourceTransactionManager(fixture.dataSource));
    }

    private RuntimeRetirementTopology topology(RuntimeRetirementTestFixture fixture,
        String scopePrefix) throws Exception {
        var features = new EnumMap<RuntimeRetirementTopology.Feature,
            RuntimeRetirementTopology.Presence>(RuntimeRetirementTopology.Feature.class);
        for (var feature : RuntimeRetirementTopology.Feature.values()) {
            features.put(feature, RuntimeRetirementTopology.Presence.ABSENT);
        }
        features.put(RuntimeRetirementTopology.Feature.INDEXING,
            RuntimeRetirementTopology.Presence.PRESENT);
        var scope = new RuntimeRetirementTopology.VectorScope("qdrant", "COLLECTION_PREFIX",
            scopePrefix, List.of("dealer-vehicle", "document"));
        var contract = new RuntimeRetirementTopology.Contract(RuntimeRetirementTopology.VERSION,
            RuntimeRetirementTestFixture.TARGET, "tenant-1", features, List.of(), scope);
        ObjectMapper mapper = fixture.mapper;
        String raw = mapper.writeValueAsString(contract);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(raw.getBytes(StandardCharsets.UTF_8)));
        MockEnvironment environment = new MockEnvironment()
            .withProperty("loomai.runtime.target-retirement.topology-json", raw)
            .withProperty("loomai.runtime.target-retirement.topology-sha256", digest);
        return new RuntimeRetirementTopology(environment, mapper, fixture.identity);
    }
}
