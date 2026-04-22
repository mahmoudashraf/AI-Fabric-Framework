create table if not exists platform_marketplace_dataset_documents (
    id varchar(64) primary key,
    dataset_handle_id varchar(64) not null,
    document_id varchar(256) not null,
    entity_type varchar(128) not null,
    content_fingerprint varchar(128) not null,
    dataset_hash varchar(128) not null,
    metadata_json text,
    last_synced_at timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uq_marketplace_dataset_document unique (dataset_handle_id, document_id)
);

create index if not exists idx_marketplace_dataset_documents_handle
    on platform_marketplace_dataset_documents (dataset_handle_id, document_id);
