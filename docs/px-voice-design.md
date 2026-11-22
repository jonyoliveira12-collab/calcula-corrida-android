# PX — comportamento proposto

Estado: implementado; atualização de segundo plano e modos de fala em validação para 0.9.2.

## Uso

- Aba inferior exclusiva PX, sem substituir Amigos nem remover Metas/Histórico.
- Selecionar conversa de voz privada com amigo aceito ou grupo privado por convite.
- Entrar e sair do canal explicitamente. Os participantes escolhem participar da escuta.
- Um único canal ativo por aparelho, com nome do destinatário/grupo sempre visível.
- No modo direto, participantes autorizados podem transmitir simultaneamente, sem pedido HTTP por fala.
- Na tela PX e na bolinha: escolher segurar/soltar ou tocar para iniciar e tocar novamente para parar. A escolha é salva. Ambos transmitem ao vivo; não geram mensagem de áudio gravada.
- Apagar a tela encerra qualquer transmissão, mas mantém canal e escuta ativos no serviço em primeiro plano. O serviço mantém CPU ativa durante o PX; sair libera esse recurso.
- Perda de foco de áudio interrompe o microfone e silencia o PX, sem sair do canal. Recuperar foco retoma somente escuta; o microfone exige novo gesto. Reconexão de transporte também não expulsa do grupo.
- O áudio de escuta usa modo normal/mídia, sem manter o aparelho em modo de chamada. Antes de transmitir, gravação ativa de outro aplicativo impede abertura do microfone do PX. A captura silenciada pelo sistema encerra a fala.
- Fora do aplicativo: bolinha PX própria e independente, com o nome da pessoa ou do grupo. Não reutilizar nem alterar a bolinha JD do leitor. A bolinha PX aparece somente após ativar o rádio e entrar explicitamente no canal; desaparece ao sair/desligar. Segurar solicita a fala; soltar/cancelar encerra a transmissão. Arrastar move somente a bolinha PX e cancela o gesto de fala.
- Estado da bolinha PX e notificação identificam canal, conexão, quem fala e microfone ativo. Nenhuma transmissão apenas por entrar no canal.
- Perda do toque, ACTION_CANCEL, desconexão, saída do canal, troca de conta, revogação de convite ou parada do serviço encerram a captura e liberam a vez de fala.
- Limite de fala e validade curta da autorização evitam microfone preso após falha de rede.
- Notificação permanente com silenciar, sair e parar o PX. O usuário pode desligar a escuta fora do app.
- As mensagens gravadas do chat continuam independentes; não confundir um arquivo enviado com voz ao vivo.

## Compatibilidade Android e integração

O app atual só grava áudio com FriendsActivity visível e encerra captura ao sair. FriendsService não declara tipos de serviço para microfone/reprodução. A bolinha pertence ao leitor de ofertas e desaparece quando a leitura é desligada, em janelas sensíveis e ao apagar a tela.

PX exige serviço de voz dedicado, iniciado explicitamente com a atividade visível e permissões concedidas, com tipos microphone/mediaPlayback e notificação. Ouvir no segundo plano e falar pela bolinha dependem desse serviço ativo. Não usar acessibilidade para obter acesso oculto ao microfone ou burlar bloqueios do Android/bancos. Modo banco continua desligando a leitura; o comportamento do PX em telas sensíveis deve respeitar essa escolha e não recriar automaticamente a bolinha.

O leitor atual precisa de uma interface de coordenação de gesto, sem substituir sua captura de ofertas. Áudio ao vivo deve ter transporte próprio, limitado e autenticado: avaliar latência, rede móvel, consumo de dados/bateria e cotas/custo antes de selecionar a infraestrutura. O polling de chat e os uploads de arquivos existentes não oferecem um PX ao vivo.

Amizade/grupo e consentimento precisam ser verificados no servidor. Bloqueio/remoção deve interromper acesso também em conexões já abertas. Sem chaves administrativas no APK e sem canais de áudio públicos por padrão. Não gravar o canal automaticamente.

## Critérios para liberar

- Teste entre dois aparelhos: um fala, o outro ouve com o aplicativo fora da tela.
- Grupo com disputa simultânea pela vez e nenhum cruzamento entre canais.
- Soltar/cancelar toque, sair, interromper internet e revogar acesso param a transmissão.
- Nenhum áudio recebido por terceiros, removidos ou contas trocadas.
- Regressões da bolinha: toque curto, arraste, pausa de leitura e Modo banco preservados.
- Microfone inativo sem pressionar; indicadores corretos; foco e volume de mídia respeitados.
- Android 13 e 16, tela bloqueada, interrupção por chamada, Bluetooth e retorno de conexão.
- Medir atraso real e consumo antes de anunciar disponibilidade. Não publicar só a aba sem transmissão funcional.

Referências: https://developer.android.com/develop/background-work/services/fgs/service-types e https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
