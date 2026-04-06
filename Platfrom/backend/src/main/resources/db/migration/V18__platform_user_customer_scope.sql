alter table platform_users
    add column if not exists customer_id varchar(64);

alter table platform_users
    drop constraint if exists fk_platform_users_customer;

alter table platform_users
    add constraint fk_platform_users_customer
        foreign key (customer_id) references platform_customers (id);

create index if not exists idx_platform_users_customer
    on platform_users (customer_id);
