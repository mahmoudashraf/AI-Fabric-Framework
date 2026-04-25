alter table partner_evidence_bundles
    add column if not exists verification_run_id varchar(64);

alter table partner_support_escalations
    add column if not exists evidence_bundle_ids_json text not null default '[]';

create table if not exists partner_verification_runs (
    id varchar(64) primary key,
    partner_account_id varchar(64) not null,
    store_assignment_id varchar(64) not null,
    pack_id varchar(128) not null,
    pack_name varchar(255) not null,
    status varchar(64) not null,
    summary_json text not null default '{}',
    started_by_member_id varchar(64) not null,
    evidence_bundle_id varchar(64),
    started_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint fk_partner_verification_runs_account
        foreign key (partner_account_id) references partner_accounts(id) on delete cascade,
    constraint fk_partner_verification_runs_assignment
        foreign key (store_assignment_id) references partner_store_assignments(id),
    constraint fk_partner_verification_runs_member
        foreign key (started_by_member_id) references partner_members(id)
);

create index if not exists idx_partner_verification_runs_account
    on partner_verification_runs(partner_account_id, started_at desc);

create index if not exists idx_partner_verification_runs_store
    on partner_verification_runs(store_assignment_id, started_at desc);

create table if not exists partner_verification_run_steps (
    id varchar(64) primary key,
    run_id varchar(64) not null,
    step_id varchar(128) not null,
    step_label varchar(255) not null,
    status varchar(64) not null,
    partner_safe_message text not null,
    suggested_fix text,
    evidence_json text not null default '[]',
    sort_order integer not null,
    checked_at timestamp with time zone not null,
    constraint fk_partner_verification_run_steps_run
        foreign key (run_id) references partner_verification_runs(id) on delete cascade
);

create unique index if not exists uq_partner_verification_run_steps_run_step
    on partner_verification_run_steps(run_id, step_id);

create index if not exists idx_partner_verification_run_steps_run
    on partner_verification_run_steps(run_id, sort_order asc);

create table if not exists partner_template_applications (
    id varchar(64) primary key,
    partner_account_id varchar(64) not null,
    store_assignment_id varchar(64),
    template_id varchar(128) not null,
    template_name varchar(255) not null,
    category varchar(64) not null,
    applied_by_member_id varchar(64) not null,
    status varchar(64) not null,
    checklist_json text not null default '[]',
    assumptions_json text not null default '[]',
    applied_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_partner_template_applications_account
        foreign key (partner_account_id) references partner_accounts(id) on delete cascade,
    constraint fk_partner_template_applications_assignment
        foreign key (store_assignment_id) references partner_store_assignments(id),
    constraint fk_partner_template_applications_member
        foreign key (applied_by_member_id) references partner_members(id)
);

create index if not exists idx_partner_template_applications_account
    on partner_template_applications(partner_account_id, applied_at desc);

create index if not exists idx_partner_template_applications_store
    on partner_template_applications(store_assignment_id, applied_at desc);

create table if not exists partner_store_notes (
    id varchar(64) primary key,
    partner_account_id varchar(64) not null,
    store_assignment_id varchar(64) not null,
    created_by_member_id varchar(64) not null,
    body_markdown text not null,
    status varchar(64) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_partner_store_notes_account
        foreign key (partner_account_id) references partner_accounts(id) on delete cascade,
    constraint fk_partner_store_notes_assignment
        foreign key (store_assignment_id) references partner_store_assignments(id),
    constraint fk_partner_store_notes_member
        foreign key (created_by_member_id) references partner_members(id)
);

create index if not exists idx_partner_store_notes_store
    on partner_store_notes(store_assignment_id, created_at desc);
