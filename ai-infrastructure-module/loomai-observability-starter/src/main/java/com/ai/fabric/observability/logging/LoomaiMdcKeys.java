package com.ai.fabric.observability.logging;

public final class LoomaiMdcKeys {

    public static final String PRODUCT = "product";
    public static final String SERVICE = "service";
    public static final String ENVIRONMENT = "environment";
    public static final String REQUEST_ID = "requestId";
    public static final String ACTOR_ID = "actorId";
    public static final String ACTOR_DISPLAY_NAME = "actorDisplayName";
    public static final String DEPLOYMENT_ID = "deploymentId";
    public static final String RELEASE_ID = "releaseId";
    public static final String VERIFICATION_RUN_ID = "verificationRunId";
    public static final String TENANT_ID = "tenantId";
    public static final String MERCHANT_ID = "merchantId";
    public static final String CUSTOMER_ID = "customerId";
    public static final String SERVICE_REF = "serviceRef";
    public static final String HTTP_ROUTE = "httpRoute";
    public static final String TRACE_ID = "traceId";

    private LoomaiMdcKeys() {
    }
}
