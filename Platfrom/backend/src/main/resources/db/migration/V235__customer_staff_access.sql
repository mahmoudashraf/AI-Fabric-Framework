create table platform_customer_staff_access (
    id varchar(64) primary key,
    customer_id varchar(64) not null,
    user_id varchar(64) not null,
    status varchar(16) not null,
    granted_by varchar(255) not null,
    granted_at timestamp with time zone not null,
    revoked_by varchar(255),
    revoked_at timestamp with time zone,
    constraint fk_customer_staff_access_customer foreign key (customer_id) references platform_customers (id),
    constraint fk_customer_staff_access_user foreign key (user_id) references platform_users (id),
    constraint uq_customer_staff_access_member unique (customer_id, user_id),
    constraint ck_customer_staff_access_status check (status in ('ACTIVE', 'REVOKED')),
    constraint ck_customer_staff_access_revocation check (
        (status = 'ACTIVE' and revoked_by is null and revoked_at is null)
        or (status = 'REVOKED' and revoked_by is not null and revoked_at is not null)
    )
);

-- Revoked rows remain explicit denials; they must not turn into missing-grant fallback.
create index idx_customer_staff_access_visibility on platform_customer_staff_access (user_id, status, customer_id);
