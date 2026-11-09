# JHONDRIVER

Aplicativo Android para análise de ofertas de corrida.

## Assinatura privada e atualização sem desinstalar

A publicação usa exclusivamente a chave original, fornecida por secrets privados do GitHub Actions. O certificado é conferido antes da compilação: SHA-256 `01537f8902325696735cd4db6710ec2186eb21f8007e11ed74d757f8af30d5b8`. Isso corresponde à chave que estava no repositório; a compatibilidade com uma instalação concreta depende de ela ter sido assinada com esse mesmo certificado.

No repositório, abra **Settings → Secrets and variables → Actions → New repository secret** e cadastre:

| Secret | Conteúdo |
| --- | --- |
| `JHONDRIVER_KEYSTORE_BASE64` | Arquivo da chave original convertido em base64, sem quebras de linha |
| `JHONDRIVER_KEYSTORE_PASSWORD` | Senha do arquivo original |
| `JHONDRIVER_KEY_ALIAS` | Alias original |
| `JHONDRIVER_KEY_PASSWORD` | Senha da chave original |

Use a cópia original da chave e suas credenciais existentes. Não cole esses dados em issues, commits, conversas ou logs. Não gere outra chave para preencher esses secrets. Se a chave original não estiver disponível, mantenha a publicação bloqueada e planeje uma migração.

Os testes de pull requests não recebem secrets. A publicação em `main` só acontece depois dos testes e falha se faltar configuração ou se o certificado for diferente. A chave temporária é apagada ao final. O APK de teste local usa assinatura debug e não deve ser distribuído como atualização do aplicativo existente.

A remoção da chave do estado atual do repositório **não apaga a exposição no histórico**. Guardá-la em Secrets evita novas publicações, mas não torna a chave antiga novamente secreta. Será necessário planejar uma rotação compatível para distribuição futura; esta alteração conserva a assinatura existente para evitar uma reinstalação imediata.

## Validação

`gradle testDebugUnitTest assembleDebug`

Testes cobrem valores por km/hora, metros, dois trechos, resumo sem duplicação, moeda com ponto/vírgula, leituras ambíguas e entradas inválidas. OCR, interface e compatibilidade com aplicativos bancários precisam de testes no dispositivo. O botão **Desligar leitura** desativa o serviço; não garante que todos os bancos aceitem o aplicativo instalado.

## Aviso de atualização

As versões que contêm o verificador consultam as releases públicas do GitHub ao abrir o app, com intervalo mínimo de uma hora durante a mesma sessão. Em Perfil, Verificar atualização permite uma consulta imediata. Só aparecem APKs de versão superior, mesmo identificador e mesmo certificado, indicados por um `update.json` de canal stable. A instalação continua exigindo confirmação no Android.

Quem instalou uma versão antiga sem esse recurso precisa receber e instalar uma atualização manual compatível uma vez. Cadastrar os quatro secrets de assinatura e publicar o APK assinado com a chave original continuam sendo pré-requisitos. A versão TESTE é separada e não recebe a atualização do aplicativo principal.

## Diagnóstico da leitura

A bolinha CC aparece no JHONDRIVER e nos apps de motorista reconhecidos enquanto Copiloto ou Semáforo estiver ativo. Pode ser arrastada; um toque abre o app. É removida ao sair desses apps. Desligar leitura desativa o serviço de acessibilidade. O painel informa o status da leitura sem salvar o texto bruto da tela. A consulta periódica ocorre apenas na janela ativa dos apps de motorista permitidos. Android 14+ usa captura da janela específica para não misturar apps em tela dividida; Android 11–13 exige tela cheia para o fallback por imagem.
