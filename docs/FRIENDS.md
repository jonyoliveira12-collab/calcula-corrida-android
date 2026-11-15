# Amigos no JHONDRIVER

Área separada da captura de ofertas: adicionar por código, aceitar convite, presença online, conversa individual e compartilhamento voluntário da última posição.

## Fluxo do motorista

1. Entrar com a conta e abrir **Amigos** no início.
2. Definir o nome público, copiar o código de amigo e trocar o código com a pessoa desejada.
3. Enviar/aceitar o convite. Ser amigo não concede acesso à posição.
4. Tocar **Ficar online** para ativar presença e receber notificações genéricas de mensagens.
5. Abrir o amigo, escolher compartilhar, conceder a permissão de localização e confirmar 15 minutos, 1 hora ou 4 horas.
6. Parar para um amigo ou ficar offline na tela/notificação a qualquer momento. Nova sessão online nunca reativa compartilhamentos anteriores.

A posição pode ser aproximada conforme a permissão Android. O mapa mostra horário e precisão. Não existe garantia de conexão nem substituição de serviços de emergência. Não interagir com o chat dirigindo.

## Comportamento e privacidade

- Abrir a tela ou ficar online não inicia GPS. Não solicita contatos nem localização permanente em segundo plano.
- Serviço de primeiro plano iniciado pelo usuário; notificação contínua; sem início automático no boot, sem reinício automático após encerramento.
- GPS/rede apenas enquanto existe consentimento ativo; ponto recente enviado aproximadamente a cada dez segundos, sem trilha histórica no banco.
- Presença e mensagens consultadas a cada vinte segundos no serviço; tela atualiza a cada cinco segundos. Atrasos e falta de sinal podem ocorrer.
- Presença expira após 75 segundos sem resposta; posição expira após 120 segundos e exige presença válida. Ao perder internet, interromper é imediato no aparelho; a revogação remota depende da conexão ou desses prazos.
- Conta autenticada, sessão ainda válida e amizade aceita são exigidas no servidor. Compartilhamento é individual, temporário, revogável, não recíproco automaticamente.
- Bloquear/remover revoga as permissões nas duas direções. Desbloquear exige novo convite.
- Texto de mensagens fica no banco protegido por autorização e HTTPS; não há promessa de criptografia ponta a ponta. Push de mensagens não exibe conteúdo na tela bloqueada.
- Somente a última coordenada é armazenada, sem histórico de deslocamento. Mensagens são retidas para a conversa; apagar a conta remove os dados sociais por cascata.
- Mapa usa Leaflet (licença incluída) e tiles do OpenStreetMap. O provedor recebe os pedidos de mapa, sem JWT, e-mail ou mensagens. Nenhuma ponte JavaScript é exposta.

## Banco e validação

Migrações versionadas em `supabase/migrations`, schema privado `jd_social`, tabelas sem acesso direto de clientes e RLS com negação por padrão. A função pública é invoker; a implementação privada valida a sessão e deriva o usuário do JWT. Não distribui chave administrativa no app.

`tests/friends_backend.sql` executa fixtures transitórias dentro de transação com rollback. Cobre convites pendentes, isolamento, consentimento, expiração, pontos antigos, bloqueio, repetição de mensagens, nova sessão de serviço e login revogado. Nenhum usuário real ou localização real é usado.

`FriendLocationPolicyTest` valida posições inválidas/antigas e durações. `FriendsPrivacyTest` verifica em Android que abrir a área não ativa serviço/GPS e que não há permissão de localização permanente. A bateria preexistente do leitor continua no workflow Android.

## Aparência e assinaturas futuras

O motorista escolhe tema escuro ou claro nas configurações ou na área Amigos. A escolha fica no aparelho e acompanha painel, relatórios, histórico, configurações, entrada e editor da etiqueta, sem mudar o tema escolhido para a etiqueta flutuante. Trocar tema não altera as preferências do leitor nem ativa GPS.

