# Configuração de autenticação JHONDRIVER

## Serviço escolhido
Supabase (plano Free para começar): autenticação por e-mail/senha e PostgreSQL gerenciado.
O Supabase Auth guarda os hashes das senhas; o aplicativo nunca deve gravar senhas em texto puro.

## Passos necessários
1. Crie um projeto em https://supabase.com/dashboard (não precisa cadastrar cartão para começar no Free, sujeito aos limites e políticas atuais).
2. No painel do projeto, copie Project URL e a chave publicável (Publishable key / anon key legada).
3. Edite app/src/main/java/br/com/calculacorrida/SupabaseConfig.kt e substitua os dois valores de exemplo.
4. No Authentication > Providers > Email, mantenha Email habilitado. Configure confirmação de e-mail conforme desejar.
5. Execute supabase/profiles.sql no SQL Editor. Essa tabela é para dados de perfil; credenciais ficam no Supabase Auth.
6. Compile e teste cadastro, confirmação de e-mail e login.

## Segurança
- Nunca colocar a chave service_role/secret no APK.
- A chave publicável pode estar no app; as políticas RLS protegem os dados.
- O fluxo atual salva o access token localmente para manter a sessão neste aparelho.
- Antes de publicar comercialmente, implementar refresh token, logout, recuperação de senha, exclusão de conta, política de privacidade e testes de segurança.

## Observação
A autenticação só funcionará depois de preencher SupabaseConfig.kt com os dados reais do seu projeto. Nenhuma conta ou banco externo foi criado automaticamente.
