package com.loomai.demo.dealership.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Component
public class RuntimePrivateAssertionSigner {

    private final DealershipDemoProperties properties;
    private final ObjectMapper objectMapper;

    public RuntimePrivateAssertionSigner(DealershipDemoProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String authorizationValue(List<String> scopes) {
        DealershipDemoProperties.PrivateAccess access = properties.getRuntime().getPrivateAccess();
        require(access.getSigningKey(), "Runtime assertion signing key");
        require(access.getDeploymentId(), "Runtime deployment id");
        require(access.getTenantId(), "Runtime tenant id");
        require(access.getIssuer(), "Runtime assertion issuer");
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("sub", "system:dealership-demo-ingestion");
            payload.put("subjectType", "TRUSTED_BACKEND");
            payload.put("authMode", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
            payload.put("callerType", "TRUSTED_BACKEND");
            payload.put("deploymentId", access.getDeploymentId().trim());
            if (StringUtils.hasText(access.getCustomerId())) {
                payload.put("customerId", access.getCustomerId().trim());
            }
            payload.put("tenantId", access.getTenantId().trim());
            payload.put("iss", access.getIssuer().trim());
            if (StringUtils.hasText(access.getAudience())) {
                payload.put("aud", access.getAudience().trim());
            }
            payload.put("exp", Instant.now().plus(properties.getRuntime().getAssertionTtl()).toString());
            ArrayNode scopeArray = payload.putArray("scopes");
            scopes.stream().filter(StringUtils::hasText).map(String::trim).distinct().forEach(scopeArray::add);

            String payloadSegment = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(payload));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(access.getSigningKey().trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payloadSegment.getBytes(StandardCharsets.UTF_8)));
            return "Bearer rpa1." + payloadSegment + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign the runtime private assertion.", ex);
        }
    }

    private void require(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(label + " is not configured.");
        }
    }
}
