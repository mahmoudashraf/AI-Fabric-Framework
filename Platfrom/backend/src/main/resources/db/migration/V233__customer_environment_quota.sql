ALTER TABLE platform_customers
    ADD COLUMN infrastructure_environment_limit integer NOT NULL DEFAULT 2
    CHECK (infrastructure_environment_limit >= 0);
