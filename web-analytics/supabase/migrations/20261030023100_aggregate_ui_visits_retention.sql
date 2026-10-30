begin;
create extension if not exists pg_cron;
select cron.schedule('loomai-ui-visits-retention', '17 3 * * *',
  $$delete from loomai_web_analytics.hourly_visits where hour_bucket < now() - interval '90 days'$$)
where not exists (select 1 from cron.job where jobname = 'loomai-ui-visits-retention');
commit;
