create table if not exists platform_user_preferences (
    id varchar(64) primary key,
    user_id varchar(64) not null,
    preference_key varchar(128) not null,
    preference_json text not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_platform_user_preferences_user
        foreign key (user_id) references platform_users (id) on delete cascade,
    constraint uq_platform_user_preferences
        unique (user_id, preference_key)
);

create index if not exists idx_platform_user_preferences_user
    on platform_user_preferences (user_id);
