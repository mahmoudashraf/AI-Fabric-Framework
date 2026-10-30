CREATE TABLE desk_context_provider (
  binding_id VARCHAR(160) PRIMARY KEY REFERENCES desk_binding(binding_id),
  provider_url VARCHAR(2048) NOT NULL,
  protected_service_key TEXT NOT NULL,
  resolve_enabled BOOLEAN NOT NULL
);
