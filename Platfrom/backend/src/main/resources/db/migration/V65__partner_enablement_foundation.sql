create table if not exists partner_accounts (
    id varchar(64) primary key,
    name varchar(255) not null,
    status varchar(64) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_partner_accounts_status
    on partner_accounts(status, created_at desc);

create table if not exists partner_members (
    id varchar(64) primary key,
    partner_account_id varchar(64) not null,
    supabase_user_id varchar(128) not null unique,
    auth_provider varchar(64),
    auth_provider_subject varchar(255),
    email varchar(255) not null,
    email_verified boolean not null default false,
    display_name varchar(255),
    avatar_url text,
    role varchar(64) not null,
    status varchar(64) not null,
    last_login_at timestamp with time zone,
    last_auth_provider_seen_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_partner_members_account
        foreign key (partner_account_id) references partner_accounts(id) on delete cascade
);

create index if not exists idx_partner_members_account
    on partner_members(partner_account_id);

create index if not exists idx_partner_members_email
    on partner_members(email);

create table if not exists partner_client_implementation_requests (
    id varchar(64) primary key,
    partner_account_id varchar(64) not null,
    created_by_member_id varchar(64) not null,
    client_name varchar(255) not null,
    contact_email varchar(255),
    shop_domain varchar(255) not null,
    vertical varchar(64),
    requested_tier varchar(64) not null,
    requested_surfaces_json text not null,
    known_integrations_json text not null default '[]',
    notes text,
    status varchar(64) not null,
    approval_code varchar(128) unique,
    approval_url text,
    approval_expires_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_partner_client_requests_account
        foreign key (partner_account_id) references partner_accounts(id) on delete cascade,
    constraint fk_partner_client_requests_member
        foreign key (created_by_member_id) references partner_members(id)
);

create index if not exists idx_partner_client_requests_account
    on partner_client_implementation_requests(partner_account_id, created_at desc);

create index if not exists idx_partner_client_requests_shop
    on partner_client_implementation_requests(shop_domain);

create table if not exists partner_store_access_requests (
    id varchar(64) primary key,
    partner_account_id varchar(64) not null,
    implementation_request_id varchar(64) not null,
    requested_by_member_id varchar(64) not null,
    shop_domain varchar(255) not null,
    requested_scope varchar(64) not null,
    status varchar(64) not null,
    approval_code varchar(128) not null unique,
    approval_url text not null,
    expires_at timestamp with time zone not null,
    approved_at timestamp with time zone,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_partner_store_access_requests_account
        foreign key (partner_account_id) references partner_accounts(id) on delete cascade,
    constraint fk_partner_store_access_requests_implementation
        foreign key (implementation_request_id) references partner_client_implementation_requests(id) on delete cascade,
    constraint fk_partner_store_access_requests_member
        foreign key (requested_by_member_id) references partner_members(id)
);

create index if not exists idx_partner_store_access_requests_account
    on partner_store_access_requests(partner_account_id, created_at desc);

create table if not exists partner_store_access_approvals (
    id varchar(64) primary key,
    access_request_id varchar(64) not null,
    partner_account_id varchar(64) not null,
    shop_domain varchar(255) not null,
    approver_name varchar(255),
    approver_email varchar(255),
    approved_scope varchar(64) not null,
    source_flow varchar(64) not null,
    approved_at timestamp with time zone not null,
    details_json text not null default '{}',
    constraint fk_partner_store_access_approvals_request
        foreign key (access_request_id) references partner_store_access_requests(id) on delete cascade,
    constraint fk_partner_store_access_approvals_account
        foreign key (partner_account_id) references partner_accounts(id) on delete cascade
);

create index if not exists idx_partner_store_access_approvals_account
    on partner_store_access_approvals(partner_account_id, approved_at desc);

create table if not exists partner_store_assignments (
    id varchar(64) primary key,
    partner_account_id varchar(64) not null,
    store_connection_id varchar(64),
    shop_domain varchar(255) not null,
    merchant_name varchar(255),
    status varchar(64) not null,
    assignment_source varchar(64) not null,
    approved_by varchar(255),
    permissions_json text not null default '[]',
    approved_at timestamp with time zone,
    suspended_at timestamp with time zone,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_partner_store_assignments_account
        foreign key (partner_account_id) references partner_accounts(id) on delete cascade,
    constraint fk_partner_store_assignments_store
        foreign key (store_connection_id) references shopify_store_connections(id)
);

create unique index if not exists uq_partner_store_assignments_account_shop
    on partner_store_assignments(partner_account_id, shop_domain);

create index if not exists idx_partner_store_assignments_store
    on partner_store_assignments(store_connection_id);

create table if not exists partner_action_audits (
    id varchar(64) primary key,
    partner_account_id varchar(64),
    partner_member_id varchar(64),
    action varchar(128) not null,
    target_type varchar(64),
    target_id varchar(128),
    result varchar(64) not null,
    details_json text not null default '{}',
    created_at timestamp with time zone not null
);

create index if not exists idx_partner_action_audits_account
    on partner_action_audits(partner_account_id, created_at desc);

create table if not exists partner_support_escalations (
    id varchar(64) primary key,
    partner_account_id varchar(64) not null,
    store_assignment_id varchar(64),
    created_by_member_id varchar(64) not null,
    title varchar(255) not null,
    severity varchar(32) not null,
    status varchar(64) not null,
    next_action text,
    due_at timestamp with time zone,
    description text not null,
    reproduction_steps text,
    expected_behavior text,
    actual_behavior text,
    impact text,
    resolution_summary text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_partner_support_escalations_account
        foreign key (partner_account_id) references partner_accounts(id) on delete cascade,
    constraint fk_partner_support_escalations_assignment
        foreign key (store_assignment_id) references partner_store_assignments(id),
    constraint fk_partner_support_escalations_member
        foreign key (created_by_member_id) references partner_members(id)
);

create index if not exists idx_partner_support_escalations_account
    on partner_support_escalations(partner_account_id, updated_at desc);

create table if not exists partner_support_replies (
    id varchar(64) primary key,
    escalation_id varchar(64) not null,
    author_member_id varchar(64),
    author_name varchar(255) not null,
    author_role varchar(64) not null,
    visibility varchar(64) not null,
    body_markdown text not null,
    attachments_json text not null default '[]',
    created_at timestamp with time zone not null,
    constraint fk_partner_support_replies_escalation
        foreign key (escalation_id) references partner_support_escalations(id) on delete cascade
);

create index if not exists idx_partner_support_replies_escalation
    on partner_support_replies(escalation_id, created_at asc);

create table if not exists partner_evidence_bundles (
    id varchar(64) primary key,
    partner_account_id varchar(64) not null,
    store_assignment_id varchar(64),
    generated_by_member_id varchar(64),
    bundle_name varchar(255) not null,
    bundle_kind varchar(64) not null,
    status varchar(64) not null,
    summary_json text not null default '{}',
    attachments_json text not null default '[]',
    generated_at timestamp with time zone not null,
    expires_at timestamp with time zone,
    constraint fk_partner_evidence_bundles_account
        foreign key (partner_account_id) references partner_accounts(id) on delete cascade,
    constraint fk_partner_evidence_bundles_assignment
        foreign key (store_assignment_id) references partner_store_assignments(id),
    constraint fk_partner_evidence_bundles_member
        foreign key (generated_by_member_id) references partner_members(id)
);

create index if not exists idx_partner_evidence_bundles_account
    on partner_evidence_bundles(partner_account_id, generated_at desc);
