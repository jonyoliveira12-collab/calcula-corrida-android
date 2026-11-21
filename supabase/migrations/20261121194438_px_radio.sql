begin;
create schema if not exists jd_px;
revoke all on schema jd_px from public,anon,authenticated;
create table jd_px.channels(id uuid primary key default gen_random_uuid(),kind text not null check(kind in('private','group')),pair text unique,owner uuid not null references auth.users(id) on delete cascade,name text not null check(length(name) between 1 and 40),closed boolean not null default false,dirty boolean not null default true,created_at timestamptz not null default now());
create table jd_px.members(channel uuid not null references jd_px.channels(id) on delete cascade,user_id uuid not null references auth.users(id) on delete cascade,accepted boolean not null default false,primary key(channel,user_id));
create index on jd_px.members(user_id,channel);
create table jd_px.connections(id uuid primary key,user_id uuid not null references auth.users(id) on delete cascade,auth_session uuid not null,channel uuid not null references jd_px.channels(id) on delete cascade,expires timestamptz not null,created_at timestamptz not null default now());
create index on jd_px.connections(channel,expires);
create index on jd_px.connections(user_id);
create table jd_px.floor(channel uuid primary key references jd_px.channels(id) on delete cascade,connection uuid not null references jd_px.connections(id) on delete cascade,expires timestamptz not null,started timestamptz not null default now());
create table jd_px.mutex(channel uuid primary key references jd_px.channels(id) on delete cascade,token uuid not null,expires timestamptz not null);
alter table jd_px.channels enable row level security;
alter table jd_px.members enable row level security;
alter table jd_px.connections enable row level security;
alter table jd_px.floor enable row level security;
alter table jd_px.mutex enable row level security;
revoke all on all tables in schema jd_px from public,anon,authenticated;
create function jd_px.allowed(c uuid,u uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from jd_px.channels ch join jd_px.members m on m.channel=ch.id where ch.id=c and not ch.closed and m.user_id=u and m.accepted
 and (ch.owner=u or jd_social.friends(ch.owner,u))
 and not exists(select 1 from jd_px.members other join jd_social.links l on l.state='blocked' and ((l.requester=u and l.recipient=other.user_id)or(l.recipient=u and l.requester=other.user_id)) where other.channel=c and other.accepted));
