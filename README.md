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
