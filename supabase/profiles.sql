-- JHONDRIVER - perfil básico associado ao usuário autenticado pelo Supabase Auth.
-- Execute no SQL Editor do seu projeto Supabase.
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text,
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

create policy "Usuário lê o próprio perfil"
on public.profiles for select
to authenticated
using (auth.uid() = id);

create policy "Usuário cria o próprio perfil"
on public.profiles for insert
to authenticated
with check (auth.uid() = id);

create policy "Usuário atualiza o próprio perfil"
on public.profiles for update
to authenticated
using (auth.uid() = id)
with check (auth.uid() = id);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, email)
  values (new.id, new.email)
  on conflict (id) do update set email = excluded.email;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute procedure public.handle_new_user();
