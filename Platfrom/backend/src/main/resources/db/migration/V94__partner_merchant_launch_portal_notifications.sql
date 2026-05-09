alter table partner_store_access_requests
    add column if not exists invite_recipient_email varchar(255);

alter table partner_store_access_requests
    add column if not exists invite_status varchar(64);

alter table partner_store_access_requests
    add column if not exists invite_channel varchar(64);

alter table partner_store_access_requests
    add column if not exists invite_message text;

alter table partner_store_access_requests
    add column if not exists invite_sent_at timestamp with time zone;

alter table partner_store_access_requests
    add column if not exists invite_count integer not null default 0;
