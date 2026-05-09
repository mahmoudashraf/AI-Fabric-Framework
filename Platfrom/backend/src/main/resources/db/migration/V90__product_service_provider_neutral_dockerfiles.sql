update platform_managed_product_services
set dockerfile_path = 'product-services/shopify-bridge-service/deploy/container/Dockerfile',
    updated_at = current_timestamp
where service_kind = 'SHOPIFY_BRIDGE_SERVICE'
  and dockerfile_path = 'product-services/shopify-bridge-service/deploy/railway/Dockerfile';

update platform_managed_product_services
set dockerfile_path = 'product-services/mcp-execution-gateway-service/deploy/container/Dockerfile',
    updated_at = current_timestamp
where service_kind = 'MCP_EXECUTION_GATEWAY_SERVICE'
  and dockerfile_path = 'product-services/mcp-execution-gateway-service/deploy/railway/Dockerfile';
