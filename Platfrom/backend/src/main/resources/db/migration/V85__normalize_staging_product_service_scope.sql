-- Keep the compatibility Shopify Bridge service ref, but make the active staging
-- product-service scope match the Coolify staging target it is bound to.
update platform_managed_product_services
set environment_scope = 'staging',
    updated_at = current_timestamp
where lower(service_ref) = 'shopify-bridge-prod'
  and (
      lower(base_url) like '%shopify-bridge-staging.46.224.145.148.sslip.io%'
      or lower(base_url) like '%loomai-shopify-bridge-staging.46.224.145.148.sslip.io%'
      or lower(base_url) like '%46.224.145.148%'
  );
