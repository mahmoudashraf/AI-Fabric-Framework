package com.ai.infrastructure.connector.rest.templating;

import java.util.Map;

public record TemplateContext(
    String actionId,
    Map<String, Object> params,
    Map<String, Object> trace,
    String idempotencyKey,
    Object body,
    Integer status,
    Map<String, Object> headers
) {
}

