package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportDocumentStatusServiceTest {
    final DeploymentTargetContextService targets = mock(DeploymentTargetContextService.class);
    final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    final DeploymentRuntimeJsonTransport transport = mock(DeploymentRuntimeJsonTransport.class);
    final SupportDocumentStatusService service = new SupportDocumentStatusService(targets, authority, transport);
    final DeploymentEntity deployment = SupportFixture.deployment("dep-a", "customer-a", "tenant-a");
    final DeploymentTargetReference target = new DeploymentTargetReference("dep-a", "stage", "rel-stage");
    final DeploymentVersionEntity version = new DeploymentVersionEntity();
    final DeploymentReleaseEntity release = new DeploymentReleaseEntity();
    final Map<String, String> headers = Map.of("Authorization", "private-assertion");
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    void ready() {
        when(targets.resolveOwnedRead("dep-a", "stage", "rel-stage", "customer-a", "tenant-a"))
            .thenReturn(new DeploymentTargetContextService.Context(deployment, new DeploymentTargetProfileEntity(), release,
                version, "https://stage-runtime.example", "https://connector.example"));
        when(authority.issueReviewedIntegrationSessionHeaders(deployment, version, release, "case-principal", List.of("documents:read")))
            .thenReturn(headers);
    }

    @Test void projectsOnlyIndexLifecycleAndVersionsFromActualExactTargetRead() throws Exception {
        ready();
        var body = json.readTree("""
            [{"status":"ACTIVE","activeVersion":1,"candidateVersion":2,"updatedAt":"2026-10-30T10:00:00Z",
              "displayName":"DO_NOT_DISCLOSE","objectLocatorDigest":"DO_NOT_DISCLOSE","failureMessage":"DO_NOT_DISCLOSE"},
             {"status":"FAILED","activeVersion":null,"candidateVersion":1,"updatedAt":"2026-10-30T11:00:00Z"}]
            """);
        when(transport.send("https://stage-runtime.example", "GET", "/api/documents/sources", headers, null, null))
            .thenReturn(new DeploymentRuntimeJsonTransport.Response(200, body));
        var result = service.read(deployment, target, "case-principal");
        assertThat(result).containsEntry("availability", "CURRENT").containsEntry("registeredSourceCount", 2)
            .containsEntry("sourcesWithActiveVersion", 1).containsEntry("sourcesWithNewerCandidate", 1)
            .containsEntry("upstreamStorageProbed", false).containsEntry("releaseId", "rel-stage")
            .containsEntry("sourcesByStatus", Map.of("ACTIVE", 1, "FAILED", 1));
        assertThat(json.writeValueAsString(result)).doesNotContain("DO_NOT_DISCLOSE", "private-assertion", "connector.example");
        verify(authority).issueReviewedIntegrationSessionHeaders(deployment, version, release, "case-principal", List.of("documents:read"));
    }

    @Test void staleOrForeignTargetNeverIssuesHeadersOrQueriesRuntime() {
        when(targets.resolveOwnedRead("dep-a", "stage", "rel-stage", "customer-a", "tenant-a"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "private DO_NOT_DISCLOSE"));
        assertThat(service.read(deployment, target, "case-principal")).containsEntry("availability", "INCOMPLETE")
            .doesNotContainValue("private DO_NOT_DISCLOSE");
        verifyNoInteractions(authority, transport);
    }

    @Test void upstreamDenialIsAnIncompleteObservationNotAnEmptyHealthyIndex() throws Exception {
        ready();
        when(transport.send("https://stage-runtime.example", "GET", "/api/documents/sources", headers, null, null))
            .thenReturn(new DeploymentRuntimeJsonTransport.Response(403, json.readTree("{\"secret\":\"DO_NOT_DISCLOSE\"}")));
        assertThat(service.read(deployment, target, "case-principal")).containsEntry("availability", "INCOMPLETE")
            .doesNotContainKey("registeredSourceCount");
    }

    @Test void malformedSourceListDoesNotBecomeSuccessfulEmptyStatus() throws Exception {
        ready();
        when(transport.send("https://stage-runtime.example", "GET", "/api/documents/sources", headers, null, null))
            .thenReturn(new DeploymentRuntimeJsonTransport.Response(200, json.readTree("{}")));
        assertThat(service.read(deployment, target, "case-principal")).containsEntry("availability", "INCOMPLETE");
    }

    @Test void mismatchedDeploymentNeverUsesTheSuppliedTarget() {
        var foreign = new DeploymentTargetReference("dep-foreign", "stage", "rel-stage");
        assertThat(service.read(deployment, foreign, "case-principal")).containsEntry("availability", "INCOMPLETE");
        verifyNoInteractions(targets, authority, transport);
    }

    @Test void nonObjectSourcesDoNotProduceCurrentCounts() throws Exception {
        ready();
        when(transport.send("https://stage-runtime.example", "GET", "/api/documents/sources", headers, null, null))
            .thenReturn(new DeploymentRuntimeJsonTransport.Response(200, json.readTree("[null]")));
        assertThat(service.read(deployment, target, "case-principal")).containsEntry("availability", "INCOMPLETE")
            .doesNotContainKey("registeredSourceCount");
    }
}