$$;
revoke all on function jd_px.allowed(uuid,uuid) from public,anon,authenticated;
create function public.jd_px(p_action text,p_user uuid default null,p_session uuid default null,p_data jsonb default '{}'::jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare c uuid; target uuid; conn uuid; lock_id uuid; v text; pair_key text; v_channel jd_px.channels%rowtype; v_connection jd_px.connections%rowtype; answer jsonb; v_floor jd_px.floor%rowtype; now_at timestamptz:=clock_timestamp();
begin
 if auth.role()<>'service_role' then raise exception 'Acesso indisponível' using errcode='42501';end if;
 if p_action='hook_check' then return jsonb_build_object('ok',exists(select 1 from vault.decrypted_secrets where name='jd_px_hook' and decrypted_secret=p_data->>'nonce'));end if;
 if p_action='rooms' then return jsonb_build_object('rooms',coalesce((select jsonb_agg(id) from jd_px.channels where dirty),'[]'::jsonb));end if;
 c:=nullif(p_data->>'channel','')::uuid;
 if p_action='lock' then
  lock_id:=(p_data->>'lock')::uuid;
  insert into jd_px.mutex(channel,token,expires) values(c,lock_id,now_at+interval '20 seconds') on conflict(channel)do update set token=excluded.token,expires=excluded.expires where jd_px.mutex.expires<now_at;
  return jsonb_build_object('ok',found);
 elsif p_action='unlock' then delete from jd_px.mutex where channel=c and token=(p_data->>'lock')::uuid;return jsonb_build_object('ok',true);
 elsif p_action='inspect' then
  return jsonb_build_object('ok',true,'channel',c,'connections',coalesce((select jsonb_agg(jsonb_build_object('identity',x.id,'name',coalesce(p.name,'Motorista'))) from jd_px.connections x left join jd_social.people p on p.user_id=x.user_id where x.channel=c and x.expires>now_at and jd_px.allowed(c,x.user_id) and exists(select 1 from auth.sessions s where s.id=x.auth_session and s.user_id=x.user_id and(s.not_after is null or s.not_after>now_at))),'[]'::jsonb),'speaker',(select connection from jd_px.floor f where f.channel=c and f.expires>now_at and f.started>now_at-interval '30 seconds' and exists(select 1 from jd_px.connections x where x.id=f.connection and x.expires>now_at and jd_px.allowed(c,x.user_id))));
 elsif p_action='settled' then update jd_px.channels set dirty=false where id=c;return jsonb_build_object('ok',true);
 end if;
 if p_user is null or not exists(select 1 from auth.sessions s where s.id=p_session and s.user_id=p_user and(s.not_after is null or s.not_after>now_at)) then raise exception 'Entre novamente para usar o PX.' using errcode='42501';end if;
 perform pg_advisory_xact_lock(hashtextextended(p_user::text,17));
 if p_action='list' then
  return jsonb_build_object('ok',true,'channels',coalesce((select jsonb_agg(jsonb_build_object('id',ch.id,'kind',ch.kind,'name',case when ch.kind='private' then(select p.name from jd_px.members m join jd_social.people p on p.user_id=m.user_id where m.channel=ch.id and m.user_id<>p_user limit 1)else ch.name end,'owner',ch.owner=p_user,'accepted',m.accepted,'members',(select jsonb_agg(jsonb_build_object('id',mm.user_id,'name',coalesce(p.name,'Motorista'),'accepted',mm.accepted)) from jd_px.members mm left join jd_social.people p on p.user_id=mm.user_id where mm.channel=ch.id))) from jd_px.channels ch join jd_px.members m on m.channel=ch.id where m.user_id=p_user and not ch.closed and(ch.owner=p_user or jd_social.friends(ch.owner,p_user))),'[]'::jsonb));
 elsif p_action='private' then
  target:=(p_data->>'peer')::uuid;
  if not jd_social.friends(p_user,target) then raise exception 'Converse somente com amigos aceitos.' using errcode='42501';end if;
  pair_key:=least(p_user::text,target::text)||':'||greatest(p_user::text,target::text);
  insert into jd_px.channels(kind,pair,owner,name)values('private',pair_key,least(p_user::text,target::text)::uuid,'Conversa privada') on conflict(pair)do update set closed=false returning id into c;
  insert into jd_px.members(channel,user_id,accepted)values(c,p_user,true),(c,target,true)on conflict(channel,user_id)do update set accepted=true;
  return jsonb_build_object('ok',true,'channel',c);
 elsif p_action='group' then
  v:=btrim(regexp_replace(p_data->>'name','[[:cntrl:]]','','g'));
  if coalesce(length(v),0)not between 1 and 40 then raise exception 'Use um nome de 1 a 40 caracteres.';end if;
  if(select count(*)from jd_px.channels where owner=p_user and kind='group' and not closed)>=20 then raise exception 'Limite de grupos atingido.';end if;
  insert into jd_px.channels(kind,owner,name)values('group',p_user,v)returning id into c;
  insert into jd_px.members(channel,user_id,accepted)values(c,p_user,true);
  return jsonb_build_object('ok',true,'channel',c);
 end if;
 select * into v_channel from jd_px.channels where id=c and not closed;
 if not found then raise exception 'Canal indisponível.' using errcode='42501';end if;
 if p_action='accept' then
  if not jd_social.friends(v_channel.owner,p_user) then raise exception 'Convite indisponível.' using errcode='42501';end if;
  update jd_px.members set accepted=true where channel=c and user_id=p_user;
  if not found then raise exception 'Convite indisponível.' using errcode='42501';end if;
  return jsonb_build_object('ok',true);
 elsif p_action in('invite','remove','exit','close') then
  if p_action='exit' then target:=p_user;
  else
   if v_channel.owner<>p_user then raise exception 'Somente o dono pode gerenciar o grupo.' using errcode='42501';end if;
   target:=nullif(p_data->>'peer','')::uuid;
  end if;
  if p_action='invite' then
   if v_channel.kind<>'group' or not jd_social.friends(p_user,target) then raise exception 'Convide um amigo aceito.' using errcode='42501';end if;
   if(select count(*)from jd_px.members where channel=c)>=12 then raise exception 'Grupo limitado a 12 participantes.';end if;
   insert into jd_px.members(channel,user_id)values(c,target)on conflict do nothing;
  elsif p_action='close' or target=v_channel.owner then update jd_px.channels set closed=true where id=c;
  else delete from jd_px.members where channel=c and user_id=target;
  end if;
  update jd_px.channels set dirty=true where id=c;return jsonb_build_object('ok',true,'channel',c);
 end if;
 if not jd_px.allowed(c,p_user) then raise exception 'Acesso ao canal encerrado.' using errcode='42501';end if;
 if p_action='connect' then
  conn:=(p_data->>'connection')::uuid;
  if exists(select 1 from jd_px.connections where id=conn)then raise exception 'Conexão já utilizada.';end if;
  update jd_px.connections set expires=now_at where user_id=p_user;
  insert into jd_px.connections(id,user_id,auth_session,channel,expires)values(conn,p_user,p_session,c,now_at+interval '20 seconds');
  update jd_px.channels set dirty=true where id=c or id in(select channel from jd_px.connections where user_id=p_user);
 elsif p_action in('tick','claim','release','leave') then
  conn:=(p_data->>'connection')::uuid;
  select * into v_connection from jd_px.connections where id=conn and user_id=p_user and auth_session=p_session and channel=c and expires>now_at for update;
  if not found then raise exception 'Entre novamente no canal.' using errcode='42501';end if;
  update jd_px.connections set expires=case when p_action='leave' then now_at else now_at+interval '20 seconds'end where id=conn;
  if p_action='claim' then
   insert into jd_px.floor(channel,connection,expires,started)values(c,conn,now_at+interval '8 seconds',now_at)
   on conflict(channel)do update set connection=excluded.connection,expires=excluded.expires,started=case when jd_px.floor.connection=excluded.connection then jd_px.floor.started else excluded.started end
   where jd_px.floor.expires<now_at or(jd_px.floor.connection=excluded.connection and jd_px.floor.started>now_at-interval '30 seconds');
  elsif p_action='tick' and coalesce((p_data->>'talking')::boolean,false)then
   update jd_px.floor set expires=least(now_at+interval '8 seconds',started+interval '30 seconds')where channel=c and connection=conn and expires>now_at and started>now_at-interval '30 seconds';
  else delete from jd_px.floor where channel=c and connection=conn;
  end if;
 else raise exception 'Ação inválida.';end if;
 select * into v_floor from jd_px.floor where channel=c and expires>now_at;
 return jsonb_build_object('ok',true,'channel',c,'connection',conn,'name',case when v_channel.kind='private' then(select coalesce(p.name,'Motorista')from jd_px.members m left join jd_social.people p on p.user_id=m.user_id where m.channel=c and m.user_id<>p_user limit 1)else v_channel.name end,'display_name',(select coalesce(name,'Motorista')from jd_social.people where user_id=p_user),'can_talk',v_floor.connection=conn,'lease_ms',greatest(0,floor(extract(epoch from (v_floor.expires-now_at))*1000)),'speaker',(select coalesce(p.name,'Motorista')from jd_px.connections x left join jd_social.people p on p.user_id=x.user_id where x.id=v_floor.connection));
end;
$$;
revoke all on function public.jd_px(text,uuid,uuid,jsonb)from public,anon,authenticated;
grant execute on function public.jd_px(text,uuid,uuid,jsonb)to service_role;
commit;

create extension if not exists pg_net with schema extensions;
create extension if not exists pg_cron with schema pg_catalog;
begin;
do $$ begin if not exists(select 1 from vault.secrets where name='jd_px_hook')then perform vault.create_secret(encode(extensions.gen_random_bytes(32),'hex'),'jd_px_hook','Internal PX revocation hook, never sent to clients');end if;end $$;
create function jd_px.enqueue_reconcile() returns void language plpgsql security definer set search_path='' as $$
declare secret text;
begin
 if not exists(select 1 from jd_px.channels where dirty)then return;end if;
 select decrypted_secret into secret from vault.decrypted_secrets where name='jd_px_hook';
 if secret is null then return;end if;
 perform net.http_post(url:='https://hyoaomusfpeccdchgoik.supabase.co/functions/v1/jd-px',headers:=jsonb_build_object('Content-Type','application/json','X-PX-Hook',secret),body:='{}'::jsonb,timeout_milliseconds:=15000);
end;$$;
revoke all on function jd_px.enqueue_reconcile()from public,anon,authenticated;
create function jd_px.friend_changed()returns trigger language plpgsql security definer set search_path='' as $$
begin
 update jd_px.channels set dirty=true where id in(select channel from jd_px.members where user_id in(old.requester,old.recipient));
 perform jd_px.enqueue_reconcile();return null;
end;$$;
revoke all on function jd_px.friend_changed()from public,anon,authenticated;
create trigger jd_px_friend_revocation after update or delete on jd_social.links for each row execute function jd_px.friend_changed();
select cron.schedule('jd-px-reconcile','10 seconds','select jd_px.enqueue_reconcile()');
commit;
begin;
alter table jd_px.channels add column room_key uuid not null default gen_random_uuid();
create table jd_px.retired_rooms(room_key uuid primary key,channel uuid not null references jd_px.channels(id) on delete cascade);
alter table jd_px.retired_rooms enable row level security;
revoke all on jd_px.retired_rooms from public,anon,authenticated;
create function jd_px.rotate_room(c uuid)returns void language plpgsql security definer set search_path='' as $$
begin
 insert into jd_px.retired_rooms(room_key,channel)select room_key,id from jd_px.channels where id=c on conflict do nothing;
 update jd_px.channels set room_key=gen_random_uuid(),dirty=true where id=c;
 update jd_px.connections set expires=clock_timestamp()where channel=c;
 delete from jd_px.floor where channel=c;
end;$$;
revoke all on function jd_px.rotate_room(uuid)from public,anon,authenticated;
create or replace function jd_px.friend_changed()returns trigger language plpgsql security definer set search_path='' as $$
declare c uuid;
begin
 if tg_op='UPDATE' and old.state is not distinct from new.state then return null;end if;
 for c in select distinct channel from jd_px.members where user_id in(old.requester,old.recipient)loop perform jd_px.rotate_room(c);end loop;
 perform jd_px.enqueue_reconcile();return null;
end;$$;
create function jd_px.member_changed()returns trigger language plpgsql security definer set search_path='' as $$
begin
 if old.accepted then perform jd_px.rotate_room(old.channel);perform jd_px.enqueue_reconcile();end if;return null;
end;$$;
revoke all on function jd_px.member_changed()from public,anon,authenticated;
create trigger jd_px_member_revocation after delete on jd_px.members for each row execute function jd_px.member_changed();
commit;

create or replace function public.jd_px(p_action text,p_user uuid default null,p_session uuid default null,p_data jsonb default '{}'::jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare c uuid; target uuid; conn uuid; lock_id uuid; v text; pair_key text; v_channel jd_px.channels%rowtype; v_connection jd_px.connections%rowtype; answer jsonb; v_floor jd_px.floor%rowtype; now_at timestamptz:=clock_timestamp();
begin
 if auth.role()<>'service_role' then raise exception 'Acesso indisponível' using errcode='42501';end if;
 if p_action='hook_check' then return jsonb_build_object('ok',exists(select 1 from vault.decrypted_secrets where name='jd_px_hook' and decrypted_secret=p_data->>'nonce'));end if;
 if p_action='rooms' then return jsonb_build_object('rooms',coalesce((select jsonb_agg(id) from jd_px.channels where dirty),'[]'::jsonb));end if;
 c:=nullif(p_data->>'channel','')::uuid;
 if p_action='lock' then
  lock_id:=(p_data->>'lock')::uuid;
  insert into jd_px.mutex(channel,token,expires) values(c,lock_id,now_at+interval '20 seconds') on conflict(channel)do update set token=excluded.token,expires=excluded.expires where jd_px.mutex.expires<now_at;
  return jsonb_build_object('ok',found);
 elsif p_action='unlock' then delete from jd_px.mutex where channel=c and token=(p_data->>'lock')::uuid;return jsonb_build_object('ok',true);
 elsif p_action='inspect' then
  return jsonb_build_object('ok',true,'channel',c,'room_key',(select room_key from jd_px.channels where id=c),'retired',coalesce((select jsonb_agg(room_key)from jd_px.retired_rooms where channel=c),'[]'::jsonb),'connections',coalesce((select jsonb_agg(jsonb_build_object('identity',x.id,'name',coalesce(p.name,'Motorista'))) from jd_px.connections x left join jd_social.people p on p.user_id=x.user_id where x.channel=c and x.expires>now_at and jd_px.allowed(c,x.user_id) and exists(select 1 from auth.sessions s where s.id=x.auth_session and s.user_id=x.user_id and(s.not_after is null or s.not_after>now_at))),'[]'::jsonb),'speaker',(select connection from jd_px.floor f where f.channel=c and f.expires>now_at and f.started>now_at-interval '30 seconds' and exists(select 1 from jd_px.connections x where x.id=f.connection and x.expires>now_at and jd_px.allowed(c,x.user_id))));
 elsif p_action='retired_done' then delete from jd_px.retired_rooms where channel=c and room_key=(p_data->>'room_key')::uuid;return jsonb_build_object('ok',true);
 elsif p_action='settled' then update jd_px.channels set dirty=false where id=c;return jsonb_build_object('ok',true);
 end if;
 if p_user is null or not exists(select 1 from auth.sessions s where s.id=p_session and s.user_id=p_user and(s.not_after is null or s.not_after>now_at)) then raise exception 'Entre novamente para usar o PX.' using errcode='42501';end if;
 perform pg_advisory_xact_lock(hashtextextended(p_user::text,17));
 if p_action='list' then
  return jsonb_build_object('ok',true,'channels',coalesce((select jsonb_agg(jsonb_build_object('id',ch.id,'kind',ch.kind,'name',case when ch.kind='private' then(select p.name from jd_px.members m join jd_social.people p on p.user_id=m.user_id where m.channel=ch.id and m.user_id<>p_user limit 1)else ch.name end,'owner',ch.owner=p_user,'accepted',m.accepted,'members',(select jsonb_agg(jsonb_build_object('id',mm.user_id,'name',coalesce(p.name,'Motorista'),'accepted',mm.accepted)) from jd_px.members mm left join jd_social.people p on p.user_id=mm.user_id where mm.channel=ch.id))) from jd_px.channels ch join jd_px.members m on m.channel=ch.id where m.user_id=p_user and not ch.closed and(ch.owner=p_user or jd_social.friends(ch.owner,p_user))),'[]'::jsonb));
 elsif p_action='private' then
  target:=(p_data->>'peer')::uuid;
  if not jd_social.friends(p_user,target) then raise exception 'Converse somente com amigos aceitos.' using errcode='42501';end if;
  pair_key:=least(p_user::text,target::text)||':'||greatest(p_user::text,target::text);
  insert into jd_px.channels(kind,pair,owner,name)values('private',pair_key,least(p_user::text,target::text)::uuid,'Conversa privada') on conflict(pair)do update set closed=false returning id into c;
  insert into jd_px.members(channel,user_id,accepted)values(c,p_user,true),(c,target,true)on conflict(channel,user_id)do update set accepted=true;
  return jsonb_build_object('ok',true,'channel',c);
 elsif p_action='group' then
  v:=btrim(regexp_replace(p_data->>'name','[[:cntrl:]]','','g'));
  if coalesce(length(v),0)not between 1 and 40 then raise exception 'Use um nome de 1 a 40 caracteres.';end if;
  if(select count(*)from jd_px.channels where owner=p_user and kind='group' and not closed)>=20 then raise exception 'Limite de grupos atingido.';end if;
  insert into jd_px.channels(kind,owner,name)values('group',p_user,v)returning id into c;
  insert into jd_px.members(channel,user_id,accepted)values(c,p_user,true);
  return jsonb_build_object('ok',true,'channel',c);
 end if;
 select * into v_channel from jd_px.channels where id=c and not closed;
 if not found then raise exception 'Canal indisponível.' using errcode='42501';end if;
 if p_action='accept' then
  if not jd_social.friends(v_channel.owner,p_user) then raise exception 'Convite indisponível.' using errcode='42501';end if;
  update jd_px.members set accepted=true where channel=c and user_id=p_user;
  if not found then raise exception 'Convite indisponível.' using errcode='42501';end if;
  return jsonb_build_object('ok',true);
 elsif p_action in('invite','remove','exit','close') then
  if p_action='exit' then target:=p_user;
  else
   if v_channel.owner<>p_user then raise exception 'Somente o dono pode gerenciar o grupo.' using errcode='42501';end if;
   target:=nullif(p_data->>'peer','')::uuid;
  end if;
  if p_action='invite' then
   if v_channel.kind<>'group' or not jd_social.friends(p_user,target) then raise exception 'Convide um amigo aceito.' using errcode='42501';end if;
   if(select count(*)from jd_px.members where channel=c)>=12 then raise exception 'Grupo limitado a 12 participantes.';end if;
   insert into jd_px.members(channel,user_id)values(c,target)on conflict do nothing;
  elsif p_action='close' or target=v_channel.owner then update jd_px.channels set closed=true where id=c;
  else delete from jd_px.members where channel=c and user_id=target;
  end if;
  update jd_px.channels set dirty=true where id=c;return jsonb_build_object('ok',true,'channel',c);
 end if;
 if not jd_px.allowed(c,p_user) then raise exception 'Acesso ao canal encerrado.' using errcode='42501';end if;
 if p_action='connect' then
  conn:=(p_data->>'connection')::uuid;
  if exists(select 1 from jd_px.connections where id=conn)then raise exception 'Conexão já utilizada.';end if;
  update jd_px.connections set expires=now_at where user_id=p_user;
  insert into jd_px.connections(id,user_id,auth_session,channel,expires)values(conn,p_user,p_session,c,now_at+interval '20 seconds');
  update jd_px.channels set dirty=true where id=c or id in(select channel from jd_px.connections where user_id=p_user);
 elsif p_action in('tick','claim','release','leave') then
  conn:=(p_data->>'connection')::uuid;
  select * into v_connection from jd_px.connections where id=conn and user_id=p_user and auth_session=p_session and channel=c and expires>now_at for update;
  if not found then raise exception 'Entre novamente no canal.' using errcode='42501';end if;
  update jd_px.connections set expires=case when p_action='leave' then now_at else now_at+interval '20 seconds'end where id=conn;
  if p_action='claim' then
   insert into jd_px.floor(channel,connection,expires,started)values(c,conn,now_at+interval '8 seconds',now_at)
   on conflict(channel)do update set connection=excluded.connection,expires=excluded.expires,started=case when jd_px.floor.connection=excluded.connection then jd_px.floor.started else excluded.started end
   where jd_px.floor.expires<now_at or(jd_px.floor.connection=excluded.connection and jd_px.floor.started>now_at-interval '30 seconds');
  elsif p_action='tick' and coalesce((p_data->>'talking')::boolean,false)then
   update jd_px.floor set expires=least(now_at+interval '8 seconds',started+interval '30 seconds')where channel=c and connection=conn and expires>now_at and started>now_at-interval '30 seconds';
  else delete from jd_px.floor where channel=c and connection=conn;
  end if;
 else raise exception 'Ação inválida.';end if;
 select * into v_floor from jd_px.floor where channel=c and expires>now_at;
 return jsonb_build_object('ok',true,'channel',c,'room_key',v_channel.room_key,'connection',conn,'name',case when v_channel.kind='private' then(select coalesce(p.name,'Motorista')from jd_px.members m left join jd_social.people p on p.user_id=m.user_id where m.channel=c and m.user_id<>p_user limit 1)else v_channel.name end,'display_name',(select coalesce(name,'Motorista')from jd_social.people where user_id=p_user),'can_talk',v_floor.connection=conn,'lease_ms',greatest(0,floor(extract(epoch from (v_floor.expires-now_at))*1000)),'speaker',(select coalesce(p.name,'Motorista')from jd_px.connections x left join jd_social.people p on p.user_id=x.user_id where x.id=v_floor.connection));
end;
$$;
