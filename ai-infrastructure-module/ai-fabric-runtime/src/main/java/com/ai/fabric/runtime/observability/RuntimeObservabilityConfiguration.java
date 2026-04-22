package com.ai.fabric.runtime.observability;

import com.ai.fabric.observability.logging.LoomaiMdcKeys;
import com.ai.fabric.observability.logging.LoomaiObservabilityContextContributor;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ResponseStatusException;

@Configuration(proxyBeanMethods = false)
public class RuntimeObservabilityConfiguration {

    @Bean
    LoomaiObservabilityContextContributor runtimeAuthContextContributor(
        RuntimeRequestAuthResolver runtimeRequestAuthResolver
    ) {
        return request -> {
            try {
                RuntimeResolvedIdentity identity = runtimeRequestAuthResolver.resolveOptionalVerifiedContext(request);
                if (identity == null || identity.getAuthContext() == null) {
                    return Map.of();
                }
                RuntimeAuthContext authContext = identity.getAuthContext();
                Map<String, String> context = new LinkedHashMap<>();
                putIfPresent(context, LoomaiMdcKeys.DEPLOYMENT_ID, authContext.getDeploymentId());
                putIfPresent(context, LoomaiMdcKeys.TENANT_ID, authContext.getTenantId());
                putIfPresent(context, LoomaiMdcKeys.CUSTOMER_ID, authContext.getCustomerId());
                putIfPresent(context, LoomaiMdcKeys.ACTOR_ID, authContext.getSubjectId());
                return context;
            } catch (ResponseStatusException ignored) {
                return Map.of();
            }
        };
    }

    private void putIfPresent(Map<String, String> context, String key, String value) {
        if (value != null && !value.isBlank()) {
            context.put(key, value);
        }
    }
}
