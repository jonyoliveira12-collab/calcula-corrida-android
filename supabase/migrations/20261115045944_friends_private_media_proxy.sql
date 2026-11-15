-- File bytes are served only after a fresh authorization check, not a cached Storage GET.
drop policy jd_media_read on storage.objects;
create policy jd_media_read_owner on storage.objects for select to authenticated using(bucket_id='jd-friend-media' and owner_id=(select auth.uid())::text and jd_social.media_allowed(name,'read'));
create function public.jd_friend_media_access(object_path text) returns boolean
language sql security invoker set search_path='' as $$select jd_social.media_allowed(object_path,'read')$$;
revoke all on function public.jd_friend_media_access(text) from public,anon;
grant execute on function public.jd_friend_media_access(text) to authenticated;
notify pgrst,'reload schema';
