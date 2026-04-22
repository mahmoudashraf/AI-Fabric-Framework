package com.ai.fabric.observability.logging;

import com.ai.fabric.observability.config.LoomaiObservabilityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoomaiRequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE =
        LoomaiRequestCorrelationFilter.class.getName() + ".requestId";

    private static final Logger log = LoggerFactory.getLogger(LoomaiRequestCorrelationFilter.class);

    private final LoomaiObservabilityProperties properties;
    private final List<LoomaiObservabilityContextContributor> contributors;

    public LoomaiRequestCorrelationFilter(LoomaiObservabilityProperties properties,
                                          List<LoomaiObservabilityContextContributor> contributors) {
        this.properties = properties;
        this.contributors = contributors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        Map<String, String> context = previous == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previous);
        enrichStaticContext(context);
        enrichRequestContext(request, context);

        String requestId = context.get(LoomaiMdcKeys.REQUEST_ID);
        if (StringUtils.hasText(requestId)) {
            request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
            String responseHeader = properties.getHttp().getResponseIdHeader();
            if (StringUtils.hasText(responseHeader)) {
                response.setHeader(responseHeader, requestId);
            }
        }

        if (context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previous == null || previous.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }

    private void enrichStaticContext(Map<String, String> context) {
        putIfHasText(context, LoomaiMdcKeys.PRODUCT, properties.getProduct());
        putIfHasText(context, LoomaiMdcKeys.SERVICE, properties.getService());
        putIfHasText(context, LoomaiMdcKeys.ENVIRONMENT, properties.getEnvironment());
    }

    private void enrichRequestContext(HttpServletRequest request, Map<String, String> context) {
        putIfHasText(context, LoomaiMdcKeys.REQUEST_ID, requestId(request));
        putIfHasText(context, LoomaiMdcKeys.DEPLOYMENT_ID, firstPresent(
            request.getHeader("X-Deployment-Id"),
            request.getParameter("deploymentId")
        ));
        putIfHasText(context, LoomaiMdcKeys.RELEASE_ID, firstPresent(
            request.getHeader("X-Release-Id"),
            request.getParameter("releaseId")
        ));
        putIfHasText(context, LoomaiMdcKeys.VERIFICATION_RUN_ID, firstPresent(
            request.getHeader("X-Verification-Run-Id"),
            request.getParameter("verificationRunId")
        ));
        putIfHasText(context, LoomaiMdcKeys.TENANT_ID, firstPresent(
            request.getHeader("X-Tenant-Id"),
            request.getParameter("tenantId")
        ));
        putIfHasText(context, LoomaiMdcKeys.MERCHANT_ID, firstPresent(
            request.getHeader("X-Merchant-Id"),
            request.getParameter("merchantId")
        ));
        putIfHasText(context, LoomaiMdcKeys.SERVICE_REF, firstPresent(
            request.getHeader("X-Service-Ref"),
            request.getParameter("serviceRef")
        ));
        putIfHasText(context, LoomaiMdcKeys.CUSTOMER_ID, firstPresent(
            request.getHeader("X-Customer-Id"),
            request.getParameter("customerId")
        ));
        putIfHasText(context, LoomaiMdcKeys.TRACE_ID, request.getHeader("X-Trace-Id"));

        Principal principal = request.getUserPrincipal();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (principal == null && authentication != null) {
            principal = authentication;
        }
        enrichPrincipalContext(context, principal, authentication);

        for (LoomaiObservabilityContextContributor contributor : contributors) {
            try {
                Map<String, String> contribution = contributor.contribute(request);
                if (contribution != null && !contribution.isEmpty()) {
                    contribution.forEach((key, value) -> putIfHasText(context, key, value));
                }
            } catch (RuntimeException ex) {
                log.debug("Ignoring observability context contributor failure.", ex);
            }
        }
    }

    private void enrichPrincipalContext(Map<String, String> context,
                                        Principal principal,
                                        Authentication authentication) {
        Object source = principal;
        if (source == null && authentication != null) {
            source = authentication.getPrincipal();
        }
        if (source == null) {
            return;
        }

        putIfHasText(context, LoomaiMdcKeys.ACTOR_ID, firstPresent(
            accessor(source, "actorId"),
            accessor(source, "userId"),
            accessor(source, "subjectId"),
            accessor(source, "username"),
            actorName(authentication, source)
        ));
        putIfHasText(context, LoomaiMdcKeys.ACTOR_DISPLAY_NAME, firstPresent(
            accessor(source, "displayName"),
            accessor(source, "name")
        ));
        putIfHasText(context, LoomaiMdcKeys.TENANT_ID, firstPresent(
            context.get(LoomaiMdcKeys.TENANT_ID),
            accessor(source, "tenantId")
        ));
        putIfHasText(context, LoomaiMdcKeys.DEPLOYMENT_ID, firstPresent(
            context.get(LoomaiMdcKeys.DEPLOYMENT_ID),
            accessor(source, "deploymentId")
        ));
        putIfHasText(context, LoomaiMdcKeys.MERCHANT_ID, firstPresent(
            context.get(LoomaiMdcKeys.MERCHANT_ID),
            accessor(source, "merchantId"),
            accessor(source, "shopDomain"),
            accessor(source, "destination")
        ));
        putIfHasText(context, LoomaiMdcKeys.CUSTOMER_ID, firstPresent(
            context.get(LoomaiMdcKeys.CUSTOMER_ID),
            accessor(source, "customerId")
        ));
        putIfHasText(context, LoomaiMdcKeys.SERVICE_REF, firstPresent(
            context.get(LoomaiMdcKeys.SERVICE_REF),
            accessor(source, "serviceRef"),
            accessor(source, "productServiceRef")
        ));
    }

    private String actorName(Authentication authentication, Object source) {
        if (source instanceof String stringValue) {
            return "anonymousUser".equals(stringValue) ? null : stringValue;
        }
        if (authentication == null) {
            return null;
        }
        String name = authentication.getName();
        return StringUtils.hasText(name) && !"anonymousUser".equals(name) ? name : null;
    }

    private String requestId(HttpServletRequest request) {
        String configuredHeader = properties.getHttp().getRequestIdHeader();
        String requestId = StringUtils.hasText(configuredHeader) ? request.getHeader(configuredHeader) : null;
        if (StringUtils.hasText(requestId)) {
            return requestId.trim();
        }
        Object existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String existingId && !existingId.isBlank()) {
            return existingId;
        }
        return UUID.randomUUID().toString();
    }

    private String accessor(Object source, String accessorName) {
        if (source == null || !StringUtils.hasText(accessorName)) {
            return null;
        }
        try {
            Method method = source.getClass().getMethod(accessorName);
            Object value = method.invoke(source);
            if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
                return stringValue.trim();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        String getterName = "get" + Character.toUpperCase(accessorName.charAt(0)) + accessorName.substring(1);
        try {
            Method method = source.getClass().getMethod(getterName);
            Object value = method.invoke(source);
            if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
                return stringValue.trim();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private String firstPresent(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private void putIfHasText(Map<String, String> context, String key, String value) {
        if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
            context.put(key, value.trim());
        }
    }
}
