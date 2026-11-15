-- Social media is private, immutable, and readable only by the owner or accepted recipients.
alter table jd_social.people add column avatar_path text;
alter table jd_social.presence add column mode text not null default 'online' check(mode in('online','away'));
alter table jd_social.messages add column message_kind text not null default 'text' check(message_kind in('text','audio'));
alter table jd_social.messages add column audio_path text;
alter table jd_social.messages add column audio_duration_ms integer;
alter table jd_social.messages add constraint jd_audio_fields check((message_kind='text' and audio_path is null and audio_duration_ms is null) or(message_kind='audio' and audio_path is not null and audio_duration_ms between 300 and 60000));
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values('jd-friend-media','jd-friend-media',false,2097152,array['image/jpeg','audio/mp4']);

create function jd_social.media_allowed(object_path text,operation text) returns boolean
language plpgsql security definer set search_path='' as $$
declare u uuid:=jd_social.require_user(); owner uuid;
begin
 if object_path !~ '^[a-f0-9-]{36}/(avatars/[a-f0-9-]{36}\.jpg|audio/[a-f0-9-]{36}\.m4a)$' then return false;end if;
 owner:=split_part(object_path,'/',1)::uuid;
 if operation='write' then
  return owner=u and (select count(*) from storage.objects where bucket_id='jd-friend-media' and owner_id=u::text and created_at>now()-interval '1 day')<100;
 elsif operation='delete' then return owner=u;
 elsif operation<>'read' then return false;end if;
 if owner=u then return true;end if;
 if not jd_social.friends(u,owner) then return false;end if;
 return exists(select 1 from jd_social.people where user_id=owner and avatar_path=object_path) or
 exists(select 1 from jd_social.messages where audio_path=object_path and ((sender_id=owner and recipient_id=u)or(sender_id=u and recipient_id=owner)));
