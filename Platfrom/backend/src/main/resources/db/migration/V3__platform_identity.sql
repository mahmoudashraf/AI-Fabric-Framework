create table platform_users (
    id varchar(64) primary key,
    email varchar(255) not null unique,
    display_name varchar(255) not null,
    password_hash varchar(255) not null,
    role varchar(64) not null,
    status varchar(64) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    last_login_at timestamp with time zone
);

create table platform_user_sessions (
    id varchar(64) primary key,
    user_id varchar(64) not null,
    token_hash varchar(128) not null unique,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    last_seen_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    constraint fk_platform_user_sessions_user
        foreign key (user_id) references platform_users (id)
);

create index idx_platform_user_sessions_user_id
    on platform_user_sessions (user_id);
