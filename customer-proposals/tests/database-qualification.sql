begin;
do $$
declare
  p uuid; c uuid; r jsonb; i integer;
begin
  insert into loomai_customer_proposals.customer_proposals(slug,recipient_label,status,content,docx_base64)
  values('qualification-rollback','Fictional test','published','{"title":"Protected fixture","sections":[]}', 'UEs=') returning id into p;
  insert into loomai_customer_proposals.customer_proposal_codes(proposal_id,proposal_version,code_digest,expires_at)
  values(p,1,repeat('a',64),now()+interval '1 day') returning id into c;
  r := public.exchange_customer_proposal_code('qualification-rollback',repeat('a',64),repeat('b',64),repeat('c',64));
  if not (r ? 'maxAge') then raise exception 'VALID_CODE_FAILED'; end if;
  r := public.read_customer_proposal('qualification-rollback',repeat('b',64),false);
  if r->>'title' <> 'Protected fixture' then raise exception 'CONTENT_FAILED'; end if;
  r := public.read_customer_proposal('wrong-scope',repeat('b',64),false);
  if r->>'error' <> 'ACCESS_DENIED' then raise exception 'SCOPE_FAILED'; end if;
  update loomai_customer_proposals.customer_proposal_codes set revoked_at=now() where id=c;
  r := public.read_customer_proposal('qualification-rollback',repeat('b',64),true);
  if r->>'error' <> 'ACCESS_DENIED' then raise exception 'REVOKED_DOWNLOAD_FAILED'; end if;
  update loomai_customer_proposals.customer_proposal_codes set revoked_at=null where id=c;
  update loomai_customer_proposals.customer_proposal_sessions set expires_at=now()-interval '1 second' where proposal_id=p;
  r := public.read_customer_proposal('qualification-rollback',repeat('b',64),false);
  if r->>'error' <> 'ACCESS_DENIED' then raise exception 'SESSION_EXPIRY_FAILED'; end if;
  update loomai_customer_proposals.customer_proposal_sessions set expires_at=now()+interval '1 hour' where proposal_id=p;
  update loomai_customer_proposals.customer_proposals set status='withdrawn' where id=p;
  r := public.read_customer_proposal('qualification-rollback',repeat('b',64),false);
  if r->>'error' <> 'ACCESS_DENIED' then raise exception 'WITHDRAWAL_FAILED'; end if;
  update loomai_customer_proposals.customer_proposals set status='published',version=2 where id=p;
  r := public.read_customer_proposal('qualification-rollback',repeat('b',64),false);
  if r->>'error' <> 'ACCESS_DENIED' then raise exception 'VERSION_FAILED'; end if;
  update loomai_customer_proposals.customer_proposals set version=1 where id=p;
  update loomai_customer_proposals.customer_proposal_codes set created_at=now()-interval '2 days',expires_at=now()-interval '1 day' where id=c;
  r := public.exchange_customer_proposal_code('qualification-rollback',repeat('a',64),repeat('d',64),repeat('e',64));
  if r->>'error' <> 'ACCESS_DENIED' then raise exception 'CODE_EXPIRY_FAILED'; end if;
  for i in 1..21 loop
    r := public.exchange_customer_proposal_code('qualification-rollback',repeat('f',64),repeat('1',64),repeat('9',64));
  end loop;
  if r->>'error' <> 'RATE_LIMITED' then raise exception 'RATE_LIMIT_FAILED'; end if;
end;
$$;
rollback;
