alter table partner_client_implementation_requests
    add column if not exists store_connection_id varchar(64);

alter table partner_store_access_requests
    add column if not exists store_connection_id varchar(64);

create index if not exists idx_partner_client_requests_store
    on partner_client_implementation_requests(store_connection_id);

create index if not exists idx_partner_store_access_requests_store
    on partner_store_access_requests(store_connection_id);

create index if not exists idx_partner_store_access_requests_shop
    on partner_store_access_requests(shop_domain, created_at desc);

create index if not exists idx_partner_store_access_requests_account_store_status
    on partner_store_access_requests(partner_account_id, store_connection_id, status);

create index if not exists idx_partner_store_assignments_account_store_status
    on partner_store_assignments(partner_account_id, store_connection_id, status);
