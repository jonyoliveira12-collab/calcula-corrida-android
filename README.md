# JHONDRIVER 0.4.1 — análise automática

A função principal é ler automaticamente a oferta exibida pela Uber/99/inDrive/iFood, calcular os indicadores no aparelho e apresentar o cartão sobre o app de motorista. O botão CC funciona com o app em segundo plano, enquanto Copiloto ou Semáforo e as permissões necessárias estiverem ativos. A edição 0.4.0 por imagem foi retirada; não há mais fluxo para escolher foto de oferta.

## Métricas e IA

O cartão mostra valor da oferta, minutos até o passageiro quando identificados, tempo total, distância, nota quando identificada, R$/km e R$/hora com centavos. A distância total e o tempo incluem chegada e viagem quando ambos os trechos aparecem. Dados ausentes não são inventados. R$/hora é projeção bruta da oferta, não promessa de ganho nem lucro líquido.

A leitura/OCR e os cálculos acontecem localmente. Depois de consentimento explícito, a IA recebe somente números e classificação, sem imagens, endereços, nomes ou conversas. A Edge Function `analyze-offer` na Supabase deve validar a sessão e usar `GEMINI_API_KEY` nos secrets do servidor. O APK usa apenas a chave publicável e a sessão do usuário. Nenhuma chave privada de IA deve entrar no APK, repositório ou conversa.

A função `analyze-offer` está publicada no projeto `hyoaomusfpeccdchgoik`, com validação JWT obrigatória e verificação da sessão na função. As funções existentes `hello-world` e `quick-responder` foram preservadas. O código está em `supabase/functions/analyze-offer/index.ts`. Verificação ao vivo: OPTIONS retornou 200 com chave GEMINI_API_KEY configurada (somente presença, sem revelar valor); POST sem autenticação retornou 401. Teste direto de geração com números fictícios: gemini-2.5-flash retornou 404 apesar de aparecer no catálogo. O padrão foi migrado para gemini-3.5-flash-lite, que respondeu HTTP 200 e produziu orientação em português em 6,17 s (incluindo inicialização e rede). O código temporário de verificação foi removido; o POST normal continua exigindo sessão válida. O fluxo completo com a conta real no Android ainda precisa de teste no aparelho. Não é necessária outra versão do APK 0.4.1 para essa correção no servidor.

O cliente mostra estados distintos para função ausente, falta de configuração, sessão expirada e indisponibilidade. As métricas locais continuam independentes da IA. Novos logins guardam o refresh token em armazenamento privado do app e renovam a sessão em resposta a um 401, sem registrar tokens nos logs.

## Permissões, segundo plano e bancos

O serviço declara uma lista explícita de pacotes de motorista e do próprio JHONDRIVER. Texto/imagem de ofertas só é processado quando o app de motorista reconhecido está ativo. A bolha e o cartão são removidos em outros apps. Android 14+ usa captura da janela específica; o fallback Android 11–13 exige app de motorista em tela cheia. Android anterior usa somente o texto de acessibilidade. O serviço não aceita nem recusa corridas, não faz gestos nem cliques automáticos.

A auditoria do pipeline confere essa configuração e permissões não relacionadas. Ela NÃO significa aprovação do Play Protect ou garantia de compatibilidade com Nubank. A causa exata do bloqueio do banco ainda não foi confirmada. Não desative proteções para usar o app. Não foi tentado disfarçar a identidade nem contornar a proteção do banco.

Na versão 0.4.5, quando o Android identifica uma janela do Nubank entre as janelas interativas, a leitura de ofertas é pausada e a bolinha e os cartões são removidos, inclusive se outro app estiver com foco. A leitura volta após o banco sair das janelas identificadas. A proteção depende das informações de janela disponibilizadas pelo Android; não garante liberação pelo banco. O teste usa um aplicativo sintético no emulador, não o Nubank real.

O usuário também pode escolher “DESLIGAR PARA USAR BANCO” no perfil ou no menu CC. Isso desativa o serviço de acessibilidade, preservando as opções de Copiloto e Semáforo; a reativação exige ação explícita do usuário nos ajustes do Android. “Pausar leitura” sozinho não desativa o serviço de acessibilidade. O usuário relatou que o Nubank abriu quando a leitura foi desativada, mas a causa específica da avaliação de risco continua sem confirmação do banco.

O nome exibido é JHONDRIVER. O pacote de avaliação permanece `br.com.calculacorrida.teste`; o aplicativo oficial mantém `br.com.calculacorrida`.

## Login

O botão de olho mostra/esconde a senha e volta a escondê-la quando a tela perde o foco. CRIAR CONTA tem texto dourado de alto contraste e borda visível.

## Publicação e atualizações

