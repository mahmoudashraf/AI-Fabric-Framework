create table if not exists platform_customers (
    id varchar(64) primary key,
    name varchar(255) not null,
    slug varchar(255) not null unique,
    description varchar(1000),
    status varchar(64) not null,
    platform_managed boolean not null default false,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists platform_customer_tenants (
    id varchar(64) primary key,
    customer_id varchar(64) not null,
    name varchar(255) not null,
    slug varchar(255) not null,
    description varchar(1000),
    status varchar(64) not null,
    platform_managed boolean not null default false,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_platform_customer_tenants_customer
        foreign key (customer_id) references platform_customers (id) on delete cascade,
    constraint uq_platform_customer_tenants_customer_slug
        unique (customer_id, slug)
);

alter table platform_deployments
    add column if not exists customer_id varchar(64);

alter table platform_deployments
    add column if not exists tenant_id varchar(64);

insert into platform_customers (
    id,
    name,
    slug,
    description,
    status,
    platform_managed,
    created_at,
    updated_at
)
select
    'cust-internal',
    'Platform Internal',
    'platform-internal',
    'Platform-owned customer boundary for internal, legacy, and system-created deployments.',
    'ACTIVE',
    true,
    current_timestamp,
    current_timestamp
where not exists (
    select 1 from platform_customers where id = 'cust-internal'
);

insert into platform_customer_tenants (
    id,
    customer_id,
    name,
    slug,
    description,
    status,
    platform_managed,
    created_at,
    updated_at
)
select
    'ten-' || substring(d.id, 5),
    'cust-internal',
    case
        when d.name is null or trim(d.name) = '' then d.id || ' Tenant'
        else d.name || ' Tenant'
    end,
    'tenant-' || substring(d.id, 5),
    'Auto-created tenant binding for a deployment that existed before customer and tenant modeling.',
    'ACTIVE',
    true,
    current_timestamp,
    current_timestamp
from platform_deployments d
where d.tenant_id is null
  and not exists (
    select 1
    from platform_customer_tenants t
    where t.id = 'ten-' || substring(d.id, 5)
  );

update platform_deployments
set customer_id = 'cust-internal'
where customer_id is null;

update platform_deployments
set tenant_id = 'ten-' || substring(id, 5)
where tenant_id is null;

alter table platform_deployments
    add constraint fk_platform_deployments_customer
        foreign key (customer_id) references platform_customers (id);

alter table platform_deployments
    add constraint fk_platform_deployments_tenant
        foreign key (tenant_id) references platform_customer_tenants (id);

create index if not exists idx_platform_deployments_customer
    on platform_deployments (customer_id);

create unique index if not exists uq_platform_deployments_tenant
    on platform_deployments (tenant_id);

alter table platform_deployments
    alter column customer_id set not null;

alter table platform_deployments
    alter column tenant_id set not null;
