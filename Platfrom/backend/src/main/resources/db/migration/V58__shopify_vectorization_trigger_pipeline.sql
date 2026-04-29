create table if not exists shopify_store_vectorization_policies (
    id varchar(64) primary key,
    shop_domain varchar(255) not null unique,
    policy_version bigint not null,
    auto_indexing_default boolean not null,
    source_policies_json text not null,
    updated_by varchar(255),
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists shopify_store_vectorization_events (
    id varchar(64) primary key,
    event_type varchar(64) not null,
    event_source varchar(64) not null,
    schema_version varchar(32) not null,
    shop_domain varchar(255) not null,
    deployment_id varchar(64),
    source_category varchar(64) not null,
    entity_type varchar(64) not null,
    source_object_id varchar(255),
    operation varchar(32) not null,
    trigger_reason varchar(64),
    changed_fields_json text,
    source_record_version varchar(255),
    aggregate_key varchar(255) not null,
    dedupe_key varchar(255) not null unique,
    correlation_id varchar(255) not null,
    causation_id varchar(255),
    shopify_webhook_id varchar(255),
    shopify_topic varchar(255),
    delivery_attempt integer,
    payload_checksum varchar(128),
    occurred_at timestamp not null,
    available_at timestamp not null,
    payload_json text,
    headers_json text,
    queued_at timestamp not null,
    status varchar(32) not null,
    coalesced_run_id varchar(64),
    notes text,
    attempt_count integer not null,
    last_attempt_at timestamp,
    next_attempt_at timestamp not null,
    lease_owner varchar(128),
    lease_expires_at timestamp,
    failure_code varchar(64),
    last_error_summary text,
    dead_lettered_at timestamp,
    retention_expires_at timestamp not null,
    completed_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_shopify_vectorization_events_status_next
    on shopify_store_vectorization_events (status, next_attempt_at);

create index if not exists idx_shopify_vectorization_events_aggregate_occurred
    on shopify_store_vectorization_events (aggregate_key, occurred_at);

create index if not exists idx_shopify_vectorization_events_store_entity_object
    on shopify_store_vectorization_events (shop_domain, entity_type, source_object_id);

create index if not exists idx_shopify_vectorization_events_retention
    on shopify_store_vectorization_events (retention_expires_at);

create index if not exists idx_shopify_vectorization_events_run
    on shopify_store_vectorization_events (coalesced_run_id);

create table if not exists shopify_store_vectorization_ledgers (
    id varchar(64) primary key,
    shop_domain varchar(255) not null,
    deployment_id varchar(64) not null,
    source_category varchar(64) not null,
    entity_type varchar(64) not null,
    source_object_id varchar(255) not null,
    source_record_version varchar(255),
    indexed_output_fingerprint varchar(128) not null,
    field_fingerprints_json text,
    first_indexed_at timestamp,
    last_indexed_at timestamp not null,
    last_seen_at timestamp not null,
    last_index_run_id varchar(64),
    last_indexed_deployment_hash varchar(128),
    deleted_at timestamp,
    last_trigger_decision varchar(64),
    last_trigger_reason varchar(64),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uq_shopify_vectorization_ledger unique (deployment_id, entity_type, source_object_id)
);

create index if not exists idx_shopify_vectorization_ledgers_shop_entity
    on shopify_store_vectorization_ledgers (shop_domain, entity_type, deleted_at);

create index if not exists idx_shopify_vectorization_ledgers_source_category
    on shopify_store_vectorization_ledgers (deployment_id, entity_type, source_category);
