insert into platform_marketplace_plugins (
    id, slug, display_name, plugin_type, publisher_slug, publisher_display_name,
    short_description, status, created_at, updated_at
) values
    (
        'mkp-data-simulated-dealership-stock-v1',
        'simulated-dealership-stock-v1',
        'Simulated Dealership Stock',
        'DATA',
        'loom',
        'Loom AI',
        'Fictional dealership stock synchronized through a reviewed AT Connect shaped simulator. Not an Auto Trader service or endorsement.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-action-simulated-dealership-discovery-v1',
        'simulated-dealership-discovery-v1',
        'Simulated Dealership Discovery',
        'ACTION',
        'loom',
        'Loom AI',
        'Read-only search, vehicle detail and comparison actions over the deployment-local synchronized stock projection.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-action-dealership-engagement-v1',
        'dealership-engagement-v1',
        'Dealership Engagement Actions',
        'ACTION',
        'loom',
        'Loom AI',
        'Confirmed callback and test-drive requests sent to a deployment-owned dealership receiver.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkp-template-simulated-dealership-experience-v1',
        'simulated-dealership-experience-v1',
        'Simulated Dealership AI Experience',
        'TEMPLATE',
        'loom',
        'Loom AI',
        'Self-service dealership AI Workspace with fictional live stock, customer-owned policy documents, governed engagement and human handover.',
        'ACTIVE',
        current_timestamp,
        current_timestamp
    );

insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
) values
    (
        'mkv-data-simulated-dealership-stock-v1-0',
        'mkp-data-simulated-dealership-stock-v1',
        '1.0.0',
        'EARLY_ACCESS',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-data-simulated-dealership-stock-v1",
          "version": "1.0.0",
          "pluginType": "DATA",
          "displayName": "Simulated Dealership Stock",
          "description": "Fictional dealership stock synchronized through the documented AT Connect authentication, stock, media and notification shapes. This is a Loom AI simulator, not an Auto Trader service or endorsement.",
          "compatibility": {
            "requiredCapabilities": ["knowledgeSources"],
            "supportedDeploymentTargets": ["custom-start-from-scratch"],
            "supportedAuthModes": ["PUBLIC_RUNTIME_ANONYMOUS", "PUBLIC_RUNTIME_AUTHENTICATED", "PRIVATE_RUNTIME_BACKEND_MEDIATED"]
          },
          "pricing": {"pricingModel": "FREE"},
          "installForm": [
            {"id": "providerBaseUrl", "label": "Managed simulator URL", "type": "url", "required": true},
            {"id": "providerHost", "label": "Managed simulator host", "type": "text", "required": true},
            {"id": "providerKey", "label": "Simulator API key", "type": "secretRef", "required": true},
            {"id": "providerSecret", "label": "Simulator API secret", "type": "secretRef", "required": true},
            {"id": "notificationSecret", "label": "Simulator notification secret", "type": "secretRef", "required": true},
            {"id": "advertiserId", "label": "Fictional advertiser ID", "type": "text", "required": true}
          ],
          "permissions": {
            "contributesKnowledgeSources": true,
            "requiresSharedDatasetAccess": false,
            "requiresDeploymentSecrets": true
          },
          "contributions": {
            "entityConfig": {
              "ai-entities": {
                "dealer-vehicle": {
                  "indexing": {"enabled": true, "max-characters": 8000},
                  "analysis": {"enabled": false, "after": []},
                  "searchable-fields": [
                    {"name": "content", "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"], "preprocessing": "CLEAN", "max-length": 8000, "priority": 100, "required": true}
                  ],
                  "metadata-fields": [
                    {"name": "tenantId", "data-type": "ID", "destinations": ["VECTOR_METADATA"], "priority": 100, "required": true, "sanitize-pii": false},
                    {"name": "deploymentId", "data-type": "ID", "destinations": ["VECTOR_METADATA"], "priority": 100, "required": true, "sanitize-pii": false},
                    {"name": "sourceId", "data-type": "ID", "destinations": ["VECTOR_METADATA"], "priority": 90, "required": true, "sanitize-pii": false},
                    {"name": "stockId", "data-type": "ID", "destinations": ["VECTOR_METADATA", "API_RESPONSE"], "priority": 90, "required": true, "sanitize-pii": false},
                    {"name": "imageId", "data-type": "ID", "destinations": ["VECTOR_METADATA", "API_RESPONSE"], "priority": 60, "required": false, "sanitize-pii": false},
                    {"name": "imageUrl", "data-type": "STRING", "destinations": ["VECTOR_METADATA", "API_RESPONSE"], "priority": 60, "required": false, "sanitize-pii": false}
                  ]
                }
              }
            },
            "datasets": [
              {
                "datasetId": "simulated-dealership-stock",
                "entityType": "dealer-vehicle",
                "storageScope": "CUSTOMER_MANAGED",
                "sharingScope": "DEPLOYMENT_ONLY",
                "ingestionMode": "EXTERNAL_SYNC_HTTP",
                "updateStrategy": "UPSERT_BY_ID",
                "syncConnector": {
                  "connectorType": "HTTP_JSON",
                  "connectionProfile": {
                    "profileId": "simulated-stock-provider",
                    "environment": "managed-simulator",
                    "managedBaseUrlField": "providerBaseUrl",
                    "managedAllowedHostField": "providerHost",
                    "auth": {
                      "strategy": "FORM_TOKEN_EXCHANGE",
                      "managedTokenBaseUrlField": "providerBaseUrl",
                      "tokenPath": "/authenticate",
                      "tokenMethod": "POST",
                      "credentialFields": [
                        {"name": "key", "secretRefField": "providerKey"},
                        {"name": "secret", "secretRefField": "providerSecret"}
                      ],
                      "tokenJsonPointer": "/access_token",
                      "absoluteExpiryJsonPointer": "/expires_at",
                      "authorizationHeader": "Authorization",
                      "authorizationScheme": "Bearer",
                      "expirySkewSeconds": 30,
                      "timeoutMs": 5000
                    },
                    "ratePolicy": {
                      "maxConcurrent": 2,
                      "minIntervalMs": 50,
                      "rateLimitedPauseMs": 30000,
                      "unavailablePauseMs": 5000,
                      "maxAttempts": 2,
                      "retryBackoffMs": 250,
                      "retryStatuses": [429, 502, 503, 504]
                    },
                    "errorMappings": [],
                    "capabilityGrants": ["stock:read"],
                    "correlationResponseHeaders": []
                  },
                  "protectedResource": {
                    "bindingId": "simulated-dealership-account",
                    "environment": "managed-simulator",
                    "resourceType": "advertiser",
                    "resourceIdField": "advertiserId",
                    "policyRef": "single-advertiser-deployment",
                    "capabilityGrants": ["stock:read"]
                  },
                  "httpSource": {
                    "sourceId": "simulated-dealership-stock-source",
                    "enabled": true,
                    "path": "/stock",
                    "method": "GET",
                    "query": {"lifecycleState": "FORECOURT"},
                    "completeHttpStatuses": [200],
                    "trustedResourcePlacements": [{"target": "QUERY", "field": "advertiserId"}],
                    "requiredCapabilityGrants": ["stock:read"],
                    "pagination": {"strategy": "PAGE_SIZE", "pageQuery": "page", "sizeQuery": "pageSize", "startPage": 1, "pageSize": 200, "maxPages": 100},
                    "mapping": {
                      "recordsJsonPointer": "/results",
                      "idJsonPointer": "/metadata/stockId",
                      "resourceJsonPointer": "/advertiser/advertiserId",
                      "contentFields": {
                        "make": "/vehicle/make",
                        "model": "/vehicle/model",
                        "derivative": "/vehicle/derivative",
                        "registrationYear": "/vehicle/yearOfManufacture",
                        "fuelType": "/vehicle/fuelType",
                        "bodyType": "/vehicle/bodyType",
                        "transmission": "/vehicle/transmissionType",
                        "mileage": "/vehicle/odometerReadingMiles",
                        "priceGbp": "/adverts/retailAdverts/totalPrice/amountGBP",
                        "features": "/features"
                      },
                      "entityFields": {
                        "stockId": "/metadata/stockId",
                        "make": "/vehicle/make",
                        "model": "/vehicle/model",
                        "derivative": "/vehicle/derivative",
                        "year": "/vehicle/yearOfManufacture",
                        "priceGbp": "/adverts/retailAdverts/totalPrice/amountGBP",
                        "mileage": "/vehicle/odometerReadingMiles",
                        "fuelType": "/vehicle/fuelType",
                        "transmission": "/vehicle/transmissionType",
                        "bodyType": "/vehicle/bodyType",
                        "availability": "/metadata/lifecycleState",
                        "advertStatus": "/adverts/retailAdverts/advertiserAdvert/status",
                        "features": "/features",
                        "imageId": "/media/images/0/imageId",
                        "imageUrl": "/media/images/0/href"
                      },
                      "metadataFields": {
                        "stockId": "/metadata/stockId",
                        "searchId": "/metadata/searchId",
                        "lifecycleState": "/metadata/lifecycleState",
                        "advertStatus": "/adverts/retailAdverts/advertiserAdvert/status",
                        "providerUpdatedAt": "/metadata/lastUpdated",
                        "providerRecordVersion": "/metadata/versionNumber",
                        "imageId": "/media/images/0/imageId",
                        "imageUrl": "/media/images/0/href"
                      },
                      "inclusionConditions": [
                        {"jsonPointer": "/adverts/retailAdverts/advertiserAdvert/status", "allowedValues": ["PUBLISHED"]}
                      ],
                      "maxRecords": 20000,
                      "maxResponseBytes": 10485760
                    },
                    "tombstonePolicy": {
                      "strategy": "ABSENT_OR_FIELD_VALUE",
                      "operationJsonPointer": "/metadata/lifecycleState",
                      "deleteValues": ["SOLD", "WASTEBIN", "DELETED"]
                    },
                    "targetedRecordFetch": {
                      "enabled": true,
                      "path": "/stock",
                      "method": "GET",
                      "completeHttpStatuses": [200],
                      "absentHttpStatuses": [404],
                      "query": {"page": 1, "pageSize": 1},
                      "recordKeyPlacement": {"target": "QUERY", "field": "stockId"}
                    },
                    "scheduleSeconds": 900
                  },
                  "webhook": {
                    "sourceId": "simulated-stock-events",
                    "method": "PUT",
                    "allowedContentTypes": ["application/json"],
                    "verification": {
                      "strategy": "HMAC_SHA256_TIMESTAMP_DOT_RAW_BODY",
                      "signatureHeader": "AutoTrader-Signature",
                      "secretRefField": "notificationSecret",
                      "timestampComponent": "t",
                      "signatureComponent": "v1",
                      "timestampUnit": "SECONDS",
                      "replayWindowSeconds": 300
                    },
                    "eventIdentityJsonPointers": ["/id", "/time"],
                    "eventTypeJsonPointer": "/type",
                    "resourceJsonPointer": "/data/advertiser/advertiserId",
                    "recordKeyJsonPointer": "/data/metadata/stockId",
                    "reconciliationStrategy": "FETCH_CURRENT_RECORD",
                    "allowedEventTypes": ["STOCK_UPDATE"],
                    "responseStatuses": {"accepted": 200, "duplicate": 200, "resourceMismatch": 422},
                    "maxBodyBytes": 1048576,
                    "maxReconcileAttempts": 3,
                    "retryDelaySeconds": 10,
                    "registrationExpected": true,
                    "manualReplayEnabled": true,
                    "orderingPolicy": "RECONCILE_LATEST_STATE"
                  }
                }
              }
            ],
            "knowledgeSources": [
              {
                "sourceType": "deployment-private-vector",
                "sourceKey": "simulated-dealership-stock",
                "adapterType": "deployment-private-vector",
                "datasetRef": "simulated-dealership-stock",
                "entityType": "dealer-vehicle",
                "attributionLabel": "Fictional synchronized dealership stock"
              }
            ]
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'Reviewed fictional provider contract. Managed endpoint and credentials are generated per deployment; no real provider access is claimed.',
        'seeded-simulated-dealership-stock-v1',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-action-simulated-dealership-discovery-v1-0',
        'mkp-action-simulated-dealership-discovery-v1',
        '1.0.0',
        'EARLY_ACCESS',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-action-simulated-dealership-discovery-v1",
          "version": "1.0.0",
          "pluginType": "ACTION",
          "displayName": "Simulated Dealership Discovery",
          "compatibility": {
            "requiredCapabilities": ["actions"],
            "supportedDeploymentTargets": ["custom-start-from-scratch"]
          },
          "pricing": {"pricingModel": "FREE"},
          "permissions": {"contributesActions": true, "requiresExternalHttpExecution": false},
          "contributions": {
            "actions": [
              {
                "actionId": "dealership_search_inventory",
                "adapterType": "connector-http",
                "description": "Search current synchronized fictional stock using every explicit buyer preference as a typed filter.",
                "category": "dealership_inventory",
                "readOnly": true,
                "anonymousAllowed": true,
                "requiresConfirmation": false,
                "groundingEligible": true,
                "groundingVectorSpaces": ["dealer-vehicle"],
                "readActionResolutionEligible": true,
                "llmFacts": {"lists": [{"sourcePath": "results", "target": "items", "maxItems": 24, "includeFields": ["stockId", "make", "model", "derivative", "year", "priceGbp", "mileage", "fuelType", "transmission", "bodyType", "availability", "features", "imageUrl"]}]},
                "params": [
                  {"name": "make", "description": "Exact manufacturer brand only. Put fuel or powertrain terms in fuelType.", "type": "STRING", "required": false},
                  {"name": "fuelType", "description": "Exact buyer-stated fuel type.", "type": "STRING", "required": false, "allowedValues": ["Electric", "Petrol", "Diesel", "Hybrid", "Plug-in hybrid"]},
                  {"name": "bodyType", "description": "Exact buyer-stated body type.", "type": "STRING", "required": false},
                  {"name": "maxPriceGbp", "description": "Maximum whole-pound budget. Omit when absent.", "type": "INTEGER", "required": false, "min": 1},
                  {"name": "maxMileage", "description": "Maximum mileage. Omit when absent.", "type": "INTEGER", "required": false, "min": 1},
                  {"name": "limit", "description": "Maximum vehicles to return.", "type": "INTEGER", "required": false, "min": 1, "max": 24}
                ],
                "route": {
                  "sourceProjection": {
                    "sourceRef": "simulated-dealership-stock-source",
                    "filters": [
                      {"param": "make", "fields": ["make"], "operator": "EQUALS_IGNORE_CASE"},
                      {"param": "fuelType", "fields": ["fuelType"], "operator": "EQUALS_IGNORE_CASE"},
                      {"param": "bodyType", "fields": ["bodyType"], "operator": "EQUALS_IGNORE_CASE"},
                      {"param": "maxPriceGbp", "fields": ["priceGbp"], "operator": "NUMBER_LESS_THAN_OR_EQUAL", "absentValues": ["0"]},
                      {"param": "maxMileage", "fields": ["mileage"], "operator": "NUMBER_LESS_THAN_OR_EQUAL", "absentValues": ["0"]}
                    ],
                    "outputFields": ["stockId", "make", "model", "derivative", "year", "priceGbp", "mileage", "fuelType", "transmission", "bodyType", "availability", "advertStatus", "features", "imageId", "imageUrl"],
                    "limitParam": "limit",
                    "defaultLimit": 10,
                    "maxLimit": 24,
                    "requireSuccessfulSync": true,
                    "maxStalenessSeconds": 1800
                  },
                  "response": {
                    "result": {"_items": "{{body.results}}", "_count": "{{body.returnedCount}}", "_totalCount": "{{body.totalResults}}", "results": "{{body.results}}", "total": "{{body.totalResults}}", "source": "{{body.source}}"},
                    "pinnedTargetsFromCollection": {
                      "collectionJsonPointer": "/results",
                      "idJsonPointer": "/stockId",
                      "vectorSpace": "dealer-vehicle",
                      "contentFields": {"make": "/make", "model": "/model", "derivative": "/derivative", "year": "/year", "priceGbp": "/priceGbp", "mileage": "/mileage", "fuelType": "/fuelType", "bodyType": "/bodyType", "availability": "/availability"},
                      "metadataFields": {"stockId": "/stockId", "make": "/make", "model": "/model", "year": "/year", "imageUrl": "/imageUrl"},
                      "maxTargets": 8,
                      "maxContentChars": 1200
                    },
                    "message": "Current fictional stock search completed."
                  },
                  "authz": {"enabled": false}
                }
              },
              {
                "actionId": "dealership_get_vehicle",
                "adapterType": "connector-http",
                "description": "Load one current stock record from a trusted attached or conversation target.",
                "category": "dealership_inventory",
                "readOnly": true,
                "anonymousAllowed": true,
                "requiresConfirmation": false,
                "groundingEligible": true,
                "groundingVectorSpaces": ["dealer-vehicle"],
                "readActionResolutionEligible": true,
                "llmFacts": {"objects": [{"sourcePath": "vehicleRecord", "target": "vehicle", "includeFields": ["stockId", "make", "model", "derivative", "year", "priceGbp", "mileage", "fuelType", "transmission", "bodyType", "availability", "features", "imageUrl"]}]},
                "params": [
                  {"name": "vehicle", "description": "Exact stock ID from trusted attached context or an earlier provider result.", "type": "STRING", "required": true, "visibility": "INTERNAL", "askUser": false, "resolveFrom": {"source": "ATTACHMENT_METADATA", "metadataKeys": ["lookupValue", "stockId", "vehicleId", "id"]}, "evidenceBound": true, "evidenceKeys": ["lookupValue", "stockId", "vehicleId", "id"], "evidenceFallbackPolicy": "CLARIFY"}
                ],
                "route": {
                  "sourceProjection": {
                    "sourceRef": "simulated-dealership-stock-source",
                    "filters": [{"param": "vehicle", "fields": ["stockId"], "operator": "EQUALS_IGNORE_CASE"}],
                    "outputFields": ["stockId", "make", "model", "derivative", "year", "priceGbp", "mileage", "fuelType", "transmission", "bodyType", "availability", "advertStatus", "features", "imageId", "imageUrl"],
                    "defaultLimit": 1,
                    "maxLimit": 1,
                    "requireSuccessfulSync": true,
                    "maxStalenessSeconds": 1800
                  },
                  "response": {
                    "result": {"_items": "{{body.results}}", "_count": "{{body.returnedCount}}", "vehicleRecord": "{{body.results[0]}}", "source": "{{body.source}}"},
                    "pinnedTargetsFromCollection": {
                      "collectionJsonPointer": "/results",
                      "idJsonPointer": "/stockId",
                      "vectorSpace": "dealer-vehicle",
                      "contentFields": {"make": "/make", "model": "/model", "derivative": "/derivative", "year": "/year", "priceGbp": "/priceGbp", "mileage": "/mileage", "fuelType": "/fuelType", "bodyType": "/bodyType", "availability": "/availability", "features": "/features"},
                      "metadataFields": {"stockId": "/stockId", "make": "/make", "model": "/model", "year": "/year", "imageUrl": "/imageUrl"},
                      "maxTargets": 1,
                      "maxContentChars": 1600
                    },
                    "message": "Current fictional stock details loaded."
                  },
                  "authz": {"enabled": false}
                }
              },
              {
                "actionId": "dealership_compare_vehicles",
                "adapterType": "connector-http",
                "description": "Compare two to four trusted stock IDs from current conversation evidence.",
                "category": "dealership_inventory",
                "readOnly": true,
                "anonymousAllowed": true,
                "requiresConfirmation": false,
                "groundingEligible": true,
                "groundingVectorSpaces": ["dealer-vehicle"],
                "readActionResolutionEligible": true,
                "params": [
                  {"name": "vehicleReferences", "description": "Comma-separated trusted stock IDs returned by prior inventory evidence.", "type": "STRING", "required": true, "visibility": "INTERNAL", "askUser": false, "evidenceBound": true, "evidenceKeys": ["stockId", "vehicleId", "id"], "evidenceFallbackPolicy": "CLARIFY"}
                ],
                "route": {
                  "sourceProjection": {
                    "sourceRef": "simulated-dealership-stock-source",
                    "filters": [{"param": "vehicleReferences", "fields": ["stockId"], "operator": "ANY_TOKEN_EQUALS_IGNORE_CASE", "tokenDelimiter": ","}],
                    "outputFields": ["stockId", "make", "model", "derivative", "year", "priceGbp", "mileage", "fuelType", "transmission", "bodyType", "availability", "features", "imageUrl"],
                    "defaultLimit": 4,
                    "maxLimit": 4,
                    "requireSuccessfulSync": true,
                    "maxStalenessSeconds": 1800
                  },
                  "response": {"result": {"_items": "{{body.results}}", "_count": "{{body.returnedCount}}", "vehicles": "{{body.results}}", "source": "{{body.source}}"}, "message": "Current fictional stock compared."},
                  "authz": {"enabled": false}
                }
              }
            ]
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'Reviewed source-projection actions over deployment-local synchronized fictional stock. No provider API is called by the model.',
        'seeded-simulated-dealership-discovery-v1',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-action-dealership-engagement-v1-0',
        'mkp-action-dealership-engagement-v1',
        '1.0.0',
        'EARLY_ACCESS',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-action-dealership-engagement-v1",
          "version": "1.0.0",
          "pluginType": "ACTION",
          "displayName": "Dealership Engagement Actions",
          "compatibility": {
            "requiredCapabilities": ["actions"],
            "supportedDeploymentTargets": ["custom-start-from-scratch"]
          },
          "pricing": {"pricingModel": "FREE"},
          "installForm": [
            {"id": "receiverActionUrl", "label": "Managed dealership receiver URL", "type": "url", "required": true},
            {"id": "receiverApiKey", "label": "Managed dealership receiver key", "type": "secretRef", "required": true}
          ],
          "permissions": {"contributesActions": true, "requiresExternalHttpExecution": true, "requiresDeploymentSecrets": true},
          "contributions": {
            "actions": [
              {
                "actionId": "dealership_request_callback",
                "adapterType": "connector-http",
                "description": "Create a callback request only after the buyer asks to submit it, supplies contact details and accepts confirmation.",
                "category": "dealership_engagement",
                "readOnly": false,
                "anonymousAllowed": true,
                "requiresConfirmation": true,
                "confirmationMessage": "Send your confirmed contact details to the dealership about this vehicle?",
                "params": [
                  {"name": "vehicle", "description": "Trusted current vehicle target.", "type": "STRING", "required": true, "visibility": "INTERNAL", "askUser": false, "resolveFrom": {"source": "ATTACHMENT_METADATA", "metadataKeys": ["lookupValue", "stockId", "vehicleId", "id"]}, "evidenceBound": true, "evidenceKeys": ["lookupValue", "stockId", "vehicleId", "id"], "evidenceFallbackPolicy": "CLARIFY"},
                  {"name": "name", "description": "Customer name.", "type": "STRING", "required": true, "sensitive": true},
                  {"name": "email", "description": "Optional customer email address.", "type": "STRING", "required": false, "sensitive": true},
                  {"name": "phone", "description": "Customer telephone number.", "type": "STRING", "required": true, "sensitive": true},
                  {"name": "message", "description": "Optional callback notes.", "type": "STRING", "required": false, "sensitive": true},
                  {"name": "consent", "description": "Whether the customer agreed to be contacted.", "type": "BOOLEAN", "required": true}
                ],
                "route": {
                  "managedUrlField": "receiverActionUrl",
                  "managedHeaderSecretRefFields": {"X-DEALERSHIP-INTERNAL-KEY": "receiverApiKey"},
                  "method": "POST",
                  "idempotencyHeader": "Idempotency-Key",
                  "request": {"body": {"actionId": "{{actionId}}", "idempotencyKey": "{{idempotencyKey}}", "params": {"confirmationAccepted": true, "vehicleReference": "{{params.vehicle}}", "name": "{{params.name}}", "email": "{{params.email}}", "phone": "{{params.phone}}", "message": "{{params.message}}", "consent": "{{params.consent}}"}, "trace": {"requestId": "{{trace.requestId}}", "conversationId": "{{trace.conversationId}}", "authContext": "{{trace.authContext}}"}}},
                  "response": {"successHttpStatus": [200], "result": {"receipt": "{{body.data}}"}, "message": "{{body.message}}"},
                  "authz": {"enabled": false}
                }
              },
              {
                "actionId": "dealership_request_test_drive",
                "adapterType": "connector-http",
                "description": "Create a test-drive request only after the buyer asks to submit it, supplies required contact details and accepts confirmation.",
                "category": "dealership_engagement",
                "readOnly": false,
                "anonymousAllowed": true,
                "requiresConfirmation": true,
                "confirmationMessage": "Send your name, contact details and preferred date to request this test drive?",
                "params": [
                  {"name": "vehicle", "description": "Trusted current vehicle target.", "type": "STRING", "required": true, "visibility": "INTERNAL", "askUser": false, "resolveFrom": {"source": "ATTACHMENT_METADATA", "metadataKeys": ["lookupValue", "stockId", "vehicleId", "id"]}, "evidenceBound": true, "evidenceKeys": ["lookupValue", "stockId", "vehicleId", "id"], "evidenceFallbackPolicy": "CLARIFY"},
                  {"name": "name", "description": "Customer name.", "type": "STRING", "required": true, "sensitive": true},
                  {"name": "email", "description": "Customer email address.", "type": "STRING", "required": true, "sensitive": true},
                  {"name": "phone", "description": "Customer telephone number.", "type": "STRING", "required": true, "sensitive": true},
                  {"name": "preferredDate", "description": "Optional preferred date or date range.", "type": "STRING", "required": false},
                  {"name": "message", "description": "Optional test-drive notes.", "type": "STRING", "required": false, "sensitive": true}
                ],
                "route": {
                  "managedUrlField": "receiverActionUrl",
                  "managedHeaderSecretRefFields": {"X-DEALERSHIP-INTERNAL-KEY": "receiverApiKey"},
                  "method": "POST",
                  "idempotencyHeader": "Idempotency-Key",
                  "request": {"body": {"actionId": "{{actionId}}", "idempotencyKey": "{{idempotencyKey}}", "params": {"confirmationAccepted": true, "vehicleReference": "{{params.vehicle}}", "name": "{{params.name}}", "email": "{{params.email}}", "phone": "{{params.phone}}", "preferredDate": "{{params.preferredDate}}", "message": "{{params.message}}"}, "trace": {"requestId": "{{trace.requestId}}", "conversationId": "{{trace.conversationId}}", "authContext": "{{trace.authContext}}"}}},
                  "response": {"successHttpStatus": [200], "result": {"receipt": "{{body.data}}"}, "message": "{{body.message}}"},
                  "authz": {"enabled": false}
                }
              }
            ]
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'Reviewed confirmed write actions. The generated secret-authenticated receiver validates runtime identity, tenant, deployment, active target and idempotency before applying a business effect.',
        'seeded-dealership-engagement-v1',
        current_timestamp,
        current_timestamp
    ),
    (
        'mkv-template-simulated-dealership-experience-v1-0',
        'mkp-template-simulated-dealership-experience-v1',
        '1.0.0',
        'EARLY_ACCESS',
        'PUBLISHED',
        '{
          "schemaVersion": 1,
          "pluginId": "mkp-template-simulated-dealership-experience-v1",
          "version": "1.0.0",
          "pluginType": "TEMPLATE",
          "displayName": "Simulated Dealership AI Experience",
          "description": "Early-access self-service composition with fictional provider data. It does not claim Auto Trader approval, credentials or production access.",
          "compatibility": {
            "requiredCapabilities": ["templates", "shellConfig"],
            "supportedDeploymentTargets": ["custom-start-from-scratch"]
          },
          "pricing": {"pricingModel": "FREE"},
          "permissions": {"contributesTemplate": true, "contributesShellPresentation": true},
          "contributions": {
            "template": {
              "templateId": "custom-start-from-scratch",
              "deploymentBehavior": {
                "type": "CONVERSATIONAL",
                "contractVersion": 1,
                "requiredRuntimeCapabilityIds": ["ai-fabric-core", "ai-fabric-chat-session"],
                "allowedExecutionExtensions": ["GOVERNED_RESOLVER", "CONVERSATION_HANDOVER"],
                "defaultExecutionExtensions": ["GOVERNED_RESOLVER", "CONVERSATION_HANDOVER"],
                "allowedChannelBindings": ["BACKEND_API", "DOCKED_COMPOSER", "MAX_MODE", "INLINE_ASSISTANT"],
                "verificationPackIds": ["conversational-behavior-v1"]
              },
              "security": {
                "authzMode": "ALLOW_VERIFIED",
                "publicRuntimeBootstrapEnabled": true,
                "publicRuntimeTokenIssuer": "runtime-public-bootstrap",
                "publicRuntimeAcceptedIssuers": "runtime-public-bootstrap",
                "publicRuntimeAcceptedAudiences": "loomai-ai-workspace",
                "publicRuntimeDefaultAudience": "loomai-ai-workspace"
              },
              "requiredPluginRefs": [
                "mkp-data-simulated-dealership-stock-v1@1.0.0",
                "mkp-action-simulated-dealership-discovery-v1@1.0.0",
                "mkp-action-dealership-engagement-v1@1.0.0",
                "mkp-data-dealership-knowledge-v1@1.0.0",
                "mkp-action-conversation-handover@1.0.0",
                "mkp-integration-basic-loomai-inbox@1.0.0"
              ],
              "requiredPluginInstallDefaults": {
                "mkp-data-simulated-dealership-stock-v1@1.0.0": {
                  "config": {"providerBaseUrl": "\u0024{MANAGED_SIMULATOR_BASE_URL}", "providerHost": "\u0024{MANAGED_SIMULATOR_HOST}", "advertiserId": "\u0024{MANAGED_SIMULATOR_ACCOUNT_ID}"},
                  "secretRefs": {"providerKey": "MANAGED_SIMULATOR_PROVIDER_KEY", "providerSecret": "MANAGED_SIMULATOR_PROVIDER_SECRET", "notificationSecret": "MANAGED_SIMULATOR_NOTIFICATION_SECRET"}
                },
                "mkp-action-dealership-engagement-v1@1.0.0": {
                  "config": {"receiverActionUrl": "\u0024{MANAGED_RECEIVER_ACTION_URL}"},
                  "secretRefs": {"receiverApiKey": "MANAGED_RECEIVER_API_KEY"}
                },
                "mkp-data-dealership-knowledge-v1@1.0.0": {
                  "config": {"publicContentApprovalConfirmed": true}
                },
                "mkp-integration-basic-loomai-inbox@1.0.0": {
                  "config": {"providerBaseUrl": "\u0024{MANAGED_INBOX_BASE_URL}", "operatorConsoleUrl": "\u0024{MANAGED_INBOX_CONSOLE_URL}"},
                  "secretRefs": {"providerApiKeySecretRef": "MANAGED_INBOX_API_KEY", "runtimeCallbackSigningSecretRef": "MANAGED_INBOX_CALLBACK_SIGNING_SECRET", "providerWebhookSigningSecretRef": "MANAGED_INBOX_PROVIDER_WEBHOOK_SIGNING_SECRET"}
                }
              },
              "shell": {
                "greeting": {"title": "Dealership AI Workspace", "message": "Search current fictional stock, compare vehicles, ask about approved dealership policies, request follow-up or transfer to a person."},
                "starterPrompts": [
                  {"id": "electric-family-suv", "label": "Electric family SUV", "query": "Show me an electric family SUV under GBP 35,000"},
                  {"id": "delivery-policy", "label": "Delivery policy", "query": "Compare suitable cars and explain the approved delivery policy"}
                ],
                "defaultConversationMode": "executor"
              }
            }
          }
        }',
        'mpub-loom',
        'system',
        'system',
        current_timestamp,
        'Reviewed early-access composition. Customer document storage remains a separately authorized binding and the real Auto Trader packages remain reserved drafts.',
        'seeded-simulated-dealership-experience-v1',
        current_timestamp,
        current_timestamp
    );
