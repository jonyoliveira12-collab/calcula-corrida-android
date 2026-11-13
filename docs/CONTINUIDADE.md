# Continuidade do JHONDRIVER

## Pedido confirmado

- O usuário está com a versão 0.5.0 instalada.
- A próxima versão deve atualizar a instalação existente, preservando dados.
- A aparência escolhida é a opção 2 da referência: chamas realistas ao redor do cartão.
- Cada etiqueta acompanha exclusivamente a oferta do aplicativo de origem: continua enquanto a oferta estiver visível e desaparece após a confirmação de que ela sumiu. Não deve desaparecer só porque um tempo fixo passou.
- Uber, 99, inDrive e iFood estão no escopo, incluindo ofertas de moto. Outros aplicativos precisam ser identificados antes de ampliar a lista de leitura.
- Suporte no Perfil por telefone e e-mail, usando os contatos fornecidos pelo proprietário. As ações abrem o discador ou o compositor de e-mail, sem ligar ou enviar mensagens automaticamente.

## Atualização sem desinstalação

A avaliação 0.5.0 usa `br.com.calculacorrida.avaliacao050`; a avaliação 0.5.1 usa `br.com.calculacorrida.avaliacao051`. São instalações diferentes. Além do mesmo pacote, o Android exige o mesmo certificado (ou uma linhagem de assinatura válida) e um código de versão crescente para atualizar preservando dados.

O workflow da avaliação 0.5.0 usava a assinatura debug padrão do ambiente de compilação, sem salvar sua chave. A chave privada correspondente à instalação do usuário não foi localizada. Trocar apenas o sufixo do pacote não resolve essa incompatibilidade. Não orientar a desinstalação nem prometer atualização compatível antes de verificar a assinatura do APK instalado.

A publicação oficial já prevê chave privada nos secrets do GitHub Actions e verificação de certificado. Ela usa o pacote oficial, diferente da avaliação instalada. Manter uma identidade e uma chave privada estáveis nas próximas publicações. Não reutilizar a antiga chave exposta no histórico nem colocar novas chaves no código.

## Validação

Os testes usam ofertas sintéticas. A inclusão do pacote na lista e o teste de análise numérica não comprovam leitura de todas as versões reais dos aplicativos, principalmente de entregas e moto. Ainda é necessário validar ofertas reais no tablet.

## Ajustes adicionais confirmados

- Preservar o layout da entrada; aumentar apenas as chamas realistas, com altura maior acima do formulário.
- Melhorar o painel e as configurações internas, com cartões mais organizados, estados claros e botões legíveis. Os detalhes técnicos de leitura ficam em uma área expansível.
- O seletor de lucro passa a mostrar/ocultar a margem estimada na etiqueta. Fórmula: `(oferta - distância total × custo por km) / oferta × 100`. Valores negativos permanecem visíveis. Isso não inclui custos que o motorista não configurou.
- A opção de voz passa a anunciar novas ofertas usando a síntese de voz do Android. Desativar a opção, pausar a leitura ou remover a oferta interrompe o áudio pendente. A reprodução audível ainda precisa ser validada no tablet com mecanismo de voz português instalado.

## Nova assinatura autorizada

O proprietário autorizou uma instalação inicial com chave nova, seguida de atualizações com essa mesma chave. Foi gerada uma chave privada nova fora do repositório. O certificado público está fixado no workflow; a chave privada não foi adicionada ao Git. O APK assinado usa o pacote oficial `br.com.calculacorrida`, versão 0.5.1, código 17.

Foi comparado o certificado do APK 0.5.0 publicado com o certificado da antiga chave do histórico: são diferentes. Não usar essa chave antiga para tentar atualizar a avaliação 0.5.0.

O backup privado foi preparado separadamente. O armazenamento nos quatro Secrets do GitHub continua pendente: a integração respondeu que não tem permissão para esse recurso. A publicação automática agora exige também a aprovação dos testes no emulador.

## Conexão exigida, sem aguardar a IA

O proprietário escolheu exigir internet validada para ler ofertas e exibir novos cálculos, sem esperar o comentário da IA. A perda de conexão remove as etiquetas, interrompe a voz e pausa o leitor; a conexão restabelecida permite uma nova leitura. As contas determinísticas continuam no aparelho por rapidez e precisão. O comentário da IA é separado e não bloqueia a etiqueta.

A função de IA publicada foi encontrada ativa e o endpoint respondeu à verificação de configuração. A geração autenticada de comentário com a conta do usuário no tablet ainda não foi validada nesta sessão. Não prometer tempo real para o comentário da IA nem confundir a validação do endpoint com um teste completo da IA.

## Posição, tamanho e nova referência visual

O proprietário pediu que as etiquetas possam ser arrastadas para qualquer ponto da tela. O limite antigo à janela da oferta foi removido para posições manuais; as posições automáticas continuam associadas à janela correta. Cada aplicativo e orientação guarda sua posição. Há controle de tamanho de 60% a 160%, incluindo o texto, e restauração das posições automáticas. A configuração tem acesso destacado no Perfil.

A nova referência visual interna usa preto, dourado, cidade no cabeçalho, oferta central destacada, atalhos laterais e resumo em quatro cartões. Os atalhos filtram as ofertas registradas por aplicativo. Os totais de ofertas não são apresentados como faturamento de corridas concluídas, e a meta mostra a projeção por hora da oferta, pois o aplicativo ainda não registra confirmação de conclusão das corridas.

A confirmação de configurações restritas pertence ao Android e não foi contornada. Foi adicionado um guia opcional com atalhos para as informações do próprio app e para a acessibilidade. A distribuição pela Play depende da aprovação e das regras da loja e não garante ausência dessa confirmação em todos os aparelhos.

## Backup persistente da assinatura verificado

O proprietário confirmou que o projeto Supabase JHONDRIVER é seu ou de confiança e autorizou explicitamente guardar a chave de assinatura e as senhas nesse destino. O backup foi salvo no Vault desse projeto sob o nome `jhondriver_android_release_signing_v051`.

O registro contém o PKCS12 em base64, alias, senhas, pacote e certificado público. A integridade do conteúdo completo e a correspondência do certificado foram verificadas no servidor. Os papéis `anon` e `authenticated` não têm acesso ao schema Vault nem à visão de segredos descriptografados. A chave privada nova não está em arquivos do repositório nem em assets da release.

Esse backup é a identidade persistente para as atualizações da instalação oficial `br.com.calculacorrida` iniciada com a versão 0.5.1. As próximas versões precisam reutilizar essa chave e aumentar o código de versão. A automação de assinatura dentro do GitHub Actions ainda requer cadastrar os quatro Secrets; enquanto isso, a chave pode ser recuperada privadamente do Vault para gerar e publicar um APK assinado compatível.

## Consolidação de moto/iFood (0.5.2)

A alteração separada `fix/moto-ifood-reading-2026` foi criada sobre uma base antiga 0.2.0, com outra assinatura. Suas mudanças úteis foram incorporadas seletivamente à base atual, preservando painel, permissões, etiquetas e assinatura. A 0.5.2 usa código de versão 18 e a mesma chave preservada no Vault. A leitura reconhece o botão Escolher e os totais de distância/tempo explicitamente identificados no iFood; ofertas parciais ou contraditórias continuam rejeitadas. Foram acrescentados testes unitários e cenários de leitura por imagem para Uber Moto e entrega iFood.
