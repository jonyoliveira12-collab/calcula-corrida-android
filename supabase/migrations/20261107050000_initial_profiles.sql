-- JHONDRIVER: perfis de usuários vinculados ao Supabase Auth.
-- Senhas NÃO são armazenadas nesta tabela; o Supabase Auth gerencia credenciais.

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text,
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

drop policy if exists "Ler próprio perfil" on public.profiles;
create policy "Ler próprio perfil"
on public.profiles
for select
to authenticated
using (auth.uid() = id);

drop policy if exists "Criar próprio perfil" on public.profiles;
create policy "Criar próprio perfil"
on public.profiles
for insert
to authenticated
with check (auth.uid() = id);

drop policy if exists "Atualizar próprio perfil" on public.profiles;
create policy "Atualizar próprio perfil"
on public.profiles
for update
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
for each row
execute function public.handle_new_user();
