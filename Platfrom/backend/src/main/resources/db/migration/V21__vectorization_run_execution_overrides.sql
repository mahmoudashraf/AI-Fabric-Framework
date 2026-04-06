alter table platform_vectorization_runs
    add column if not exists execution_overrides_json text not null default '{}';
