-- One database round trip for the media mutex, validated action and room state.
-- Internal service only; session/membership/floor checks remain in public.jd_px.
create or replace function public.jd_px_step(p_action text,p_user uuid,p_session uuid,p_data jsonb,p_lock uuid)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare response jsonb; acquired boolean;
begin
 if auth.role()<>'service_role' or p_action not in('connect','claim','release','leave') then raise exception 'Acesso indisponível' using errcode='42501';end if;
 acquired:=coalesce((public.jd_px('lock',null,null,jsonb_build_object('channel',p_data->>'channel','lock',p_lock))->>'ok')::boolean,false);
 if not acquired then return jsonb_build_object('_busy',true);end if;
 response:=public.jd_px(p_action,p_user,p_session,p_data);
 return response||jsonb_build_object('_voice',public.jd_px('inspect',null,null,jsonb_build_object('channel',p_data->>'channel')));
end;$$;
revoke all on function public.jd_px_step(text,uuid,uuid,jsonb,uuid) from public,anon,authenticated;
grant execute on function public.jd_px_step(text,uuid,uuid,jsonb,uuid) to service_role;