Há apenas um anúncio dos futuros planos mensal, trimestral e anual, identificado como **Em breve**. Não há preço, contratação, débito ou cobrança implementados.

`AppAppearanceTest` verifica escolha real no seletor, persistência entre telas, contraste no tema claro, ausência de GPS e preservação das preferências da etiqueta. Capturas usam nomes/dados sintéticos de exemplo.

Validação recomendada em uso real: teste com duas contas reais em aparelhos, permissões precisa/aproximada, app em segundo plano, perda de rede e revogação enquanto o mapa está aberto. Não afirmar proteção total nem localização instantânea.

## Atualização 0.7.0

- A navegação mantém Início, Metas, Histórico, Amigos e Configurações. Relatórios foi retirado; o histórico de ofertas continua preservado.
- Assinaturas são somente anúncio **EM BREVE**: mensal R$10, trimestral R$25, anual R$90, sem contratação/cobrança ativa. Avisos e contatos também aparecem na faixa rolante.
- Ajuda e suporte ficam em uma área própria ao final das configurações, com assunto do atendimento, telefone, e-mail e verificador de atualização.
- Escuro, Cinza, Claro e Natal/Ano-Novo. A opção sazonal expira em 7/1/2027 e retorna ao Cinza se selecionada.
- Etiquetas oferecem Padrão verde/Fogo realista; textura de fogo contínua sem nove recortes. A bolinha agora exibe JD, preta sobre branco. Avaliação/cores e cálculos continuam independentes da decoração.
- Em telas largas, contatos e conversa ficam lado a lado. No celular, abrir um contato mostra a conversa em uma tela própria. O campo de mensagem fica fixo ao final.
- Online/Ausente/Offline são opções explícitas. Tela apagada pode marcar ausente; um estado manual ausente não é desfeito ao acender a tela. Offline encerra serviço e compartilhamentos. Mensagens em segundo plano são notificadas pelo serviço enquanto online/ausente; não há FCM nem promessa de aviso com o app forçado a parar.
- A localização continua apenas durante autorização individual vigente. A última posição compartilhada pode abrir Waze/Google Maps; o destino externo não acompanha automaticamente um amigo em movimento.
- Fotos escolhidas no perfil são publicadas privadamente aos amigos aceitos. Áudios de até60s são gravados somente pela ação explícita do usuário, com permissão de microfone, prévia, envio e descarte. Gravação cancela ao sair da conversa; reprodução para ao sair. Arquivos temporários ficam no cache privado e são apagados no descarte/encerramento normal.

### Segurança dos arquivos

Bucket privado `jd-friend-media`, máximo2MiB e MIME JPEG/MP4-AAC. Caminhos imutáveis separados por usuário; até100uploads/dia por conta. O cliente não recebe URL pública nem chave administrativa.

O teste real de revogação mostrou que usar diretamente Storage GET podia manter autorização em cache. Por isso, somente o proprietário lê diretamente no Storage. O destinatário acessa a Edge Function `jd-friend-media` com seu JWT; ela consulta `jd_friend_media_access` a cada requisição (sessão ainda válida, amizade aceita e arquivo referenciado), busca bytes com chave somente do servidor e responde com `Cache-Control: private, no-store`. Não há URL assinada durável. Bloquear/remover corta novas leituras; arquivos já recebidos no dispositivo não podem ser apagados remotamente.

Validação: SQL transacional com rollback para dono/terceiro/pendente/bloqueado, áudio idempotente, duração e estado ausente; HTTP com três contas sintéticas, JPEG e M4A silencioso reais, upload/leitura/revogação, com limpeza dos objetos e contas ao final; instrumentação Android13/16 para gravação/revisão/descarte/cancelamento do microfone, notificação, seleção de tema e layout do tablet; bateria preexistente do leitor/calculadora. Capturas contêm somente dados de exemplo.
