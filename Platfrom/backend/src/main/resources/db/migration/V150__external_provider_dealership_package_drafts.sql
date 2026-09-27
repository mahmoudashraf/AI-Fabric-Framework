insert into platform_marketplace_plugins (
    id,
    slug,
    display_name,
    plugin_type,
    publisher_slug,
    publisher_display_name,
    short_description,
    status,
    created_at,
    updated_at
)
select
    seed.id,
    seed.slug,
    seed.display_name,
    seed.plugin_type,
    'loom',
    'Loom AI',
    seed.short_description,
    'DRAFT',
    current_timestamp,
    current_timestamp
from (values
    (
        'mkp-data-autotrader-dealership-stock-v1',
        'autotrader-dealership-stock-v1',
        'Auto Trader Dealership Stock',
        'DATA',
        'Reserved provider package. Not installable until Auto Trader sandbox grants, data rights, exact schemas, and hosted verification are complete.'
    ),
    (
        'mkp-action-autotrader-dealership-discovery-v1',
        'autotrader-dealership-discovery-v1',
        'Auto Trader Dealership Discovery',
        'ACTION',
        'Reserved provider package. Not installable until capability-specific Auto Trader read grants and sandbox verification are complete.'
    ),
    (
        'mkp-data-dealership-knowledge-v1',
        'dealership-knowledge-v1',
        'Dealership Knowledge',
        'DATA',
        'Reserved dealership-owned knowledge package pending the approved demonstration source and complete lifecycle evidence.'
    ),
    (
        'mkp-action-dealership-lead-v1',
        'dealership-lead-v1',
        'Dealership Lead Action',
        'ACTION',
        'Reserved confirmed dealership-owned action package pending a real customer application boundary and receipt workflow.'
    ),
    (
        'mkp-template-autotrader-dealership-concierge-v1',
        'autotrader-dealership-concierge-v1',
        'Auto Trader Dealership Concierge',
        'TEMPLATE',
        'Reserved composition. It must remain non-installable until every required package and the applicable provider readiness gate pass.'
    )
) as seed(id, slug, display_name, plugin_type, short_description)
where not exists (
    select 1
    from platform_marketplace_plugins existing
    where existing.id = seed.id or lower(existing.slug) = lower(seed.slug)
);

-- Deliberately no platform_marketplace_plugin_versions rows are created here.
-- A submitted or published version would imply a reviewed, executable provider
-- contract. That contract cannot exist before partner-issued sandbox access,
-- capability grants, test advertiser scope, data-use rights, and webhook rules.