end $$;
revoke all on function jd_social.media_allowed(text,text) from public,anon;
grant execute on function jd_social.media_allowed(text,text) to authenticated;
create policy jd_media_insert on storage.objects for insert to authenticated with check(bucket_id='jd-friend-media' and jd_social.media_allowed(name,'write'));
create policy jd_media_read on storage.objects for select to authenticated using(bucket_id='jd-friend-media' and jd_social.media_allowed(name,'read'));
create policy jd_media_delete on storage.objects for delete to authenticated using(bucket_id='jd-friend-media' and jd_social.media_allowed(name,'delete'));
create or replace function jd_social.api(p_action text,p_data jsonb default '{}'::jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare
 u uuid:=jd_social.require_user(); peer uuid; sid uuid; oldsid uuid; mins integer; v text; r jd_social.links%rowtype;
 point jsonb; result jsonb; contacts jsonb; chat jsonb:='[]'::jsonb; shares jsonb; inbox jsonb;
 media text; kind text; duration integer; presence_mode text;
 lat double precision; lng double precision; acc real; age_ms integer; count_now integer;
begin
 if p_data is null or jsonb_typeof(p_data)<>'object' then raise exception 'Dados inválidos.' using errcode='22023';end if;
 insert into jd_social.people(user_id) values(u) on conflict(user_id) do nothing;
 perform 1 from jd_social.people where user_id=u for no key update;
 if p_data ? 'peer' then peer:=(p_data->>'peer')::uuid;end if;
 if p_data ? 'session' then sid:=(p_data->>'session')::uuid;end if;

 if p_action='profile' then
  if p_data ? 'avatar_path' then
   media:=nullif(p_data->>'avatar_path','');
   if media is not null and (media !~ ('^'||u::text||'/avatars/[a-f0-9-]{36}\.jpg$') or not exists(select 1 from storage.objects where bucket_id='jd-friend-media' and name=media and owner_id=u::text)) then raise exception 'Foto indisponível.' using errcode='22023';end if;
   update jd_social.people set avatar_path=media where user_id=u;
  end if;
  if p_data ? 'name' then
   v:=btrim(regexp_replace(p_data->>'name','[[:cntrl:]]',' ','g'));
   if v is null or char_length(v) not between 1 and 40 then raise exception 'Use um nome entre 1 e 40 caracteres.' using errcode='22023';end if;
   update jd_social.people set name=v where user_id=u;
  end if;
 elsif p_action='request' then
  v:=regexp_replace(upper(coalesce(p_data->>'code','')),'[[:space:]-]','','g');
  if v !~ '^[A-F0-9]{12}$' then raise exception 'Código de amigo inválido.' using errcode='22023';end if;
  select user_id into peer from jd_social.people where invite_code=v;
  if peer is null or peer=u then raise exception 'Código indisponível. Confira com seu amigo.' using errcode='22023';end if;
  select * into r from jd_social.links where (requester=u and recipient=peer) or(requester=peer and recipient=u);
  if found then
   if r.state='blocked' then raise exception 'Convite indisponível.' using errcode='42501';end if;
   if r.state='pending' and r.recipient=u then raise exception 'Você já recebeu um convite dessa pessoa. Aceite em Convites.' using errcode='22023';end if;
   return jsonb_build_object('ok',true);
  end if;
  if (select count(*) from jd_social.links where (requester=u or recipient=u))>=200 then raise exception 'Limite de amigos e convites atingido.' using errcode='22023';end if;
  insert into jd_social.request_limits(user_id) values(u) on conflict do nothing;
  update jd_social.request_limits set hits=case when window_start<now()-interval '1 day' then 0 else hits end,
    window_start=case when window_start<now()-interval '1 day' then now() else window_start end where user_id=u;
  select hits into count_now from jd_social.request_limits where user_id=u;
  if count_now>=20 then raise exception 'Aguarde antes de enviar novos convites.' using errcode='P0001';end if;
  update jd_social.request_limits set hits=hits+1 where user_id=u;
  insert into jd_social.links(requester,recipient) values(u,peer) on conflict do nothing;
  return jsonb_build_object('ok',true);
 elsif p_action='respond' then
  if not exists(select 1 from jd_social.links where requester=peer and recipient=u and state='pending') then raise exception 'Convite não encontrado.' using errcode='42501';end if;
  if coalesce((p_data->>'accept')::boolean,false) then update jd_social.links set state='accepted' where requester=peer and recipient=u and state='pending';
  else delete from jd_social.links where requester=peer and recipient=u and state='pending';end if;
  return jsonb_build_object('ok',true);
 elsif p_action in('remove','block','unblock') then
  select * into r from jd_social.links where (requester=u and recipient=peer) or(requester=peer and recipient=u) for update;
  if not found then raise exception 'Amizade não encontrada.' using errcode='42501';end if;
  if r.state='blocked' and r.blocked_by<>u then raise exception 'Ação indisponível.' using errcode='42501';end if;
  if p_action='block' then update jd_social.links set state='blocked',blocked_by=u where requester=r.requester and recipient=r.recipient;
  elsif p_action='unblock' and r.state<>'blocked' then raise exception 'Ação inválida.' using errcode='22023';
  else delete from jd_social.links where requester=r.requester and recipient=r.recipient;end if;
  delete from jd_social.grants where (owner_id=u and viewer_id=peer) or(owner_id=peer and viewer_id=u);
  delete from jd_social.locations where user_id=u and not exists(select 1 from jd_social.grants where owner_id=u and expires_at>now());
  return jsonb_build_object('ok',true);
 elsif p_action='online' then
  presence_mode:=coalesce(p_data->>'mode','online');
  if presence_mode not in('online','away') then raise exception 'Status inválido.' using errcode='22023';end if;
  if sid is null then raise exception 'Sessão inválida.' using errcode='22023';end if;
  select session_id into oldsid from jd_social.presence where user_id=u for update;
  if oldsid is distinct from sid then
   delete from jd_social.grants where owner_id=u;
   delete from jd_social.locations where user_id=u;
  end if;
  insert into jd_social.presence(user_id,session_id,auth_session_id,online,last_seen,mode)
   values(u,sid,(auth.jwt()->>'session_id')::uuid,true,now(),presence_mode)
   on conflict(user_id) do update set session_id=excluded.session_id,auth_session_id=excluded.auth_session_id,online=true,last_seen=now(),mode=excluded.mode;
 elsif p_action='offline' then
  update jd_social.presence set online=false,last_seen=now() where user_id=u and session_id=sid;
  if found then delete from jd_social.grants where owner_id=u;delete from jd_social.locations where user_id=u;end if;
  return jsonb_build_object('ok',true);
 elsif p_action='heartbeat' then
  presence_mode:=p_data->>'mode';
  if presence_mode is not null and presence_mode not in('online','away') then raise exception 'Status inválido.' using errcode='22023';end if;
  update jd_social.presence set last_seen=now(),mode=coalesce(presence_mode,mode) where user_id=u and session_id=sid and online;
  if not found then return jsonb_build_object('ok',false,'session_ended',true);end if;
 elsif p_action='share' then
  perform 1 from jd_social.links where (requester=u and recipient=peer) or(requester=peer and recipient=u) for update;
  if not jd_social.friends(u,peer) then raise exception 'Aceite a amizade antes de compartilhar.' using errcode='42501';end if;
  if coalesce((p_data->>'enabled')::boolean,false) then
   if not exists(select 1 from jd_social.presence where user_id=u and session_id=sid and online and last_seen>now()-interval '75 seconds') then raise exception 'Fique online antes de compartilhar.' using errcode='22023';end if;
   mins:=coalesce((p_data->>'minutes')::integer,60);
   if mins not in(15,60,240) then raise exception 'Duração inválida.' using errcode='22023';end if;
   insert into jd_social.grants(owner_id,viewer_id,expires_at) values(u,peer,now()+make_interval(mins=>mins))
    on conflict(owner_id,viewer_id) do update set expires_at=excluded.expires_at;
  else
   delete from jd_social.grants where owner_id=u and viewer_id=peer;
   delete from jd_social.locations where user_id=u and not exists(select 1 from jd_social.grants where owner_id=u and expires_at>now());
  end if;
 elsif p_action='location' then
  if not exists(select 1 from jd_social.presence where user_id=u and session_id=sid and online and last_seen>now()-interval '75 seconds') then raise exception 'Sessão de localização encerrada.' using errcode='42501';end if;
  if not exists(select 1 from jd_social.grants where owner_id=u and expires_at>now() and jd_social.friends(u,viewer_id)) then return jsonb_build_object('ok',false,'no_shares',true);end if;
  lat:=(p_data->>'lat')::double precision;lng:=(p_data->>'lng')::double precision;acc:=(p_data->>'accuracy')::real;age_ms:=(p_data->>'age_ms')::integer;
  if lat is null or lng is null or acc is null or age_ms is null or lat not between -90 and 90 or lng not between -180 and 180 or acc not between 0 and 100000 or age_ms not between 0 and 45000 then raise exception 'Localização inválida ou antiga.' using errcode='22023';end if;
  insert into jd_social.locations(user_id,session_id,lat,lng,accuracy,measured_at) values(u,sid,lat,lng,acc,now()-age_ms*interval '1 millisecond')
   on conflict(user_id) do update set session_id=excluded.session_id,lat=excluded.lat,lng=excluded.lng,accuracy=excluded.accuracy,measured_at=excluded.measured_at;
  return jsonb_build_object('ok',true);
 elsif p_action in('send','messages','read') then
  perform 1 from jd_social.links where (requester=u and recipient=peer) or(requester=peer and recipient=u) for update;
  if not jd_social.friends(u,peer) then raise exception 'Conversa disponível somente entre amigos aceitos.' using errcode='42501';end if;
  if p_action='send' then
   kind:=coalesce(p_data->>'kind','text');media:=null;duration:=null;
   if kind not in('text','audio') then raise exception 'Mensagem inválida.' using errcode='22023';end if;
   if kind='audio' then
    media:=p_data->>'audio_path';duration:=(p_data->>'duration_ms')::integer;
    if media is null or media !~ ('^'||u::text||'/audio/[a-f0-9-]{36}\.m4a$') or duration is null or duration not between 300 and 60000 or not exists(select 1 from storage.objects where bucket_id='jd-friend-media' and name=media and owner_id=u::text) then raise exception 'Áudio inválido ou indisponível.' using errcode='22023';end if;
    v:='Mensagem de áudio';
   else v:=btrim(p_data->>'body');end if;
   if v is null or char_length(v) not between 1 and 1000 or nullif(p_data->>'nonce','') is null then raise exception 'Mensagem inválida.' using errcode='22023';end if;
   if exists(select 1 from jd_social.messages where sender_id=u and nonce=(p_data->>'nonce')::uuid and (recipient_id<>peer or body<>v or message_kind<>kind or audio_path is distinct from media or audio_duration_ms is distinct from duration)) then raise exception 'Identificador de mensagem já usado.' using errcode='22023';end if;
   if (select count(*) from jd_social.messages where sender_id=u and created_at>now()-interval '1 minute')>=30 then raise exception 'Aguarde antes de enviar mais mensagens.' using errcode='P0001';end if;
   insert into jd_social.messages(sender_id,recipient_id,nonce,body,message_kind,audio_path,audio_duration_ms) values(u,peer,(p_data->>'nonce')::uuid,v,kind,media,duration) on conflict(sender_id,nonce) do nothing;
  elsif p_action='read' then update jd_social.messages set read_at=now() where recipient_id=u and sender_id=peer and read_at is null;end if;
  select coalesce(jsonb_agg(x.item order by x.created_at,x.id),'[]'::jsonb) into chat from(
   select id,created_at,jsonb_build_object('id',id,'sender',sender_id,'body',body,'kind',message_kind,'audio_path',audio_path,'duration_ms',audio_duration_ms,'time_ms',floor(extract(epoch from created_at)*1000)::bigint) item
   from jd_social.messages where (sender_id=u and recipient_id=peer) or(sender_id=peer and recipient_id=u) order by created_at desc,id desc limit 100
  ) x;
  return jsonb_build_object('ok',true,'messages',chat);
 elsif p_action<>'snapshot' then raise exception 'Ação inválida.' using errcode='22023';
 end if;

 select coalesce(jsonb_agg(jsonb_build_object('peer',viewer_id,'until_ms',floor(extract(epoch from expires_at)*1000)::bigint)),'[]'::jsonb)
  into shares from jd_social.grants where owner_id=u and expires_at>now() and jd_social.friends(u,viewer_id);
 select jsonb_build_object('id',m.id,'sender',m.sender_id,'name',p.name,'body',m.body) into inbox
  from jd_social.messages m join jd_social.people p on p.user_id=m.sender_id
  where m.recipient_id=u and m.read_at is null and jd_social.friends(u,m.sender_id) order by m.created_at desc,m.id desc limit 1;
 if p_action in('heartbeat','online','share') then
  return jsonb_build_object('ok',true,'now_ms',floor(extract(epoch from now())*1000)::bigint,'shares',shares,'inbox',inbox);
 end if;
 select coalesce(jsonb_agg(x.item order by x.sort_name),'[]'::jsonb) into contacts from(
  select p.name sort_name,jsonb_build_object('id',p.user_id,'name',p.name,'state',l.state,'avatar_path',case when l.state='accepted' then p.avatar_path else null end,'status',case when pr.online and pr.last_seen>now()-interval '75 seconds' and exists(select 1 from auth.sessions where id=pr.auth_session_id and user_id=p.user_id and(not_after is null or not_after>now())) then pr.mode else 'offline' end,
   'incoming',l.recipient=u,'blocked_by_me',l.blocked_by=u,
   'online',l.state='accepted' and coalesce(pr.online and pr.last_seen>now()-interval '75 seconds' and exists(select 1 from auth.sessions where id=pr.auth_session_id and user_id=p.user_id and(not_after is null or not_after>now())),false),
   'seen_ms',case when l.state='accepted' then floor(extract(epoch from pr.last_seen)*1000)::bigint else null end,
   'i_share_until',floor(extract(epoch from mine.expires_at)*1000)::bigint,
   'shares_until',floor(extract(epoch from theirs.expires_at)*1000)::bigint,
   'unread',(select count(*) from jd_social.messages where sender_id=p.user_id and recipient_id=u and read_at is null),
   'location',case when l.state='accepted' and theirs.expires_at>now() and pr.online and pr.last_seen>now()-interval '75 seconds'
     and loc.session_id=pr.session_id and loc.measured_at>now()-interval '120 seconds'
     and exists(select 1 from auth.sessions where id=pr.auth_session_id and user_id=p.user_id and(not_after is null or not_after>now()))
    then jsonb_build_object('lat',loc.lat,'lng',loc.lng,'accuracy',loc.accuracy,'time_ms',floor(extract(epoch from loc.measured_at)*1000)::bigint) else null end) item
  from jd_social.links l join jd_social.people p on p.user_id=case when l.requester=u then l.recipient else l.requester end
  left join jd_social.presence pr on pr.user_id=p.user_id
  left join jd_social.locations loc on loc.user_id=p.user_id
  left join jd_social.grants mine on mine.owner_id=u and mine.viewer_id=p.user_id and mine.expires_at>now()
  left join jd_social.grants theirs on theirs.owner_id=p.user_id and theirs.viewer_id=u and theirs.expires_at>now()
  where (l.requester=u or l.recipient=u) and(l.state<>'blocked' or l.blocked_by=u)
 ) x;
 select jsonb_build_object('id',user_id,'name',name,'code',invite_code,'avatar_path',avatar_path) into result from jd_social.people where user_id=u;
 return jsonb_build_object('ok',true,'me',result,'friends',contacts,'now_ms',floor(extract(epoch from now())*1000)::bigint,'shares',shares,'inbox',inbox);
end $$;
notify pgrst,'reload schema';
