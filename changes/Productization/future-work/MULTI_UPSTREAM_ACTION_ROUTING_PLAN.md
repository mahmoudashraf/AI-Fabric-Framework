# Multi-Upstream Action Routing Plan

Status: planning document (2026-04-07)

This document addresses a current limitation: the REST connector routes all actions to a single upstream base URL. Real deployments need actions spread across multiple services, each with its own base URL and authentication.

---

## 1) Current Limitation

The REST connector configuration (`actions-routing.local.yml`) defines:

```yaml
connector:
  upstream-base-url: http://localhost:8082
```

All actions route to the same upstream. This works when the customer has one backend service, but fails when:

- Order APIs are at `orders-api.customer.com`
- Product catalog is at `catalog.customer.com`
- Shipping/logistics is at `shipping-partner.com/api`
- Payment processing is at `stripe.com/v1`
- Calendar booking is at `api.calendly.com/v2`

Currently, the customer must build a proxy service that consolidates all APIs behind one URL. This is unnecessary work.

---

## 2) Target State

Each action should optionally define its own upstream URL and auth:

```yaml
actions:
  get-order-status:
    upstream: "https://orders-api.customer.com"
    auth:
      type: bearer
      token: "${ORDERS_API_TOKEN}"
    path: "/orders/{{orderId}}/status"
    method: GET

  search-products:
    upstream: "https://catalog.customer.com"
    auth:
      type: api-key
      header: "X-API-Key"
      key: "${CATALOG_API_KEY}"
    path: "/products/search"
    method: GET

  book-appointment:
    upstream: "https://api.calendly.com/v2"
    auth:
      type: bearer
      token: "${CALENDLY_TOKEN}"
    path: "/scheduling_links"
    method: POST

  process-refund:
    upstream: "https://api.stripe.com/v1"
    auth:
      type: bearer
      token: "${STRIPE_SECRET_KEY}"
    path: "/refunds"
    method: POST
```

If an action does not specify `upstream`, it falls back to the global `connector.upstream-base-url`.

---

## 3) Auth Per Action

Different upstreams require different authentication. The action definition should support:

| Auth Type | Config | Use Case |
|---|---|---|
| `bearer` | Token in Authorization header | Most modern APIs (Stripe, Calendly, etc.) |
| `api-key` | Key in custom header | Catalog APIs, internal services |
| `basic` | Username + password | Legacy APIs |
| `oauth2` | Client credentials flow | Salesforce, HubSpot, etc. |
| `none` | No auth | Public APIs |
| `inherit` | Use global connector auth | Customer's own backend (default) |

```yaml
actions:
  example-action:
    upstream: "https://api.example.com"
    auth:
      type: oauth2
      token-url: "https://auth.example.com/oauth/token"
      client-id: "${EXAMPLE_CLIENT_ID}"
      client-secret: "${EXAMPLE_CLIENT_SECRET}"
      scope: "read write"
    path: "/resources"
    method: GET
```

Secrets are resolved from deployment-scoped provider secret bindings (existing infrastructure in Platform-V4: `DeploymentProviderSecretBindingEntity`).

---

## 4) Absolute URL Support

Actions should also support fully absolute URLs (no upstream resolution):

```yaml
actions:
  check-tracking:
    url: "https://api.aftership.com/v4/trackings/{{trackingNumber}}"
    auth:
      type: api-key
      header: "aftership-api-key"
      key: "${AFTERSHIP_KEY}"
    method: GET
```

When `url` is specified (absolute), `upstream` and `path` are ignored.

Resolution order:
1. If `url` is set → use as absolute URL
2. If `upstream` is set → `upstream + path`
3. Else → `connector.upstream-base-url + path`

---

## 5) Implementation

### Changes to action routing configuration model

Add optional fields to the action definition:

```java
public class ActionRouteDefinition {
    private String path;              // existing
    private String method;            // existing
    
    // New fields
    private String upstream;          // optional per-action upstream base URL
    private String url;              // optional absolute URL (overrides upstream + path)
    private ActionAuthConfig auth;   // optional per-action auth
}

public class ActionAuthConfig {
    private String type;             // bearer, api-key, basic, oauth2, none, inherit
    private String token;            // for bearer
    private String header;           // for api-key
    private String key;              // for api-key
    private String username;         // for basic
    private String password;         // for basic
    private String tokenUrl;         // for oauth2
    private String clientId;         // for oauth2
    private String clientSecret;     // for oauth2
    private String scope;            // for oauth2
}
```

### Changes to REST connector request building

In the request builder, resolve the target URL:

```java
String resolveActionUrl(ActionRouteDefinition action, Map<String, String> params) {
    if (action.getUrl() != null) {
        return interpolate(action.getUrl(), params);
    }
    String base = action.getUpstream() != null 
        ? action.getUpstream() 
        : config.getUpstreamBaseUrl();
    return base + interpolate(action.getPath(), params);
}
```

### Changes to auth header building

```java
HttpHeaders resolveActionAuth(ActionRouteDefinition action) {
    ActionAuthConfig auth = action.getAuth();
    if (auth == null || "inherit".equals(auth.getType())) {
        return globalAuthHeaders();
    }
    return switch (auth.getType()) {
        case "bearer" -> bearerHeaders(auth.getToken());
        case "api-key" -> apiKeyHeaders(auth.getHeader(), auth.getKey());
        case "basic"   -> basicHeaders(auth.getUsername(), auth.getPassword());
        case "oauth2"  -> oauth2Headers(auth);
        case "none"    -> new HttpHeaders();
        default        -> globalAuthHeaders();
    };
}
```

---

## 6) Impact

| Before | After |
|---|---|
| Customer must build proxy to consolidate APIs | Actions call any API directly |
| One auth mechanism per deployment | Per-action auth |
| Calendly needs SDK? No — just REST with bearer token | Confirmed: REST + per-action auth covers it |
| Zapier webhook? Need dedicated integration | Just an action with absolute URL to Zapier catch hook |
| Stripe refund? Need payment integration | Just an action with bearer token to Stripe API |

This eliminates the need for 90% of the "dedicated connectors" listed in the SaaS strategy document.

---

## 7) Effort

| Component | Effort |
|---|---|
| Action route model changes | Small |
| URL resolution logic | Small |
| Per-action auth header building | Small |
| OAuth2 client credentials flow | Medium |
| Secret resolution from deployment bindings | Small (uses existing infra) |
| Platform UI for per-action upstream/auth config | Medium |
| Documentation and examples | Small |

**Total: 2-3 weeks.** Most of the infrastructure (secret bindings, route templating, HTTP client) already exists.
