create table if not exists platform_shopify_store_documents (
    id varchar(64) primary key,
    store_connection_id varchar(64) not null,
    document_id varchar(256) not null,
    source_category varchar(64) not null,
    entity_type varchar(128) not null,
    content_fingerprint varchar(128) not null,
    metadata_json text,
    last_synced_at timestamp not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uq_platform_shopify_store_document unique (store_connection_id, document_id)
);

create index if not exists idx_platform_shopify_store_documents_store
    on platform_shopify_store_documents (store_connection_id, document_id);
