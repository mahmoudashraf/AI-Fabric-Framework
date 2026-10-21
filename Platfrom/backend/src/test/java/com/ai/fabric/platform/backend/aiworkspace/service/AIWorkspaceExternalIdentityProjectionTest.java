package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.CONFLICT;

class AIWorkspaceExternalIdentityProjectionTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AIWorkspaceExternalIdentityProjection projection = new AIWorkspaceExternalIdentityProjection(
        mapper, new AIWorkspaceAuthenticationProfileRegistry(mapper));
    private static final String MANAGED = AIWorkspaceExternalIdentityProjection.MANAGED_FIELD;
    private static final String BASE_SCOPES = "chat:query,chat:suggestions,chat:conversations";

    @Test
    void anonymousInstallationsLeaveSecurityUnchanged() throws Exception {
        ObjectNode security = security();
        ObjectNode before = security.deepCopy();
        var anonymous = new AIWorkspaceInstallationEntity();
        anonymous.setInteractionPolicy(AIWorkspaceInteractionPolicy.PUBLIC);

        projection.project(security, List.of(anonymous), handover());

        assertThat(security).isEqualTo(before);
    }

    @Test
    void projectsNormalizedSupabaseTupleAndOnlyBasicScopes() throws Exception {
        ObjectNode security = security();
        projection.project(security, List.of(auth("https://Example.Supabase.co/")), mapper.createObjectNode());

        assertThat(security.path("externalIdentityBrokerEnabled").booleanValue()).isTrue();
        assertThat(security.path("externalIdentityProvider").asText()).isEqualTo("SUPABASE");
        assertThat(security.path("externalIdentityIssuer").asText()).isEqualTo("https://example.supabase.co/auth/v1");
        assertThat(security.path("externalIdentityJwkSetUri").asText())
            .isEqualTo("https://example.supabase.co/auth/v1/.well-known/jwks.json");
        assertThat(security.path("externalIdentityAudience").asText()).isEqualTo("authenticated");
        assertThat(security.path("externalIdentityGrantedScopes").asText()).isEqualTo(BASE_SCOPES);
        assertThat(security.path("privateRuntimeAcceptedIssuers").asText()).isEqualTo("private-original");
        assertThat(security.toString()).doesNotContain("sb_publishable_fixture", "publishableKey", "methods");
        assertThat(security.path(MANAGED).path("previous").path("externalIdentityBrokerEnabled").booleanValue()).isFalse();
        assertThat(security.path(MANAGED).path("previous").has("externalIdentityIssuer")).isFalse();
    }

    @Test
    void matchingAuthenticationInstallationsShareOneIdentity() throws Exception {
        ObjectNode security = security();
        var login = auth("https://example.supabase.co");
        login.setInteractionPolicy(AIWorkspaceInteractionPolicy.LOGIN_REQUIRED);
        projection.project(security, List.of(auth("https://EXAMPLE.supabase.co/"), login), null);
        ObjectNode once = security.deepCopy();

        projection.project(security, List.of(login), null);

        assertThat(security).isEqualTo(once);
    }

    @Test
    void conflictingInstallationsFailBeforeAnyMutation() throws Exception {
        ObjectNode security = security();
        ObjectNode before = security.deepCopy();
        conflictWithoutMutation(security, before, List.of(auth("https://one.supabase.co"), auth("https://two.supabase.co")), null);
    }

    @Test
    void addsHandoverScopesOnlyWhenSelectedAndEnabledAndRemovesThemWhenDisabled() throws Exception {
        ObjectNode security = security();
        var installations = List.of(auth("https://example.supabase.co"));
        projection.project(security, installations, handover());
        assertThat(security.path("externalIdentityGrantedScopes").asText()).isEqualTo(
            BASE_SCOPES + ",chat:messages,chat:handover:request,chat:handover:control,chat:handover:read");

        for (JsonNode behavior : List.of(
            mapper.readTree("{\"conversationHandover\":{\"enabled\":true}}"),
            mapper.readTree("{\"executionExtensions\":[\"CONVERSATION_HANDOVER\"],\"conversationHandover\":{\"enabled\":\"true\"}}"),
            mapper.readTree("{\"executionExtensions\":[\"CONVERSATION_HANDOVER\"],\"conversationHandover\":{\"enabled\":false}}"))) {
            projection.project(security, installations, behavior);
            assertThat(security.path("externalIdentityGrantedScopes").asText()).isEqualTo(BASE_SCOPES);
        }
    }

    @Test
    void preservesMatchingManualBrokerIncludingExplicitScopesAndEmailPolicy() throws Exception {
        ObjectNode security = manualBroker();
        security.put("externalIdentityProvider", "supabase");
        security.put("externalIdentityGrantedScopes", BASE_SCOPES + ",chat:messages");
        security.put("externalIdentityRequireVerifiedEmail", true);
        ObjectNode before = security.deepCopy();

        projection.project(security, List.of(auth("https://example.supabase.co")), null);
        projection.project(security, List.of(), null);

        assertThat(security).isEqualTo(before);
        assertThat(security.has(MANAGED)).isFalse();
    }

    @Test
    void preservesMatchingManualBrokerWithCatalogDefaults() throws Exception {
        ObjectNode security = manualBroker();
        security.remove(List.of("externalIdentityProvider", "externalIdentityAudience", "externalIdentityGrantedScopes"));
        ObjectNode before = security.deepCopy();

        projection.project(security, List.of(auth("https://example.supabase.co")), null);

        assertThat(security).isEqualTo(before);
    }

    @Test
    void rejectsConflictingManualIdentityOrInsufficientManualScopes() throws Exception {
        for (String field : List.of("externalIdentityProvider", "externalIdentityIssuer", "externalIdentityJwkSetUri",
            "externalIdentityAudience", "externalIdentityGrantedScopes")) {
            ObjectNode security = manualBroker();
            security.put(field, "different");
            conflictWithoutMutation(security, security.deepCopy(), List.of(auth("https://example.supabase.co")), null);
        }
        ObjectNode security = manualBroker();
        conflictWithoutMutation(security, security.deepCopy(), List.of(auth("https://example.supabase.co")), handover());
    }

    @Test
    void removalRestoresAbsentAndPresentValuesAndPreservesUnrelatedChanges() throws Exception {
        ObjectNode security = security();
        security.put("externalIdentityProvider", "SUPABASE");
        security.put("externalIdentityGrantedScopes", "previous-disabled-scopes");
        security.putNull("externalIdentityAudience");
        ObjectNode before = security.deepCopy();
        projection.project(security, List.of(auth("https://example.supabase.co")), handover());
        security.put("privateRuntimeAcceptedIssuers", "reviewed-new-private");
        security.put("corsAllowedOrigins", "https://new-app.example");
        before.put("privateRuntimeAcceptedIssuers", "reviewed-new-private");
        before.put("corsAllowedOrigins", "https://new-app.example");

        projection.project(security, List.of(), null);

        assertThat(security).isEqualTo(before);
        assertThat(security.has(MANAGED)).isFalse();
    }

    @Test
    void removalRestoresAnOriginallyAbsentBrokerSwitch() throws Exception {
        ObjectNode security = security();
        security.remove("externalIdentityBrokerEnabled");
        ObjectNode before = security.deepCopy();
        projection.project(security, List.of(auth("https://example.supabase.co")), null);
        projection.project(security, List.of(), null);
        assertThat(security).isEqualTo(before);
    }

    @Test
    void everyManagedFieldDriftFailsClosedIncludingRemoval() throws Exception {
        for (String field : List.of("externalIdentityBrokerEnabled", "externalIdentityProvider", "externalIdentityIssuer",
            "externalIdentityJwkSetUri", "externalIdentityAudience", "externalIdentityGrantedScopes")) {
            ObjectNode security = security();
            projection.project(security, List.of(auth("https://example.supabase.co")), null);
            security.remove(field);
            ObjectNode drifted = security.deepCopy();
            conflictWithoutMutation(security, drifted, List.of(), null);
            conflictWithoutMutation(security, drifted, List.of(auth("https://example.supabase.co")), null);
        }
    }

    @Test
    void malformedOwnershipMetadataCannotRestoreOrOverwriteAnything() throws Exception {
        ObjectNode valid = security();
        projection.project(valid, List.of(auth("https://example.supabase.co")), null);
        for (String malformed : List.of("null", "[]", "{}", "{\"schemaVersion\":2,\"previous\":{},\"projected\":{}}")) {
            ObjectNode security = valid.deepCopy();
            security.set(MANAGED, mapper.readTree(malformed));
            conflictWithoutMutation(security, security.deepCopy(), List.of(), null);
        }
        for (String section : List.of("previous", "projected")) {
            ObjectNode security = valid.deepCopy();
            ((ObjectNode) security.path(MANAGED).path(section)).put("privateRuntimeAcceptedIssuers", "must-not-restore");
            conflictWithoutMutation(security, security.deepCopy(), List.of(), null);
        }
        ObjectNode security = valid.deepCopy();
        ((ObjectNode) security.path(MANAGED).path("projected")).put("externalIdentityBrokerEnabled", "true");
        conflictWithoutMutation(security, security.deepCopy(), List.of(), null);
    }

    @Test
    void invalidStoredProfilesAndUnknownPolicyFailWithoutMutation() throws Exception {
        for (String raw : List.of("not-json", "null", "[]", "{}")) {
            ObjectNode security = security();
            var installation = auth("https://example.supabase.co");
            installation.setAuthConfigurationJson(raw);
            conflictWithoutMutation(security, security.deepCopy(), List.of(installation), null);
        }
        ObjectNode security = security();
        var installation = auth("https://example.supabase.co");
        installation.setInteractionPolicy(null);
        conflictWithoutMutation(security, security.deepCopy(), List.of(installation), null);
    }

    @Test
    void conflictingNewProfileDoesNotPartiallyRestoreExistingProjection() throws Exception {
        ObjectNode security = security();
        projection.project(security, List.of(auth("https://example.supabase.co")), null);
        conflictWithoutMutation(security, security.deepCopy(),
            List.of(auth("https://example.supabase.co"), auth("https://different.supabase.co")), null);
    }

    @Test
    void malformedDisabledBrokerFieldsCannotBecomeUnrestorableMetadata() throws Exception {
        ObjectNode security = security();
        security.putArray("externalIdentityIssuer").add("invalid");
        conflictWithoutMutation(security, security.deepCopy(), List.of(auth("https://example.supabase.co")), null);
    }

    private void conflictWithoutMutation(ObjectNode security, ObjectNode before,
                                         List<AIWorkspaceInstallationEntity> installations, JsonNode behavior) {
        assertThatThrownBy(() -> projection.project(security, installations, behavior))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(CONFLICT));
        assertThat(security).isEqualTo(before);
    }

    private AIWorkspaceInstallationEntity auth(String projectUrl) {
        var installation = new AIWorkspaceInstallationEntity();
        installation.setInteractionPolicy(AIWorkspaceInteractionPolicy.PUBLIC_WITH_PROTECTED_CAPABILITIES);
        installation.setAuthProfileCode("supabase");
        installation.setAuthProfileVersion("1.0.0");
        ObjectNode configuration = mapper.createObjectNode();
        configuration.put("projectUrl", projectUrl);
        configuration.put("publishableKey", "sb_publishable_fixture");
        configuration.putArray("methods").add("email-password");
        installation.setAuthConfigurationJson(configuration.toString());
        return installation;
    }

    private ObjectNode security() throws Exception {
        return (ObjectNode) mapper.readTree("""
            {"externalIdentityBrokerEnabled":false,"privateRuntimeAcceptedIssuers":"private-original",
             "externalIdentityRequireVerifiedEmail":true,"corsAllowedOrigins":"https://app.example"}
            """);
    }

    private ObjectNode manualBroker() throws Exception {
        ObjectNode security = security();
        projection.project(security, List.of(auth("https://example.supabase.co")), null);
        security.remove(MANAGED);
        return security;
    }

    private JsonNode handover() throws Exception {
        return mapper.readTree("""
            {"executionExtensions":["CONVERSATION_HANDOVER"],"conversationHandover":{"enabled":true}}
            """);
    }
}
