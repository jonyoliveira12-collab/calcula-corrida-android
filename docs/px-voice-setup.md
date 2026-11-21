# Infraestrutura do PX

Situação: o PX ainda não está implementado/publicado. Esta é a preparação do serviço necessário para voz ao vivo; o aplicativo publicado continua na versão 0.8.2.

## Solução proposta

Usar LiveKit/WebRTC para a voz, mantendo Supabase para conta, amizade, convites e autorização. A autorização de Broadcast do Supabase fica em cache durante a conexão; por isso não vamos tratar o canal de chat existente como transporte de voz privado com revogação imediata.

O SDK Android estável consultado é `io.livekit:livekit-android:2.30.0`. O serviço Android de PX deve começar com a tela visível, permissão de microfone concedida e notificação ativa. Ao entrar, a captura fica desligada; segurar o botão solicita a vez e só então ativa o microfone. Soltar, cancelar o toque, perder conexão ou sair encerra a captura.

Apenas os participantes autorizados recebem ingresso do servidor. Chaves administrativas de voz ficam no servidor, fora do APK. Convites/remoções, disputa pela fala e interrupção de participantes já conectados precisam de testes reais antes de liberar grupos.

## Informação que falta

Não foi encontrada configuração de servidor PX/LiveKit no cofre do projeto. Falta uma conta/projeto LiveKit Cloud ou um servidor LiveKit próprio com endereço público e credenciais administrativas.

Para começar sem contratar plano pago: criar um projeto no plano Build gratuito do LiveKit Cloud, sujeito às cotas vigentes. Nenhuma conta, cobrança ou assinatura foi criada por esta tarefa.

Depois de criar o projeto, configurar no cofre privado do backend:

- `LIVEKIT_URL`: endereço WebSocket do projeto.
- `LIVEKIT_API_KEY`: identificador da chave administrativa.
- `LIVEKIT_API_SECRET`: segredo administrativo, usado apenas no servidor para ingressos e controle dos canais.

Só depois da configuração será possível testar transmissão real entre aparelhos e publicar uma versão com PX funcional. A conta e as credenciais do novo serviço não podem ser deduzidas da conexão atual com GitHub/Supabase.

Referências consultadas:

- https://docs.livekit.io/transport/sdk-platforms/android/
- https://github.com/livekit/client-sdk-android/releases
- https://livekit.com/pricing
- https://supabase.com/docs/guides/realtime/authorization
- https://developer.android.com/develop/background-work/services/fgs/service-types

## Verificação de configuração — 21/11/2026

O usuário cadastrou LIVEKIT_URL, LIVEKIT_API_KEY e LIVEKIT_API_SECRET nos Edge Function Secrets. A sonda temporária autenticada por desafio privado confirmou a presença dos três campos sem retornar os valores. Uma chamada de servidor para servidor ao LiveKit RoomService/ListRooms foi rejeitada com HTTP 401. A segunda verificação, normalizando espaços nas extremidades, também retornou 401. A causa específica não foi confirmada; conferir chave e segredo da mesma credencial no projeto correto antes de prosseguir com os testes de mídia. Nenhum segredo foi exposto ao chat, APK ou repositório.

A definição do produto mudou: o rádio deve ter bolinha PX separada, com o nome da pessoa/grupo; a bolinha JD do leitor permanece independente e inalterada. Não publicar um APK anunciando PX funcional enquanto autenticação e transmissão real não forem validadas.

Nova verificação no mesmo dia confirmou a causa: LIVEKIT_API_KEY e LIVEKIT_API_SECRET contêm o mesmo valor (comparação feita apenas no servidor; nenhum valor retornado). LiveKit retornou invalid token. Corrigir o segredo correspondente à chave; relógios de servidor e função estavam sincronizados.
