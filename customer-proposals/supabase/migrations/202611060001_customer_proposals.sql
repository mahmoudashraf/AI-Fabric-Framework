begin;

create schema if not exists loomai_customer_proposals;
revoke all on schema loomai_customer_proposals from public, anon, authenticated;

create table if not exists loomai_customer_proposals.customer_proposals (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' and length(slug) <= 64),
  recipient_label text not null,
  version integer not null default 1 check (version > 0),
  status text not null default 'draft' check (status in ('draft', 'published', 'withdrawn')),
  content jsonb not null check (jsonb_typeof(content) = 'object' and octet_length(content::text) <= 500000),
  docx_base64 text check (length(docx_base64) <= 3000000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists loomai_customer_proposals.customer_proposal_codes (
  id uuid primary key default gen_random_uuid(),
  proposal_id uuid not null references loomai_customer_proposals.customer_proposals(id),
  proposal_version integer not null,
  code_digest text not null unique check (code_digest ~ '^[a-f0-9]{64}$'),
  label text not null default 'Invitation',
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  revoked_at timestamptz,
  check (expires_at > created_at)
);

create table if not exists loomai_customer_proposals.customer_proposal_sessions (
  token_digest text primary key check (token_digest ~ '^[a-f0-9]{64}$'),
  proposal_id uuid not null references loomai_customer_proposals.customer_proposals(id),
  code_id uuid not null references loomai_customer_proposals.customer_proposal_codes(id),
  proposal_version integer not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null
);
create index if not exists customer_proposal_session_expiry
  on loomai_customer_proposals.customer_proposal_sessions(expires_at);

create table if not exists loomai_customer_proposals.access_attempts (
  window_start timestamptz not null,
  client_digest text not null check (client_digest ~ '^[a-f0-9]{64}$'),
  attempts integer not null,
  primary key (window_start, client_digest)
);

alter table loomai_customer_proposals.customer_proposals enable row level security;
alter table loomai_customer_proposals.customer_proposal_codes enable row level security;
alter table loomai_customer_proposals.customer_proposal_sessions enable row level security;
alter table loomai_customer_proposals.access_attempts enable row level security;
revoke all on all tables in schema loomai_customer_proposals from public, anon, authenticated;

create or replace function public.exchange_customer_proposal_code(
  p_slug text, p_code_digest text, p_token_digest text, p_client_digest text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_attempts integer;
  v_window timestamptz := pg_catalog.date_bin('10 minutes', pg_catalog.now(), '2026-01-01'::timestamptz);
  v_proposal uuid;
  v_code uuid;
  v_version integer;
  v_expiry timestamptz;
begin
  delete from loomai_customer_proposals.access_attempts where window_start < now() - interval '1 day';
  delete from loomai_customer_proposals.customer_proposal_sessions where expires_at < now() - interval '1 day';
  insert into loomai_customer_proposals.access_attempts values (v_window, p_client_digest, 1)
  on conflict (window_start, client_digest) do update
    set attempts = least(loomai_customer_proposals.access_attempts.attempts + 1, 100000)
  returning attempts into v_attempts;
  if v_attempts > 20 then return jsonb_build_object('error', 'RATE_LIMITED'); end if;

  select p.id, c.id, p.version, least(c.expires_at, now() + interval '4 hours')
    into v_proposal, v_code, v_version, v_expiry
  from loomai_customer_proposals.customer_proposals p
  join loomai_customer_proposals.customer_proposal_codes c on c.proposal_id = p.id
  where p.slug = p_slug and p.status = 'published'
    and c.code_digest = p_code_digest and c.proposal_version = p.version
    and c.revoked_at is null and c.expires_at > now();
  if v_proposal is null then return jsonb_build_object('error', 'ACCESS_DENIED'); end if;

  insert into loomai_customer_proposals.customer_proposal_sessions
    (token_digest, proposal_id, code_id, proposal_version, expires_at)
  values (p_token_digest, v_proposal, v_code, v_version, v_expiry);
  return jsonb_build_object('maxAge', greatest(1, floor(extract(epoch from (v_expiry - now())))));
end;
$$;

create or replace function public.read_customer_proposal(p_slug text, p_token_digest text, p_download boolean default false)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_result jsonb;
begin
  select case when p_download then
      case when p.docx_base64 is null then jsonb_build_object('error','NOT_FOUND')
      else jsonb_build_object('base64',p.docx_base64) end
    else p.content || jsonb_build_object('version',p.version,'downloadAvailable',p.docx_base64 is not null) end
  into v_result
  from loomai_customer_proposals.customer_proposal_sessions s
  join loomai_customer_proposals.customer_proposal_codes c on c.id = s.code_id
  join loomai_customer_proposals.customer_proposals p on p.id = s.proposal_id
  where s.token_digest = p_token_digest and p.slug = p_slug
    and s.proposal_id = c.proposal_id and p.status = 'published'
    and s.proposal_version = p.version and c.proposal_version = p.version
    and s.expires_at > now() and c.expires_at > now() and c.revoked_at is null;
  return coalesce(v_result, jsonb_build_object('error','ACCESS_DENIED'));
end;
$$;

create or replace function public.close_customer_proposal_session(p_slug text, p_token_digest text)
returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  delete from loomai_customer_proposals.customer_proposal_sessions s
  using loomai_customer_proposals.customer_proposals p
  where s.token_digest = p_token_digest and p.id = s.proposal_id and p.slug = p_slug;
  return jsonb_build_object('closed',true);
end;
$$;

revoke all on function public.exchange_customer_proposal_code(text,text,text,text) from public, anon, authenticated;
revoke all on function public.read_customer_proposal(text,text,boolean) from public, anon, authenticated;
revoke all on function public.close_customer_proposal_session(text,text) from public, anon, authenticated;
grant execute on function public.exchange_customer_proposal_code(text,text,text,text) to service_role;
grant execute on function public.read_customer_proposal(text,text,boolean) to service_role;
grant execute on function public.close_customer_proposal_session(text,text) to service_role;

comment on table loomai_customer_proposals.customer_proposal_codes is
'Customer proposal codes. Digests only; invitations are bearer access, not verified recipient identity.';
notify pgrst, 'reload schema';
commit;
