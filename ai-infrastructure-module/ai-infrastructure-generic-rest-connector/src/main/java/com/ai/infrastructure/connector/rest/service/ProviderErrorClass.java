package com.ai.infrastructure.connector.rest.service;

public enum ProviderErrorClass {
    BAD_REQUEST,
    AUTHENTICATION_REQUIRED,
    RESOURCE_ACCESS_DENIED,
    CAPABILITY_DENIED,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    TIMEOUT,
    MALFORMED_RESPONSE
}
