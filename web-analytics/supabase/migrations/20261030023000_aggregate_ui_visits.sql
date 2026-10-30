begin;

create schema if not exists loomai_web_analytics;
revoke all on schema loomai_web_analytics from public, anon, authenticated;

create table if not exists loomai_web_analytics.hourly_visits (
  hour_bucket timestamptz not null,
  site text not null check (site in ('ai-fabric', 'loomai-site', 'console', 'desk', 'engage', 'partners', 'shopify-bridge')),
  hostname text not null check (length(hostname) <= 100),
  page text not null check (length(page) <= 40),
  viewport text not null check (viewport in ('mobile', 'tablet', 'desktop')),
  views bigint not null default 1 check (views between 1 and 100000),
  primary key (hour_bucket, hostname, page, viewport)
);
alter table loomai_web_analytics.hourly_visits enable row level security;
revoke all on loomai_web_analytics.hourly_visits from public, anon, authenticated;

create or replace function public.record_loomai_ui_visit(p_site text, p_hostname text, p_page text, p_viewport text)
returns void language plpgsql security definer set search_path = '' as $$
begin
  insert into loomai_web_analytics.hourly_visits(hour_bucket, site, hostname, page, viewport, views)
  values (pg_catalog.date_trunc('hour', pg_catalog.now()), p_site, p_hostname, p_page, p_viewport, 1)
  on conflict (hour_bucket, hostname, page, viewport)
  do update set views = least(loomai_web_analytics.hourly_visits.views + 1, 100000);
end;
$$;
revoke all on function public.record_loomai_ui_visit(text, text, text, text) from public, anon, authenticated;
grant execute on function public.record_loomai_ui_visit(text, text, text, text) to service_role;

create or replace view loomai_web_analytics.daily_visits as
select (hour_bucket at time zone 'UTC')::date as day_utc, site, hostname, page, sum(views) as views
from loomai_web_analytics.hourly_visits
group by 1, site, hostname, page;
revoke all on loomai_web_analytics.daily_visits from public, anon, authenticated;

comment on table loomai_web_analytics.hourly_visits is
'Aggregate page views only. No visitor identity, IP, user agent, referrer, query string, chat or form contents. Not unique visitors or billing evidence.';

commit;
