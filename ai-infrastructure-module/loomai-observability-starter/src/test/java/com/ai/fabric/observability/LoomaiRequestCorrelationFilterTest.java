package com.ai.fabric.observability;

import com.ai.fabric.observability.config.LoomaiObservabilityProperties;
import com.ai.fabric.observability.logging.LoomaiMdcKeys;
import com.ai.fabric.observability.logging.LoomaiObservabilityContextContributor;
import com.ai.fabric.observability.logging.LoomaiRequestCorrelationFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoomaiRequestCorrelationFilterTest {

    @Test
    void populatesResponseHeaderAndContributorContext() throws Exception {
        LoomaiObservabilityProperties properties = new LoomaiObservabilityProperties();
        properties.setProduct("platform");
        properties.setService("backend");
        properties.setEnvironment("test");

        LoomaiObservabilityContextContributor contributor = request -> Map.of(
            LoomaiMdcKeys.DEPLOYMENT_ID, "dep-123",
            LoomaiMdcKeys.TENANT_ID, "tenant-9"
        );
        LoomaiRequestCorrelationFilter filter = new LoomaiRequestCorrelationFilter(properties, List.of(contributor));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("X-Request-Id", "req-456");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("operator-1", "N/A")
        );
        try {
            final Map<String, String>[] seen = new Map[1];
            FilterChain chain = (req, res) -> seen[0] = MDC.getCopyOfContextMap();

            filter.doFilter(request, response, chain);

            assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-456");
            assertThat(seen[0])
                .containsEntry(LoomaiMdcKeys.REQUEST_ID, "req-456")
                .containsEntry(LoomaiMdcKeys.PRODUCT, "platform")
                .containsEntry(LoomaiMdcKeys.SERVICE, "backend")
                .containsEntry(LoomaiMdcKeys.ENVIRONMENT, "test")
                .containsEntry(LoomaiMdcKeys.ACTOR_ID, "operator-1")
                .containsEntry(LoomaiMdcKeys.DEPLOYMENT_ID, "dep-123")
                .containsEntry(LoomaiMdcKeys.TENANT_ID, "tenant-9");
        } finally {
            SecurityContextHolder.clearContext();
            MDC.clear();
        }
    }
}
