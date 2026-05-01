alter table partner_members
    add column if not exists privileges_json text not null default '[]';

create table if not exists partner_package_trial_activations (
    id varchar(64) primary key,
    partner_account_id varchar(64) not null,
    partner_member_id varchar(64) not null,
    store_assignment_id varchar(64) not null,
    store_connection_id varchar(64),
    shop_domain varchar(255) not null,
    package_key varchar(64) not null,
    tier_key varchar(64) not null,
    status varchar(64) not null,
    trial_days integer not null,
    trial_starts_at timestamp with time zone not null,
    trial_ends_at timestamp with time zone not null,
    activated_at timestamp with time zone not null,
    deactivated_at timestamp with time zone,
    deactivated_by_member_id varchar(64),
    activation_reason text,
    deactivation_reason text,
    previous_tier_key varchar(64),
    previous_billing_status varchar(64),
    previous_subscription_id varchar(255),
    previous_subscription_name varchar(255),
    activation_provisioning_job_id varchar(64),
    deactivation_provisioning_job_id varchar(64),
    details_json text not null default '{}',
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_partner_package_trials_account
        foreign key (partner_account_id) references partner_accounts(id) on delete cascade,
    constraint fk_partner_package_trials_member
        foreign key (partner_member_id) references partner_members(id),
    constraint fk_partner_package_trials_assignment
        foreign key (store_assignment_id) references partner_store_assignments(id)
);

create index if not exists idx_partner_package_trials_store
    on partner_package_trial_activations(store_assignment_id, created_at desc);

create index if not exists idx_partner_package_trials_account
    on partner_package_trial_activations(partner_account_id, created_at desc);

create index if not exists idx_partner_package_trials_due
    on partner_package_trial_activations(status, trial_ends_at);

update partner_store_assignments
set permissions_json = '["STORE_READ","CATALOG_READ","PRODUCT_CONFIG_READ","PRODUCT_CONFIG_WRITE","PRODUCT_CONFIG_PUBLISH","STOREFRONT_SURFACE_CONTROL","KNOWLEDGE_SOURCE_CONTROL","KNOWLEDGE_SYNC_TRIGGER","APP_OWNED_CONTENT_WRITE","VERIFICATION_READ","VERIFICATION_RUN","EVIDENCE_READ","EVIDENCE_EXPORT","ESCALATION_CREATE","ESCALATION_REPLY","SUPPORT_MANAGE","AGGREGATE_ANALYTICS_READ","PRODUCT_PAUSE_RESUME","PACKAGE_TRIAL_ACTIVATE"]',
    updated_at = current_timestamp
where status = 'ACTIVE'
  and permissions_json not like '%PACKAGE_TRIAL_ACTIVATE%';
