-- A version chooses one immutable credential generation for its retained external AI schemas.
-- New credentials never replace a live version's secret references or approval.
create table deployment_ai_state_storage_credential_generations (
    binding_id varchar(64) not null,
    version_id varchar(64) not null,
    generation_id varchar(64) not null unique,
    generation_json text not null,
    fingerprint varchar(64) not null,
    created_at timestamp with time zone not null,
    primary key (binding_id, version_id),
    foreign key (binding_id, version_id) references deployment_ai_state_storage_approvals(binding_id, version_id) on delete cascade
);
