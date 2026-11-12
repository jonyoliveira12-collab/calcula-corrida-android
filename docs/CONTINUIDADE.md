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
