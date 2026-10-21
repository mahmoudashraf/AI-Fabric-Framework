-- Action metadata only. Preserve published versions, template pins, routes and
-- external business-system ownership. Adoption requires an explicit new pin.
with source as (
    select manifest_json::jsonb as m
      from platform_marketplace_plugin_versions
     where id = 'mkv-action-dealership-external-discovery-v1-0'
), revised as (
    select jsonb_set(jsonb_set(m, '{version}', '"1.1.0"'::jsonb),
        '{contributions,actions}', (
            select jsonb_agg(
                case when a ->> 'actionId' = 'dealership_search_inventory' then
                    jsonb_set(jsonb_set(a, '{description}', to_jsonb(
                        'Search current synchronized stock using only explicit constraints supported by the typed parameters. Preserve stated fuel, budget, mileage and supported body style constraints. Suitability requests such as family-friendly, practical, spacious or commuting are not categorical filters; assess them only from returned vehicle facts and disclose missing evidence. Never invent a body style or silently relax an explicit filter. For electric vehicles under GBP40000 suitable for a family, use fuelType Electric and maxPriceGbp 40000; omit bodyType and make unless explicitly stated.'::text)),
                        '{params}', (
                            select jsonb_agg(
                                case p ->> 'name'
                                    when 'make' then p || jsonb_build_object('description',
                                        'Exact vehicle manufacturer brand only. Put fuel or powertrain terms in fuelType. Omit when no manufacturer was stated; do not send an empty string or a suitability term.')
                                    when 'fuelType' then p || jsonb_build_object('description',
                                        'Explicit buyer-stated fuel type from allowedValues, using its canonical spelling (for example, electric becomes Electric). Omit when no fuel type was stated.')
                                    when 'bodyType' then p || jsonb_build_object('description',
                                        'Explicit buyer-stated vehicle body style from allowedValues, using its canonical spelling. Omit when no supported body style was stated; never infer SUV or another body style from intended use. Family, family-friendly, practical, spacious and commuting are suitability criteria, not body styles. If an unsupported body style is an explicit hard requirement, ask for clarification rather than silently dropping or replacing it. This integration vocabulary must be reviewed against the bound external stock provider before adoption.',
                                        'allowedValues', '["SUV", "Estate", "Hatchback", "Saloon", "Crossover"]'::jsonb)
                                    else p
                                end order by pn)
                              from jsonb_array_elements(a -> 'params') with ordinality as params(p, pn)
                        ))
                    else a
                end order by an)
              from jsonb_array_elements(m #> '{contributions,actions}') with ordinality as actions(a, an)
        )) as m
      from source
)
insert into platform_marketplace_plugin_versions (
    id, plugin_id, version, release_channel, status, manifest_json,
    submitted_by_publisher_id, submitted_by_actor_id, reviewed_by_actor_id,
    reviewed_at, review_notes, bundle_sha256, created_at, published_at
)
select 'mkv-action-dealership-external-discovery-v1-1', 'mkp-action-dealership-external-discovery-v1',
    '1.1.0', 'EARLY_ACCESS', 'PUBLISHED', m::text, 'mpub-loom', 'system', 'system', current_timestamp,
    'Typed inventory guidance separates explicit categorical filters from suitability. Body-style vocabulary covers the reviewed local provider fixture, not a universal provider enum. No route, executor, template, customer binding or business-system change.',
    encode(sha256(convert_to(m::text, 'UTF8')), 'hex'), current_timestamp, current_timestamp
  from revised
on conflict (id) do nothing;
