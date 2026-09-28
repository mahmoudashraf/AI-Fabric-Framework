package com.ai.infrastructure.connector.rest.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RestConnectorStartupValidator {

    private static final Pattern PATH_PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9._-]+)}");

    public RestConnectorStartupValidator(
        RestRoutingConfig config,
        Validator validator,
        RestConnectorServiceProperties serviceProperties
    ) {
        validate(config, validator, serviceProperties);
    }

    private void validate(
        RestRoutingConfig config,
        Validator validator,
        RestConnectorServiceProperties serviceProperties
    ) {
        if (config == null) {
            throw new IllegalStateException("RestRoutingConfig is required.");
        }

        if (validator != null) {
            Set<ConstraintViolation<RestRoutingConfig>> violations = validator.validate(config);
            if (violations != null && !violations.isEmpty()) {
                String first = violations.iterator().next().getPropertyPath() + " " + violations.iterator().next().getMessage();
                throw new IllegalStateException("Invalid routing config: " + first);
            }
        }

        validateInboundAuth(config);
        validatePersistence(serviceProperties);
        validateUpstream(config);
        validateAuthz(config);
        validateConnectionProfiles(config);
        validateProtectedResources(config);
        validateRoutes(config);
        validateDataSources(config, serviceProperties);
        validateWebhooks(config, serviceProperties);
        validateRuntimeDataSync(config);
    }

    private void validatePersistence(RestConnectorServiceProperties serviceProperties) {
        RestConnectorServiceProperties.Persistence persistence = serviceProperties != null
            ? serviceProperties.getPersistence()
            : null;
        if (persistence == null || !persistence.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(persistence.getJdbcUrl())
            || !StringUtils.hasText(persistence.getUsername())
            || !StringUtils.hasText(persistence.getPassword())) {
            throw new IllegalStateException("Enabled connector persistence requires JDBC URL, username, and password.");
        }
        requireDatabaseIdentifier(persistence.getSchema(), "connector persistence schema");
        boolean bootstrapConfigured = StringUtils.hasText(persistence.getBootstrapJdbcUrl())
            || StringUtils.hasText(persistence.getBootstrapUsername())
            || StringUtils.hasText(persistence.getBootstrapPassword())
            || StringUtils.hasText(persistence.getRoleName());
        if (bootstrapConfigured) {
            if (!StringUtils.hasText(persistence.getBootstrapJdbcUrl())
                || !StringUtils.hasText(persistence.getBootstrapUsername())
                || !StringUtils.hasText(persistence.getBootstrapPassword())
                || !StringUtils.hasText(persistence.getRoleName())) {
                throw new IllegalStateException("Connector persistence bootstrap configuration is incomplete.");
            }
            requireDatabaseIdentifier(persistence.getRoleName(), "connector persistence role");
            if (!persistence.getRoleName().equals(persistence.getUsername())) {
                throw new IllegalStateException("Connector persistence role and operational username must match.");
            }
        }
    }

    private void validateInboundAuth(RestRoutingConfig config) {
        RestRoutingConfig.Connector connector = config.getConnector();
        RestRoutingConfig.InboundAuth auth = connector != null ? connector.getInboundAuth() : null;
        if (auth == null) {
            throw new IllegalStateException("connector.inbound-auth is required.");
        }

        RestRoutingConfig.ApiKey apiKey = auth.getApiKey();
        boolean apiKeyEnabled = apiKey != null && apiKey.isEnabled();

        if (!auth.isAllowUnauthenticated() && !apiKeyEnabled) {
            throw new IllegalStateException("Inbound auth is not configured. Enable connector.inbound-auth.api-key.enabled (or explicitly set connector.inbound-auth.allow-unauthenticated=true).");
        }

        if (apiKeyEnabled && (!StringUtils.hasText(apiKey.getHeader()) || !StringUtils.hasText(apiKey.getValue()))) {
            throw new IllegalStateException("connector.inbound-auth.api-key.enabled=true but header/value is missing.");
        }
    }

    private void validateUpstream(RestRoutingConfig config) {
        RestRoutingConfig.Connector connector = config.getConnector();
        RestRoutingConfig.Upstream upstream = connector != null ? connector.getUpstream() : null;
        if (upstream == null) {
            return;
        }

        if (StringUtils.hasText(upstream.getBaseUrl())) {
            validateUrl(upstream.getBaseUrl().trim(), "connector.upstream.base-url");
        }
    }

    private void validateAuthz(RestRoutingConfig config) {
        RestRoutingConfig.Authz authz = config != null ? config.getAuthz() : null;
        if (authz == null || !authz.isEnabled()) {
            return;
        }

        RestRoutingConfig.Upstream upstream = authz.getUpstream();
        String baseUrl = upstream != null ? upstream.getBaseUrl() : null;
        if (!StringUtils.hasText(baseUrl)) {
            // Convenience: allow reusing connector upstream baseUrl when authz is served by the same upstream.
            RestRoutingConfig.Connector connector = config.getConnector();
            RestRoutingConfig.Upstream connectorUpstream = connector != null ? connector.getUpstream() : null;
            baseUrl = connectorUpstream != null ? connectorUpstream.getBaseUrl() : null;
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("authz.enabled=true but authz.upstream.base-url is missing.");
        }
        validateUrl(baseUrl.trim(), "authz.upstream.base-url");

        String path = authz.getPath();
        if (!StringUtils.hasText(path)) {
            throw new IllegalStateException("authz.enabled=true but authz.path is missing.");
        }
        String p = path.trim();
        if (p.contains("://")) {
            throw new IllegalStateException("authz.path must be a relative path (no scheme).");
        }
        if (!p.startsWith("/")) {
            throw new IllegalStateException("authz.path must start with '/'.");
        }
    }

    private void validateRoutes(RestRoutingConfig config) {
        if (config.getActions() == null || config.getActions().isEmpty()) {
            log.warn("No actions configured under 'actions'. /actions/execute will return ACTION_NOT_SUPPORTED.");
            return;
        }

        RestRoutingConfig.Connector connector = config.getConnector();
        String baseUrl = connector != null && connector.getUpstream() != null ? connector.getUpstream().getBaseUrl() : null;
        boolean baseUrlPresent = StringUtils.hasText(baseUrl);

        config.getActions().forEach((actionId, route) -> {
            if (!StringUtils.hasText(actionId)) {
                throw new IllegalStateException("Action route key must not be blank.");
            }
            if (route == null) {
                throw new IllegalStateException("Action route '" + actionId.trim() + "' is null.");
            }

            String url = route.getUrl();
            String path = route.getPath();
            boolean profileRoute = StringUtils.hasText(route.getConnectionProfileRef());
            if (!StringUtils.hasText(url) && !StringUtils.hasText(path)) {
                throw new IllegalStateException("Action route '" + actionId.trim() + "' must set either url or path.");
            }

            if (StringUtils.hasText(url)) {
                if (profileRoute) {
                    throw new IllegalStateException("Action route '" + actionId.trim() + "' cannot use an absolute url with connection-profile-ref.");
                }
                validateUrl(url.trim(), "actions." + actionId.trim() + ".url");
            } else {
                if (!baseUrlPresent && !profileRoute) {
                    throw new IllegalStateException("Action route '" + actionId.trim() + "' uses path but connector.upstream.base-url is missing.");
                }
                String p = path.trim();
                if (p.contains("://")) {
                    throw new IllegalStateException("Action route '" + actionId.trim() + "': path must be a relative path (no scheme).");
                }
                if (!p.startsWith("/")) {
                    throw new IllegalStateException("Action route '" + actionId.trim() + "': path must start with '/'.");
                }
            }

            if (profileRoute) {
                RestRoutingConfig.ConnectionProfile profile = requireConnectionProfile(
                    config, route.getConnectionProfileRef(), "actions." + actionId
                );
                if (!StringUtils.hasText(route.getProtectedResourceBindingRef())) {
                    throw new IllegalStateException("Action route '" + actionId.trim() + "' must declare protected-resource-binding-ref.");
                }
                requireBinding(config, route.getProtectedResourceBindingRef(), route.getConnectionProfileRef(), "actions." + actionId);
                validatePlacements(
                    route.getTrustedResourcePlacements(),
                    route.getPath(),
                    "actions." + actionId + ".trusted-resource-placements"
                );
                rejectAuthHeaderPlacement(
                    profile,
                    route.getTrustedResourcePlacements(),
                    "actions." + actionId + ".trusted-resource-placements"
                );
                requireCapabilities(
                    profile,
                    config.getProtectedResources().get(route.getProtectedResourceBindingRef()),
                    route.getRequiredCapabilityGrants(),
                    "actions." + actionId
                );
                requireCapabilityList(route.getRequiredCapabilityGrants(), "actions." + actionId + ".required-capability-grants");
                if (StringUtils.hasText(route.getIdempotencyHeader())
                    && !route.getIdempotencyHeader().trim().matches("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,160}")) {
                    throw new IllegalStateException(
                        "Action route '" + actionId.trim() + "': idempotency-header is not a valid HTTP header name."
                    );
                }
            }

            String method = route.getMethod();
            if (!StringUtils.hasText(method)) {
                throw new IllegalStateException("Action route '" + actionId.trim() + "': method is required.");
            }
            if (profileRoute && !Set.of("GET", "POST", "PUT", "PATCH", "DELETE")
                .contains(method.trim().toUpperCase(Locale.ROOT))) {
                throw new IllegalStateException(
                    "Action route '" + actionId.trim() + "': provider routes support GET, POST, PUT, PATCH, or DELETE."
                );
            }

            if (profileRoute) {
                validateStaticHeaders(route.getHeaders(), "actions." + actionId + ".headers");
                if (route.getRequest() != null) {
                    validateStaticQuery(route.getRequest().getQuery(), "actions." + actionId + ".request.query");
                }
            }

            Integer timeoutMs = route.getTimeoutMs();
            if (timeoutMs != null && (timeoutMs < 100 || timeoutMs > 120_000)) {
                throw new IllegalStateException("Action route '" + actionId.trim() + "': timeout-ms must be between 100 and 120000.");
            }

            RestRoutingConfig.Response response = route.getResponse();
            if (response != null && response.getSuccessHttpStatus() != null) {
                for (Integer status : response.getSuccessHttpStatus()) {
                    if (status == null) {
                        continue;
                    }
                    if (status < 100 || status > 599) {
                        throw new IllegalStateException("Action route '" + actionId.trim() + "': response.success-http-status must be valid HTTP codes.");
                    }
                }
            }
        });
    }

    private void validateConnectionProfiles(RestRoutingConfig config) {
        if (config.getConnectionProfiles() == null) {
            throw new IllegalStateException("connection-profiles must be an object.");
        }
        config.getConnectionProfiles().forEach((profileId, profile) -> {
            requireId(profileId, "connection profile");
            if (profile == null || !StringUtils.hasText(profile.getEnvironment())) {
                throw new IllegalStateException("Connection profile '" + profileId + "' must declare environment.");
            }
            if (!StringUtils.hasText(profile.getBaseUrl())) {
                throw new IllegalStateException("Connection profile '" + profileId + "' must declare base-url.");
            }
            validateHttpsUrl(profile.getBaseUrl(), "connection-profiles." + profileId + ".base-url");
            URI baseUri = URI.create(profile.getBaseUrl());
            if (profile.getAllowedHosts() == null || profile.getAllowedHosts().isEmpty()
                || profile.getAllowedHosts().stream().anyMatch(host -> !validHostName(host))
                || profile.getAllowedHosts().stream().noneMatch(baseUri.getHost()::equalsIgnoreCase)) {
                throw new IllegalStateException("Connection profile '" + profileId + "' must allowlist its base-url host.");
            }
            requireCapabilityList(profile.getCapabilityGrants(), "connection-profiles." + profileId + ".capability-grants");
            if (profile.getCorrelationResponseHeaders() != null
                && profile.getCorrelationResponseHeaders().stream().anyMatch(header ->
                    !StringUtils.hasText(header)
                        || !header.trim().matches("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,160}"))) {
                throw new IllegalStateException("Connection profile '" + profileId + "' contains an invalid correlation response header.");
            }
            RestRoutingConfig.ProviderAuth auth = profile.getAuth();
            if (auth == null || auth.getStrategy() == null) {
                throw new IllegalStateException("Connection profile '" + profileId + "' must declare an auth strategy.");
            }
            if (auth.getStrategy() == RestRoutingConfig.ProviderAuth.Strategy.API_KEY) {
                requireResolvedSecret(auth.getApiKeyValue(), "connection-profiles." + profileId + ".auth.api-key-value");
                if (!validHeaderName(auth.getApiKeyHeader())) {
                    throw new IllegalStateException("Connection profile '" + profileId + "' API_KEY auth requires api-key-header.");
                }
            }
            if (auth.getStrategy() == RestRoutingConfig.ProviderAuth.Strategy.FORM_TOKEN_EXCHANGE) {
                if (StringUtils.hasText(auth.getTokenBaseUrl())) {
                    validateHttpsUrl(
                        auth.getTokenBaseUrl(),
                        "connection-profiles." + profileId + ".auth.token-base-url"
                    );
                    URI tokenBaseUri = URI.create(auth.getTokenBaseUrl());
                    if (profile.getAllowedHosts() == null
                        || profile.getAllowedHosts().stream().noneMatch(tokenBaseUri.getHost()::equalsIgnoreCase)) {
                        throw new IllegalStateException(
                            "Connection profile '" + profileId + "' must allowlist its token-base-url host."
                        );
                    }
                }
                requireRelativePath(auth.getTokenPath(), "connection-profiles." + profileId + ".auth.token-path");
                if (!"POST".equalsIgnoreCase(auth.getTokenMethod())) {
                    throw new IllegalStateException("Connection profile '" + profileId + "' FORM_TOKEN_EXCHANGE must use POST.");
                }
                if (auth.getCredentialFields() == null || auth.getCredentialFields().isEmpty()) {
                    throw new IllegalStateException("Connection profile '" + profileId + "' token exchange requires credential-fields.");
                }
                auth.getCredentialFields().forEach((name, value) -> {
                    requireId(name, "token credential field");
                    requireResolvedSecret(value, "connection-profiles." + profileId + ".auth.credential-fields." + name);
                });
                if (auth.getCredentialFields().size() > 20) {
                    throw new IllegalStateException(
                        "Connection profile '" + profileId + "' token exchange supports at most 20 credential fields."
                    );
                }
                if (auth.getStaticFields() != null) {
                    if (auth.getStaticFields().size() > 20) {
                        throw new IllegalStateException(
                            "Connection profile '" + profileId + "' token exchange supports at most 20 static fields."
                        );
                    }
                    auth.getStaticFields().forEach((name, value) -> {
                        requireId(name, "token static field");
                        if (value == null || value.length() > 500) {
                            throw new IllegalStateException(
                                "Connection profile '" + profileId + "' token static fields must be at most 500 characters."
                            );
                        }
                        if (auth.getCredentialFields().containsKey(name)) {
                            throw new IllegalStateException(
                                "Connection profile '" + profileId + "' token static fields must not redefine credential field " + name + "."
                            );
                        }
                    });
                }
                requireJsonPointer(auth.getTokenJsonPointer(), "connection-profiles." + profileId + ".auth.token-json-pointer");
                boolean absoluteExpiry = StringUtils.hasText(auth.getAbsoluteExpiryJsonPointer());
                boolean relativeExpiry = StringUtils.hasText(auth.getRelativeExpiryJsonPointer());
                if (absoluteExpiry == relativeExpiry) {
                    throw new IllegalStateException("Connection profile '" + profileId + "' must declare exactly one token expiry pointer.");
                }
                requireJsonPointer(
                    absoluteExpiry ? auth.getAbsoluteExpiryJsonPointer() : auth.getRelativeExpiryJsonPointer(),
                    "connection-profiles." + profileId + ".auth.expiry"
                );
                if (!validHeaderName(auth.getAuthorizationHeader())) {
                    throw new IllegalStateException("Connection profile '" + profileId + "' token exchange requires authorization-header.");
                }
                if (!StringUtils.hasText(auth.getAuthorizationScheme())
                    || !auth.getAuthorizationScheme().trim().matches("[A-Za-z][A-Za-z0-9._-]{0,39}")) {
                    throw new IllegalStateException(
                        "Connection profile '" + profileId + "' token exchange requires a valid authorization-scheme."
                    );
                }
            }
            RestRoutingConfig.RatePolicy ratePolicy = profile.getRatePolicy();
            if (ratePolicy == null || ratePolicy.getRetryStatuses() == null || ratePolicy.getRetryStatuses().isEmpty()) {
                throw new IllegalStateException("Connection profile '" + profileId + "' must declare at least one retry status.");
            }
            if (ratePolicy.getRetryStatuses().stream().anyMatch(status -> status == null || status < 100 || status > 599)) {
                throw new IllegalStateException("Connection profile '" + profileId + "' contains an invalid retry status.");
            }
            if (profile.getErrorMappings() != null) {
                for (RestRoutingConfig.ErrorMapping mapping : profile.getErrorMappings()) {
                    if (mapping == null || mapping.getErrorClass() == null) {
                        throw new IllegalStateException("Connection profile '" + profileId + "' contains an incomplete error mapping.");
                    }
                    boolean hasPointer = StringUtils.hasText(mapping.getBodyJsonPointer());
                    boolean hasExpected = mapping.getEqualsValue() != null;
                    if (hasPointer != hasExpected) {
                        throw new IllegalStateException("Connection profile '" + profileId + "' error mapping must declare both body-json-pointer and equals-value, or neither.");
                    }
                    if (hasPointer) {
                        requireJsonPointer(mapping.getBodyJsonPointer(), "connection-profiles." + profileId + ".error-mappings.body-json-pointer");
                    }
                }
            }
        });
    }

    private boolean validHeaderName(String value) {
        return StringUtils.hasText(value)
            && value.trim().matches("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,160}");
    }

    private void validateProtectedResources(RestRoutingConfig config) {
        if (config.getProtectedResources() == null) {
            throw new IllegalStateException("protected-resources must be an object.");
        }
        config.getProtectedResources().forEach((bindingId, binding) -> {
            requireId(bindingId, "protected resource binding");
            if (binding == null || !StringUtils.hasText(binding.getConnectionProfileRef())
                || !StringUtils.hasText(binding.getEnvironment()) || !StringUtils.hasText(binding.getResourceType())
                || !StringUtils.hasText(binding.getResourceId())) {
                throw new IllegalStateException("Protected resource binding '" + bindingId + "' is incomplete.");
            }
            RestRoutingConfig.ConnectionProfile profile = requireConnectionProfile(
                config, binding.getConnectionProfileRef(), "protected-resources." + bindingId
            );
            if (!binding.getEnvironment().equalsIgnoreCase(profile.getEnvironment())) {
                throw new IllegalStateException("Protected resource binding '" + bindingId + "' environment does not match its profile.");
            }
            requireId(binding.getResourceType(), "protected resource type");
            if (binding.getResourceId().length() > 500) {
                throw new IllegalStateException("Protected resource binding '" + bindingId + "' resource ID exceeds the supported boundary.");
            }
            requireCapabilityList(binding.getCapabilityGrants(), "protected-resources." + bindingId + ".capability-grants");
        });
    }

    private void validateDataSources(RestRoutingConfig config, RestConnectorServiceProperties serviceProperties) {
        if (config.getDataSources() == null || config.getDataSources().isEmpty()) {
            return;
        }
        requirePersistence(serviceProperties, "HTTP data sources");
        config.getDataSources().forEach((sourceId, source) -> {
            requireId(sourceId, "HTTP data source");
            if (source == null || !StringUtils.hasText(source.getPath()) || !StringUtils.hasText(source.getVectorSpace())
                || !StringUtils.hasText(source.getEntityType())) {
                throw new IllegalStateException("HTTP data source '" + sourceId + "' is incomplete.");
            }
            requireId(source.getSourceVersion(), "HTTP data source version");
            requireId(source.getVectorSpace(), "HTTP data source vector space");
            requireId(source.getEntityType(), "HTTP data source entity type");
            requireKnowledgeSourceHandleRef(
                source.getKnowledgeSourceHandleRef(),
                "data-sources." + sourceId + ".knowledge-source-handle-ref"
            );
            requireRelativePath(source.getPath(), "data-sources." + sourceId + ".path");
            if (!("GET".equalsIgnoreCase(source.getMethod()) || "POST".equalsIgnoreCase(source.getMethod()))) {
                throw new IllegalStateException("HTTP data source '" + sourceId + "' must use GET or POST.");
            }
            if (source.getCompleteHttpStatuses() == null || source.getCompleteHttpStatuses().isEmpty()
                || source.getCompleteHttpStatuses().size() > 10
                || source.getCompleteHttpStatuses().stream().anyMatch(status -> status == null || status < 200 || status > 299)
                || source.getCompleteHttpStatuses().stream().distinct().count() != source.getCompleteHttpStatuses().size()) {
                throw new IllegalStateException(
                    "HTTP data source '" + sourceId
                        + "' must declare between 1 and 10 unique complete HTTP statuses in the 2xx range."
                );
            }
            RestRoutingConfig.ConnectionProfile profile = requireConnectionProfile(
                config, source.getConnectionProfileRef(), "data-sources." + sourceId
            );
            requireBinding(config, source.getProtectedResourceBindingRef(), source.getConnectionProfileRef(), "data-sources." + sourceId);
            requireCapabilities(
                profile,
                config.getProtectedResources().get(source.getProtectedResourceBindingRef()),
                source.getRequiredCapabilityGrants(),
                "data-sources." + sourceId
            );
            requireCapabilityList(
                source.getRequiredCapabilityGrants(),
                "data-sources." + sourceId + ".required-capability-grants"
            );
            validatePlacements(
                source.getTrustedResourcePlacements(),
                source.getPath(),
                "data-sources." + sourceId + ".trusted-resource-placements"
            );
            rejectAuthHeaderPlacement(
                profile,
                source.getTrustedResourcePlacements(),
                "data-sources." + sourceId + ".trusted-resource-placements"
            );
            validateStaticQuery(source.getQuery(), "data-sources." + sourceId + ".query");
            validateStaticHeaders(source.getHeaders(), "data-sources." + sourceId + ".headers");
            RestRoutingConfig.RecordMapping mapping = source.getMapping();
            if (mapping == null || !StringUtils.hasText(mapping.getIdJsonPointer())
                || mapping.getContentFields() == null || mapping.getContentFields().isEmpty()) {
                throw new IllegalStateException("HTTP data source '" + sourceId + "' requires id and content mappings.");
            }
            requireJsonPointer(mapping.getIdJsonPointer(), "data-sources." + sourceId + ".mapping.id-json-pointer");
            if (StringUtils.hasText(mapping.getRecordsJsonPointer())) {
                requireJsonPointer(mapping.getRecordsJsonPointer(), "data-sources." + sourceId + ".mapping.records-json-pointer");
            }
            requireJsonPointer(
                mapping.getResourceJsonPointer(),
                "data-sources." + sourceId + ".mapping.resource-json-pointer"
            );
            mapping.getContentFields().values().forEach(pointer -> requireJsonPointer(pointer, "data source content mapping"));
            mapping.getEntityFields().values().forEach(pointer -> requireJsonPointer(pointer, "data source entity mapping"));
            mapping.getMetadataFields().values().forEach(pointer -> requireJsonPointer(pointer, "data source metadata mapping"));
            validateProjectionMap(mapping.getContentFields(), "data-sources." + sourceId + ".mapping.content-fields");
            validateProjectionMap(mapping.getEntityFields(), "data-sources." + sourceId + ".mapping.entity-fields");
            validateProjectionMap(mapping.getMetadataFields(), "data-sources." + sourceId + ".mapping.metadata-fields");
            RestRoutingConfig.Pagination pagination = source.getPagination();
            if (pagination == null || pagination.getStrategy() == null) {
                throw new IllegalStateException("HTTP data source '" + sourceId + "' requires a pagination strategy.");
            }
            if (pagination.getStrategy() == RestRoutingConfig.Pagination.Strategy.PAGE_SIZE) {
                requireRequestFieldName(pagination.getPageQuery(), "data-sources." + sourceId + ".pagination.page-query");
                requireRequestFieldName(pagination.getSizeQuery(), "data-sources." + sourceId + ".pagination.size-query");
            }
            if (pagination.getStrategy() == RestRoutingConfig.Pagination.Strategy.CURSOR) {
                requireRequestFieldName(pagination.getCursorQuery(), "data-sources." + sourceId + ".pagination.cursor-query");
                requireJsonPointer(pagination.getNextCursorJsonPointer(), "data-sources." + sourceId + ".pagination.next-cursor-json-pointer");
            }
            RestRoutingConfig.TombstonePolicy tombstonePolicy = source.getTombstonePolicy();
            RestRoutingConfig.TombstonePolicy.Strategy tombstoneStrategy = tombstonePolicy != null
                && tombstonePolicy.getStrategy() != null
                ? tombstonePolicy.getStrategy()
                : RestRoutingConfig.TombstonePolicy.Strategy.NONE;
            boolean deletesAbsent = tombstoneStrategy == RestRoutingConfig.TombstonePolicy.Strategy.ABSENT_FROM_SNAPSHOT
                || tombstoneStrategy == RestRoutingConfig.TombstonePolicy.Strategy.ABSENT_OR_FIELD_VALUE;
            boolean deletesByField = tombstoneStrategy == RestRoutingConfig.TombstonePolicy.Strategy.FIELD_VALUE
                || tombstoneStrategy == RestRoutingConfig.TombstonePolicy.Strategy.ABSENT_OR_FIELD_VALUE;
            if (deletesAbsent && pagination != null
                && pagination.getStrategy() == RestRoutingConfig.Pagination.Strategy.CURSOR) {
                    throw new IllegalStateException(
                        "HTTP data source '" + sourceId + "' cannot delete absent records from an incremental cursor feed."
                    );
            }
            if (deletesByField) {
                requireJsonPointer(
                    tombstonePolicy.getOperationJsonPointer(),
                    "data-sources." + sourceId + ".tombstone-policy.operation-json-pointer"
                );
                if (tombstonePolicy.getDeleteValues() == null || tombstonePolicy.getDeleteValues().isEmpty()
                    || tombstonePolicy.getDeleteValues().stream().anyMatch(value -> !StringUtils.hasText(value))) {
                    throw new IllegalStateException(
                        "HTTP data source '" + sourceId + "' field-value tombstones require non-empty delete-values."
                    );
                }
            }
        });
    }

    private void validateWebhooks(RestRoutingConfig config, RestConnectorServiceProperties serviceProperties) {
        if (config.getWebhooks() == null || config.getWebhooks().isEmpty()) {
            return;
        }
        requirePersistence(serviceProperties, "provider webhooks");
        config.getWebhooks().forEach((sourceId, source) -> {
            requireId(sourceId, "webhook source");
            if (source == null || !("PUT".equalsIgnoreCase(source.getMethod()) || "POST".equalsIgnoreCase(source.getMethod()))) {
                throw new IllegalStateException("Webhook source '" + sourceId + "' must use POST or PUT.");
            }
            if (!config.getDataSources().containsKey(source.getReconcileDataSourceRef())) {
                throw new IllegalStateException("Webhook source '" + sourceId + "' must reference a configured HTTP data source.");
            }
            RestRoutingConfig.HttpDataSource dataSource = config.getDataSources().get(source.getReconcileDataSourceRef());
            if (!StringUtils.hasText(source.getProtectedResourceBindingRef())
                || !source.getProtectedResourceBindingRef().equals(dataSource.getProtectedResourceBindingRef())) {
                throw new IllegalStateException(
                    "Webhook source '" + sourceId + "' must use the same protected resource binding as its reconciliation source."
                );
            }
            RestRoutingConfig.WebhookVerification verification = source.getVerification();
            if (verification == null || verification.getStrategy() == null
                || !validHeaderName(verification.getSignatureHeader())) {
                throw new IllegalStateException("Webhook source '" + sourceId + "' must declare a verification strategy and signature header.");
            }
            requireResolvedSecret(verification.getSecret(), "webhooks." + sourceId + ".verification.secret");
            requireId(verification.getTimestampComponent(), "webhook timestamp component");
            requireId(verification.getSignatureComponent(), "webhook signature component");
            requireJsonPointer(source.getEventIdJsonPointer(), "webhooks." + sourceId + ".event-id-json-pointer");
            requireJsonPointer(source.getEventTypeJsonPointer(), "webhooks." + sourceId + ".event-type-json-pointer");
            requireJsonPointer(source.getResourceJsonPointer(), "webhooks." + sourceId + ".resource-json-pointer");
            if (source.getAllowedEventTypes() == null || source.getAllowedEventTypes().isEmpty()) {
                throw new IllegalStateException("Webhook source '" + sourceId + "' must declare allowed event types.");
            }
            requireCapabilityList(source.getAllowedEventTypes(), "webhooks." + sourceId + ".allowed-event-types");
            if (source.getAllowedContentTypes() == null || source.getAllowedContentTypes().isEmpty()
                || source.getAllowedContentTypes().stream().anyMatch(value -> !StringUtils.hasText(value)
                    || !value.trim().toLowerCase(Locale.ROOT).matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+"))) {
                throw new IllegalStateException("Webhook source '" + sourceId + "' must declare valid allowed content types.");
            }
            if (source.getOrderingPolicy() == null) {
                throw new IllegalStateException("Webhook source '" + sourceId + "' must declare an ordering policy.");
            }
        });
    }

    private void validateRuntimeDataSync(RestRoutingConfig config) {
        if (config.getDataSources() == null || config.getDataSources().isEmpty()) {
            return;
        }
        RestRoutingConfig.RuntimeDataSync runtime = config.getRuntimeDataSync();
        if (runtime == null || !runtime.isEnabled()) {
            throw new IllegalStateException("HTTP data sources require runtime-data-sync.enabled=true.");
        }
        validateUrl(runtime.getBaseUrl(), "runtime-data-sync.base-url");
        if (!validHeaderName(runtime.getApiKeyHeader())) {
            throw new IllegalStateException("runtime-data-sync.api-key-header is required.");
        }
        requireResolvedSecret(runtime.getApiKeyValue(), "runtime-data-sync.api-key-value");
        if (!StringUtils.hasText(runtime.getDeploymentId()) || !StringUtils.hasText(runtime.getTenantId())) {
            throw new IllegalStateException("runtime-data-sync deployment-id and tenant-id are required.");
        }
    }

    private RestRoutingConfig.ConnectionProfile requireConnectionProfile(RestRoutingConfig config, String ref, String path) {
        RestRoutingConfig.ConnectionProfile profile = StringUtils.hasText(ref) ? config.getConnectionProfiles().get(ref.trim()) : null;
        if (profile == null) {
            throw new IllegalStateException(path + " references an unknown connection profile.");
        }
        return profile;
    }

    private RestRoutingConfig.ProtectedResourceBinding requireBinding(
        RestRoutingConfig config,
        String ref,
        String profileRef,
        String path
    ) {
        RestRoutingConfig.ProtectedResourceBinding binding = StringUtils.hasText(ref)
            ? config.getProtectedResources().get(ref.trim()) : null;
        if (binding == null || !binding.getConnectionProfileRef().equals(profileRef)) {
            throw new IllegalStateException(path + " references an unknown or mismatched protected resource binding.");
        }
        return binding;
    }

    private void requireCapabilities(
        RestRoutingConfig.ConnectionProfile profile,
        RestRoutingConfig.ProtectedResourceBinding binding,
        List<String> required,
        String path
    ) {
        if (required != null && !required.isEmpty()
            && (binding.getCapabilityGrants() == null || !binding.getCapabilityGrants().containsAll(required))) {
            throw new IllegalStateException(path + " requires capability grants that are absent from its protected resource binding.");
        }
        if (required != null && !required.isEmpty()
            && (profile.getCapabilityGrants() == null || !profile.getCapabilityGrants().containsAll(required))) {
            throw new IllegalStateException(path + " requires capability grants that are absent from its connection profile.");
        }
    }

    private void requireCapabilityList(List<String> grants, String path) {
        if (grants == null || grants.isEmpty()
            || grants.stream().anyMatch(grant -> !StringUtils.hasText(grant)
                || !grant.trim().matches("[A-Za-z0-9][A-Za-z0-9:._-]{0,159}"))) {
            throw new IllegalStateException(path + " must contain valid capability identifiers.");
        }
    }

    private void validateStaticQuery(Map<String, Object> query, String path) {
        if (query == null) {
            return;
        }
        if (query.size() > 50) {
            throw new IllegalStateException(path + " supports at most 50 entries.");
        }
        query.forEach((name, value) -> {
            requireRequestFieldName(name, path + " field");
            if (value == null || value instanceof Map<?, ?> || value instanceof List<?>
                || String.valueOf(value).length() > 2_000) {
                throw new IllegalStateException(path + " values must be bounded scalar values.");
            }
        });
    }

    private void validateStaticHeaders(Map<String, String> headers, String path) {
        if (headers == null) {
            return;
        }
        if (headers.size() > 50) {
            throw new IllegalStateException(path + " supports at most 50 entries.");
        }
        headers.forEach((name, value) -> {
            if (!validHeaderName(name) || value == null || value.length() > 2_000) {
                throw new IllegalStateException(path + " contains an invalid or unbounded header.");
            }
        });
    }

    private void validateProjectionMap(Map<String, String> fields, String path) {
        if (fields == null) {
            return;
        }
        if (fields.size() > 100) {
            throw new IllegalStateException(path + " supports at most 100 entries.");
        }
        fields.keySet().forEach(name -> requireId(name, path + " field"));
    }

    private void requireRequestFieldName(String value, String path) {
        if (!StringUtils.hasText(value) || !value.matches("[A-Za-z0-9$][A-Za-z0-9$._-]{0,159}")) {
            throw new IllegalStateException(path + " is invalid.");
        }
    }

    private void validatePlacements(
        List<RestRoutingConfig.ResourcePlacement> placements,
        String requestPath,
        String path
    ) {
        if (placements == null || placements.isEmpty()) {
            throw new IllegalStateException(path + " must declare at least one server-owned placement.");
        }
        Set<String> pathPlacements = new LinkedHashSet<>();
        for (RestRoutingConfig.ResourcePlacement placement : placements) {
            if (placement == null || placement.getTarget() == null || !StringUtils.hasText(placement.getField())) {
                throw new IllegalStateException(path + " contains an incomplete placement.");
            }
            if (placement.getTarget() == RestRoutingConfig.ResourcePlacement.Target.HEADER) {
                if (!validHeaderName(placement.getField())) {
                    throw new IllegalStateException(path + " contains an invalid protected header placement.");
                }
            } else {
                requireRequestFieldName(placement.getField(), path + " field");
            }
            if (placement.getTarget() == RestRoutingConfig.ResourcePlacement.Target.BODY) {
                requireJsonPointer(placement.getJsonPointer(), path + ".json-pointer");
            }
            if (placement.getTarget() == RestRoutingConfig.ResourcePlacement.Target.PATH
                && (!StringUtils.hasText(requestPath)
                    || !requestPath.contains("{" + placement.getField().trim() + "}"))) {
                throw new IllegalStateException(path + " PATH placement does not match a declared path placeholder.");
            }
            if (placement.getTarget() == RestRoutingConfig.ResourcePlacement.Target.PATH) {
                pathPlacements.add(placement.getField().trim());
            }
        }
        Matcher placeholders = PATH_PLACEHOLDER.matcher(StringUtils.hasText(requestPath) ? requestPath : "");
        while (placeholders.find()) {
            if (!pathPlacements.contains(placeholders.group(1))) {
                throw new IllegalStateException(path + " leaves an unbound protected path placeholder.");
            }
        }
    }

    private void rejectAuthHeaderPlacement(
        RestRoutingConfig.ConnectionProfile profile,
        List<RestRoutingConfig.ResourcePlacement> placements,
        String path
    ) {
        if (profile == null || profile.getAuth() == null || placements == null) {
            return;
        }
        RestRoutingConfig.ProviderAuth auth = profile.getAuth();
        String authHeader = auth.getStrategy() == RestRoutingConfig.ProviderAuth.Strategy.API_KEY
            ? auth.getApiKeyHeader()
            : auth.getStrategy() == RestRoutingConfig.ProviderAuth.Strategy.FORM_TOKEN_EXCHANGE
                ? auth.getAuthorizationHeader()
                : null;
        if (!StringUtils.hasText(authHeader)) {
            return;
        }
        boolean conflict = placements.stream()
            .filter(java.util.Objects::nonNull)
            .filter(placement -> placement.getTarget() == RestRoutingConfig.ResourcePlacement.Target.HEADER)
            .map(RestRoutingConfig.ResourcePlacement::getField)
            .filter(StringUtils::hasText)
            .anyMatch(field -> field.trim().equalsIgnoreCase(authHeader.trim()));
        if (conflict) {
            throw new IllegalStateException(path + " must not target the provider authentication header.");
        }
    }

    private void requirePersistence(RestConnectorServiceProperties properties, String feature) {
        if (properties == null || properties.getPersistence() == null || !properties.getPersistence().isEnabled()) {
            throw new IllegalStateException(feature + " require rest-connector.persistence.enabled=true.");
        }
    }

    private void requireResolvedSecret(String value, String path) {
        if (!StringUtils.hasText(value) || value.contains("${")) {
            throw new IllegalStateException(path + " must resolve from a configured deployment secret.");
        }
    }

    private void requireRelativePath(String value, String path) {
        if (!StringUtils.hasText(value) || !value.startsWith("/") || value.contains("://")) {
            throw new IllegalStateException(path + " must be a relative path starting with '/'.");
        }
    }

    private void requireJsonPointer(String value, String path) {
        if (!StringUtils.hasText(value) || !value.startsWith("/")) {
            throw new IllegalStateException(path + " must be an absolute JSON Pointer.");
        }
    }

    private void requireId(String value, String label) {
        if (!StringUtils.hasText(value) || !value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,159}")) {
            throw new IllegalStateException(label + " identifier is invalid.");
        }
    }

    private void requireKnowledgeSourceHandleRef(String value, String label) {
        if (!StringUtils.hasText(value)
            || value.length() > 500
            || !value.equals(value.trim())
            || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException(label + " must be a bounded server-owned handle.");
        }
    }

    private boolean validHostName(String value) {
        return StringUtils.hasText(value)
            && !value.contains(":")
            && !value.contains("/")
            && value.length() <= 253
            && value.matches("(?i)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?");
    }

    private void requireDatabaseIdentifier(String value, String label) {
        if (!StringUtils.hasText(value) || !value.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalStateException(label + " must be a safe PostgreSQL identifier.");
        }
    }

    private void validateUrl(String url, String key) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!StringUtils.hasText(scheme)) {
                throw new IllegalArgumentException("missing scheme");
            }
            String normalized = scheme.trim().toLowerCase(Locale.ROOT);
            if (!"http".equals(normalized) && !"https".equals(normalized)) {
                throw new IllegalArgumentException("unsupported scheme '" + scheme + "'");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("missing host");
            }
        } catch (Exception ex) {
            throw new IllegalStateException(key + " must be a valid absolute http(s) URL (got '" + url + "'): " + ex.getMessage(), ex);
        }
    }

    private void validateHttpsUrl(String url, String key) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                || !StringUtils.hasText(uri.getHost())
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
                throw new IllegalArgumentException("provider endpoints require a clean HTTPS origin/base path");
            }
        } catch (Exception ex) {
            throw new IllegalStateException(key + " must be a valid absolute HTTPS URL without credentials, query, or fragment.", ex);
        }
    }
}
