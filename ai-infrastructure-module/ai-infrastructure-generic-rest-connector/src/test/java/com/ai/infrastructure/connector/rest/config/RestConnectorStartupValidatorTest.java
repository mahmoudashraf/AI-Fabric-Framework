package com.ai.infrastructure.connector.rest.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestConnectorStartupValidatorTest {

    @Test
    void acceptsBoundedDeploymentLocalHttpDataConfiguration() {
        assertThatCode(() -> new RestConnectorStartupValidator(config(), null, persistence()))
            .doesNotThrowAnyException();
    }

    @Test
    void acceptsBoundedQueryableSourceProjectionAction() {
        RestRoutingConfig config = config();
        RestRoutingConfig.ActionRoute route = sourceProjectionActionRoute();
        route.setMethod(null);
        config.setActions(Map.of("search_records", route));

        assertThatCode(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .doesNotThrowAnyException();
    }

    @Test
    void acceptsNumericAbsentValuesOnSourceProjectionFilters() {
        RestRoutingConfig config = config();
        RestRoutingConfig.ActionRoute route = sourceProjectionActionRoute();
        RestRoutingConfig.SourceProjectionFilter filter = route.getSourceProjection().getFilters().getFirst();
        filter.setOperator(RestRoutingConfig.SourceProjectionFilterOperator.NUMBER_LESS_THAN_OR_EQUAL);
        filter.setAbsentValues(List.of("0", "0.0"));
        config.setActions(Map.of("search_records", route));

        assertThatCode(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsNonNumericAbsentValuesOnNumericSourceProjectionFilters() {
        RestRoutingConfig config = config();
        RestRoutingConfig.ActionRoute route = sourceProjectionActionRoute();
        RestRoutingConfig.SourceProjectionFilter filter = route.getSourceProjection().getFilters().getFirst();
        filter.setOperator(RestRoutingConfig.SourceProjectionFilterOperator.NUMBER_LESS_THAN_OR_EQUAL);
        filter.setAbsentValues(List.of("not-a-number"));
        config.setActions(Map.of("search_records", route));

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("numeric source projection absent-values must be numeric");
    }

    @Test
    void rejectsSourceProjectionActionMixedWithHttpRouteTarget() {
        RestRoutingConfig config = config();
        RestRoutingConfig.ActionRoute route = sourceProjectionActionRoute();
        route.setPath("/records");
        config.setActions(Map.of("search_records", route));

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("source-projection is mutually exclusive");
    }

    @Test
    void rejectsSourceProjectionActionWithoutDurablePersistence() {
        RestRoutingConfig config = config();
        config.setActions(Map.of("search_records", sourceProjectionActionRoute()));
        RestConnectorServiceProperties properties = new RestConnectorServiceProperties();

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("source projection action routes require rest-connector.persistence.enabled=true");
    }

    @Test
    void rejectsSourceProjectionActionThatExposesAnUnmappedField() {
        RestRoutingConfig config = config();
        RestRoutingConfig.ActionRoute route = sourceProjectionActionRoute();
        route.getSourceProjection().setOutputFields(List.of("title", "providerSecret"));
        config.setActions(Map.of("search_records", route));

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("output-fields must use mapped entity fields");
    }

    @Test
    void acceptsFailClosedCollectionFilteringOnProtectedProviderActions() {
        RestRoutingConfig config = config();
        RestRoutingConfig.ActionRoute route = providerActionRoute();
        RestRoutingConfig.ResponseCollectionFilter filter = new RestRoutingConfig.ResponseCollectionFilter();
        filter.setCollectionJsonPointer("/results");
        filter.setCountJsonPointer("/totalResults");
        RestRoutingConfig.RecordInclusionCondition condition = new RestRoutingConfig.RecordInclusionCondition();
        condition.setJsonPointer("/publication/status");
        condition.setAllowedValues(List.of("PUBLISHED"));
        filter.setInclusionConditions(List.of(condition));
        route.getResponse().setCollectionFilters(List.of(filter));
        config.setActions(Map.of("provider_search", route));

        assertThatCode(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .doesNotThrowAnyException();
    }

    @Test
    void acceptsExplicitCollectionFieldProjectionOnProtectedProviderActions() {
        RestRoutingConfig config = config();
        RestRoutingConfig.ActionRoute route = providerActionRoute();
        RestRoutingConfig.ResponseCollectionFieldProjection projection =
            new RestRoutingConfig.ResponseCollectionFieldProjection();
        projection.setCollectionJsonPointer("/results");
        projection.setFields(Map.of("recordId", "/metadata/recordId"));
        route.getResponse().setCollectionFieldProjections(List.of(projection));
        config.setActions(Map.of("provider_search", route));

        assertThatCode(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsCollectionFieldProjectionOnOrdinaryApplicationActions() {
        RestRoutingConfig config = config();
        config.getConnector().getUpstream().setBaseUrl("https://application.fixture.invalid");
        RestRoutingConfig.ActionRoute route = new RestRoutingConfig.ActionRoute();
        route.setPath("/api/search");
        RestRoutingConfig.ResponseCollectionFieldProjection projection =
            new RestRoutingConfig.ResponseCollectionFieldProjection();
        projection.setCollectionJsonPointer("/results");
        projection.setFields(Map.of("recordId", "/metadata/recordId"));
        route.getResponse().setCollectionFieldProjections(List.of(projection));
        config.setActions(Map.of("application_search", route));

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("collection-field-projections are supported only for protected provider routes");
    }

    @Test
    void rejectsCollectionFilteringOnOrdinaryApplicationActions() {
        RestRoutingConfig config = config();
        config.getConnector().getUpstream().setBaseUrl("https://application.fixture.invalid");
        RestRoutingConfig.ActionRoute route = new RestRoutingConfig.ActionRoute();
        route.setPath("/api/search");
        RestRoutingConfig.ResponseCollectionFilter filter = new RestRoutingConfig.ResponseCollectionFilter();
        filter.setCollectionJsonPointer("/results");
        RestRoutingConfig.RecordInclusionCondition condition = new RestRoutingConfig.RecordInclusionCondition();
        condition.setJsonPointer("/publication/status");
        condition.setAllowedValues(List.of("PUBLISHED"));
        filter.setInclusionConditions(List.of(condition));
        route.getResponse().setCollectionFilters(List.of(filter));
        config.setActions(Map.of("application_search", route));

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("supported only for protected provider routes");
    }

    @Test
    void rejectsNonHttpsProviderEndpoint() {
        RestRoutingConfig config = config();
        config.getConnectionProfiles().get("neutral-provider").setBaseUrl("http://provider.fixture.invalid");

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("valid absolute HTTPS URL");
    }

    @Test
    void rejectsUnsafePaginationFieldName() {
        RestRoutingConfig config = config();
        RestRoutingConfig.Pagination pagination = config.getDataSources().get("neutral-source").getPagination();
        pagination.setStrategy(RestRoutingConfig.Pagination.Strategy.PAGE_SIZE);
        pagination.setPageQuery("page\nheader");

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("pagination.page-query is invalid");
    }

    @Test
    void rejectsInvalidCompleteHttpStatuses() {
        RestRoutingConfig config = config();
        config.getDataSources().get("neutral-source").setCompleteHttpStatuses(List.of(200, 206, 206));

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unique complete HTTP statuses");

        config.getDataSources().get("neutral-source").setCompleteHttpStatuses(List.of(200, 304));

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unique complete HTTP statuses");
    }

    @Test
    void rejectsDataSourceWithoutTrustedResourceProjection() {
        RestRoutingConfig config = config();
        config.getDataSources().get("neutral-source").getMapping().setResourceJsonPointer(null);

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("mapping.resource-json-pointer");
    }

    @Test
    void rejectsDataSourceWithoutServerOwnedKnowledgeSourceHandle() {
        RestRoutingConfig config = config();
        config.getDataSources().get("neutral-source").setKnowledgeSourceHandleRef(null);

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("knowledge-source-handle-ref");
    }

    @Test
    void rejectsProtectedResourcePlacementIntoProviderAuthenticationHeader() {
        RestRoutingConfig config = config();
        config.getDataSources().get("neutral-source").setPath("/records");
        config.getDataSources().get("neutral-source").getTrustedResourcePlacements().get(0)
            .setTarget(RestRoutingConfig.ResourcePlacement.Target.HEADER);
        config.getDataSources().get("neutral-source").getTrustedResourcePlacements().get(0)
            .setField("X-Provider-Key");

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must not target the provider authentication header");
    }

    @Test
    void rejectsUnboundProtectedPathPlaceholder() {
        RestRoutingConfig config = config();
        config.getDataSources().get("neutral-source").setPath("/scopes/{scope}/records");

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unbound protected path placeholder");
    }

    @Test
    void rejectsWebhookWhoseProtectedResourceDiffersFromItsReconciliationSource() {
        RestRoutingConfig config = config();
        config.setProtectedResources(new LinkedHashMap<>(config.getProtectedResources()));
        RestRoutingConfig.ProtectedResourceBinding other = new RestRoutingConfig.ProtectedResourceBinding();
        other.setConnectionProfileRef("neutral-provider");
        other.setEnvironment("fixture");
        other.setResourceType("tenant-scope");
        other.setResourceId("scope-2");
        other.setCapabilityGrants(List.of("records:read"));
        config.getProtectedResources().put("other-scope", other);

        RestRoutingConfig.WebhookSource webhook = new RestRoutingConfig.WebhookSource();
        webhook.setProtectedResourceBindingRef("other-scope");
        webhook.setReconcileDataSourceRef("neutral-source");
        webhook.setEventIdJsonPointer("/eventId");
        webhook.setEventTypeJsonPointer("/eventType");
        webhook.setResourceJsonPointer("/scopeId");
        webhook.setAllowedEventTypes(List.of("record.changed"));
        webhook.getVerification().setStrategy(
            RestRoutingConfig.WebhookVerification.Strategy.HMAC_SHA256_TIMESTAMP_DOT_RAW_BODY
        );
        webhook.getVerification().setSignatureHeader("X-Provider-Signature");
        webhook.getVerification().setSecret("resolved-webhook-secret");
        config.getWebhooks().put("neutral-webhook", webhook);

        assertThatThrownBy(() -> new RestConnectorStartupValidator(config, null, persistence()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("same protected resource binding");
    }

    private RestRoutingConfig config() {
        RestRoutingConfig config = new RestRoutingConfig();
        config.getConnector().getInboundAuth().setAllowUnauthenticated(true);

        RestRoutingConfig.ConnectionProfile profile = new RestRoutingConfig.ConnectionProfile();
        profile.setEnvironment("fixture");
        profile.setBaseUrl("https://provider.fixture.invalid");
        profile.setAllowedHosts(List.of("provider.fixture.invalid"));
        profile.setCapabilityGrants(List.of("records:read"));
        profile.getAuth().setStrategy(RestRoutingConfig.ProviderAuth.Strategy.API_KEY);
        profile.getAuth().setApiKeyHeader("X-Provider-Key");
        profile.getAuth().setApiKeyValue("resolved-secret");
        config.setConnectionProfiles(Map.of("neutral-provider", profile));

        RestRoutingConfig.ProtectedResourceBinding resource = new RestRoutingConfig.ProtectedResourceBinding();
        resource.setConnectionProfileRef("neutral-provider");
        resource.setEnvironment("fixture");
        resource.setResourceType("tenant-scope");
        resource.setResourceId("scope-1");
        resource.setCapabilityGrants(List.of("records:read"));
        config.setProtectedResources(Map.of("neutral-scope", resource));

        RestRoutingConfig.HttpDataSource source = new RestRoutingConfig.HttpDataSource();
        source.setSourceVersion("fixture-v1");
        source.setConnectionProfileRef("neutral-provider");
        source.setProtectedResourceBindingRef("neutral-scope");
        source.setRequiredCapabilityGrants(List.of("records:read"));
        source.setPath("/records");
        source.setVectorSpace("neutral-record");
        source.setEntityType("neutral-record");
        source.setKnowledgeSourceHandleRef("plugin/fixture/neutral-record");
        RestRoutingConfig.ResourcePlacement placement = new RestRoutingConfig.ResourcePlacement();
        placement.setTarget(RestRoutingConfig.ResourcePlacement.Target.QUERY);
        placement.setField("scopeId");
        source.setTrustedResourcePlacements(List.of(placement));
        source.getMapping().setRecordsJsonPointer("/records");
        source.getMapping().setIdJsonPointer("/id");
        source.getMapping().setResourceJsonPointer("/scopeId");
        source.getMapping().setContentFields(Map.of("title", "/title"));
        source.getMapping().setEntityFields(Map.of("title", "/title"));
        config.setDataSources(Map.of("neutral-source", source));

        config.getRuntimeDataSync().setEnabled(true);
        config.getRuntimeDataSync().setBaseUrl("http://runtime.internal");
        config.getRuntimeDataSync().setApiKeyValue("runtime-secret");
        config.getRuntimeDataSync().setDeploymentId("dep-1");
        config.getRuntimeDataSync().setTenantId("tenant-1");
        return config;
    }

    private RestRoutingConfig.ActionRoute providerActionRoute() {
        RestRoutingConfig.ActionRoute route = new RestRoutingConfig.ActionRoute();
        route.setConnectionProfileRef("neutral-provider");
        route.setProtectedResourceBindingRef("neutral-scope");
        route.setRequiredCapabilityGrants(List.of("records:read"));
        route.setPath("/records");
        RestRoutingConfig.ResourcePlacement placement = new RestRoutingConfig.ResourcePlacement();
        placement.setTarget(RestRoutingConfig.ResourcePlacement.Target.QUERY);
        placement.setField("scopeId");
        route.setTrustedResourcePlacements(List.of(placement));
        return route;
    }

    private RestRoutingConfig.ActionRoute sourceProjectionActionRoute() {
        RestRoutingConfig.ActionRoute route = new RestRoutingConfig.ActionRoute();
        RestRoutingConfig.SourceProjectionQuery projection = new RestRoutingConfig.SourceProjectionQuery();
        projection.setSourceRef("neutral-source");
        projection.setOutputFields(List.of("title"));
        RestRoutingConfig.SourceProjectionFilter filter = new RestRoutingConfig.SourceProjectionFilter();
        filter.setParam("title");
        filter.setFields(List.of("title"));
        filter.setOperator(RestRoutingConfig.SourceProjectionFilterOperator.EQUALS_IGNORE_CASE);
        projection.setFilters(List.of(filter));
        route.setSourceProjection(projection);
        return route;
    }

    private RestConnectorServiceProperties persistence() {
        RestConnectorServiceProperties properties = new RestConnectorServiceProperties();
        properties.getPersistence().setEnabled(true);
        properties.getPersistence().setJdbcUrl("jdbc:postgresql://db.internal:5432/platform");
        properties.getPersistence().setUsername("integration_connector");
        properties.getPersistence().setPassword("resolved-db-secret");
        return properties;
    }
}
