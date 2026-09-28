create table if not exists simulator_run (
    singleton_id integer primary key,
    fixture_version varchar(128) not null,
    reset_id varchar(64) not null,
    reset_at timestamp with time zone not null
);

create table if not exists simulator_account_state (
    profile varchar(32) not null,
    account_id varchar(128) not null,
    source_version bigint not null,
    event_sequence bigint not null,
    updated_at timestamp with time zone not null,
    primary key (profile, account_id)
);

create table if not exists simulator_vehicle (
    profile varchar(32) not null,
    account_id varchar(128) not null,
    vehicle_id varchar(128) not null,
    make_name varchar(128) not null,
    model_name varchar(128) not null,
    derivative varchar(255) not null,
    registration_year integer not null,
    price_minor bigint not null,
    currency varchar(8) not null,
    fuel_type varchar(64) not null,
    body_style varchar(64) not null,
    transmission varchar(64) not null,
    mileage integer not null,
    lifecycle_state varchar(32) not null,
    record_version bigint not null,
    updated_at timestamp with time zone not null,
    primary key (profile, account_id, vehicle_id)
);

create table if not exists simulator_fault (
    profile varchar(32) not null,
    account_id varchar(128) not null,
    fault_mode varchar(64) not null,
    remaining integer not null,
    delay_ms integer not null,
    updated_at timestamp with time zone not null,
    primary key (profile, account_id)
);

create index if not exists idx_simulator_vehicle_account
    on simulator_vehicle (profile, account_id, vehicle_id);