A publicação oficial exige os secrets privados `JHONDRIVER_KEYSTORE_BASE64`, `JHONDRIVER_KEYSTORE_PASSWORD`, `JHONDRIVER_KEY_ALIAS` e `JHONDRIVER_KEY_PASSWORD` no GitHub Actions, mantendo o certificado original conferido pelo pipeline. A exposição anterior da chave no histórico não foi desfeita e ainda exige planejamento de rotação. A configuração privada e publicação oficial continuam pendentes.

O app está configurado para Android 16 (API 36), exigido em novos envios à Play. O pipeline gera um AAB **sem assinatura** para verificar a compilação; esse arquivo não é uma versão pronta para publicar. A assinatura oficial, os dados da loja, as declarações de acessibilidade/privacidade e os testes no dispositivo real ainda são necessários.

O aviso de atualização procura releases com pacote/certificado compatíveis e versão maior. A pessoa confirma o download/instalação. Versões antigas sem verificador precisam de uma instalação manual inicial. O APK debug de avaliação não substitui o app oficial.

## Verificação

`gradle testDebugUnitTest assembleDebug`

`gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=br.com.calculacorrida.ReaderServiceTest,br.com.calculacorrida.LoginControlsTest`

Os testes verificam cálculos, chegada/nota ausentes, bolinha CC e controles do login. Uma oferta real da Uber e o acesso bancário ainda precisam de testes no dispositivo do usuário. O código do Rota Pro não foi acessado; apenas sua descrição pública e guia de permissões foram consultados.

## Avisos observados na Supabase

A inspeção de segurança retornou permissões EXECUTE públicas para `handle_new_user` e `rls_auto_enable`, além de proteção de senhas vazadas desativada. Não alteramos o esquema nem os privilégios dessas funções durante esta restauração. Revisar com cuidado para preservar os fluxos de cadastro existentes.

- [Permissões anônimas em funções SECURITY DEFINER](https://supabase.com/docs/guides/database/database-linter?lint=0028_anon_security_definer_function_executable)
- [Permissões de usuários autenticados](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable)
- [Proteção contra senhas vazadas](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection)

## Referência funcional pesquisada

A descrição pública do Rota Pro confirma uso de acessibilidade para ler ofertas, exibir o cartão e calcular métricas. A documentação de configuração cita acessibilidade, sobreposição, notificações e ajustes de bateria. Isso não informa sua implementação interna nem explica a aceitação por cada banco.

- [Descrição no Google Play](https://play.google.com/store/apps/details?id=app.rotapro&hl=pt-BR)
- [Guia de permissões](https://rotapro.app/ajuda/primeiros-passos/)


### Compact offer card (0.4.2)

Semáforo → Estilo do cartão opens a light preview/settings screen. R$/km, R$/hour,
passenger rating, pickup details and AI commentary can be individually shown or
hidden. The warning can appear above or below, in light/dark/colored themes.
The reader enumerates visible allowlisted driver windows, including unfocused
windows, and on Android 14+ captures only the selected window for OCR. A capture
is discarded if that window disappears or changes bounds during recognition.
Overlay size and placement are clamped to the owning app window after rotation.

`reader-fixture` is an emulator-only synthetic pixel fixture, installed under the
allowlisted Uber package solely in the isolated CI emulator. It contains no Uber
code, account or networking. It is never included in or distributed with the
JHONDRIVER APK. The instrumentation test rotates the emulator and moves focus
to another overlay before checking OCR. Synthetic fixtures cannot certify
coverage of all real driver-app versions, multiwindow layouts or bank acceptance.


### Reader correction (0.4.3)

The reader also handles allowlisted TYPE_SYSTEM windows used by driver offer
overlays. A substantial offer overlay takes priority over a small driver bubble.
Window identity and window bounds are checked independently of transient node
bounds. Interactive-window retrieval flags are restored when the service connects.
See https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#getWindows() .

99 countdown offers with two route legs and an explicit offer marker can be read
without an Accept label. Dynamic base-fare labels are excluded from the total fare.
OCR retries rotated inputs after an incomplete first result. Polling recovers after
transient platform exceptions and screenshot requests have a recovery timeout.

CC remains available in the background with pause/resume/stop controls. A known
Nubank foreground package suppresses reading/overlays; this does not certify bank
compatibility. Home includes a heartbeat and a copyable numeric/status diagnostic
without screenshot content, addresses, authentication tokens or private keys.
The card uses a small colored verdict with no large filled warning banner and a
20-second hold period (20/30/60 seconds selectable).

CI synthetic fixtures cover portrait, landscape, 99 countdown pricing, inDrive, and
a separate Uber overlay over Android Settings alongside a smaller driver bubble.
No user screenshots are published in the repository or bundled in the APK.
