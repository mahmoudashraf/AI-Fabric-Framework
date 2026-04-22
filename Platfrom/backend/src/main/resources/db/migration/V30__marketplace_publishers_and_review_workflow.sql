create table platform_marketplace_publishers (
    id varchar(64) primary key,
    slug varchar(128) not null,
    display_name varchar(255) not null,
    contact_email varchar(255) not null,
    owner_user_id varchar(64) not null,
    verification_status varchar(64) not null,
    status varchar(64) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index uk_platform_marketplace_publishers_slug
    on platform_marketplace_publishers (slug);

alter table platform_marketplace_plugin_versions
    add column submitted_by_publisher_id varchar(64);

alter table platform_marketplace_plugin_versions
    add column submitted_by_actor_id varchar(64);

alter table platform_marketplace_plugin_versions
    add column reviewed_by_actor_id varchar(64);

alter table platform_marketplace_plugin_versions
    add column reviewed_at timestamp with time zone;

alter table platform_marketplace_plugin_versions
    add column review_notes text;

alter table platform_marketplace_plugin_versions
    add column bundle_sha256 varchar(128);

alter table platform_marketplace_plugin_versions
    add constraint fk_platform_marketplace_plugin_versions_publisher
        foreign key (submitted_by_publisher_id) references platform_marketplace_publishers (id) on delete set null;

insert into platform_marketplace_publishers (
    id,
    slug,
    display_name,
    contact_email,
    owner_user_id,
    verification_status,
    status,
    created_at,
    updated_at
) values (
    'mpub-loom',
    'loom',
    'Loom AI',
    'platform@loom.ai',
    'system',
    'VERIFIED',
    'ACTIVE',
    current_timestamp,
    current_timestamp
);

update platform_marketplace_plugin_versions
set submitted_by_publisher_id = 'mpub-loom',
    submitted_by_actor_id = 'system',
    reviewed_by_actor_id = 'system',
    reviewed_at = published_at,
    review_notes = 'Seeded first-party marketplace plugin version.'
where submitted_by_publisher_id is null;
