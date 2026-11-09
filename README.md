# JHONDRIVER 0.4.0

Edição com análise manual e por imagem escolhida pelo usuário. Nome de exibição: JHONDRIVER. A edição de avaliação mantém seu identificador existente (`br.com.calculacorrida.teste`), evitando confundir uma mudança cosmética com uma correção de segurança.

## Mudança de funcionamento

Foram removidos do código e do manifesto o serviço de acessibilidade, captura automática da tela, janelas de sobreposição, bolinha CC e cliente de IA das ofertas. Não há leitura automática de outros aplicativos. O usuário digita os valores ou escolhe/compartilha uma imagem; OCR ocorre no aparelho e os valores precisam ser revisados antes do cálculo. Nenhuma imagem é enviada ao servidor. A análise pode ser aberta sem conta na tela de login. Conta e atualizações continuam usando internet.

As imagens selecionadas não são copiadas para o histórico. Somente os valores confirmados são salvos localmente. O usuário pode apagar o histórico no aplicativo. O seletor do Android concede acesso apenas à imagem escolhida, sem solicitar acesso geral à galeria.

## Segurança e compatibilidade

O pipeline verifica manifesto, permissões e ausência das classes removidas no APK. Isso não equivale a aprovação do Google Play Protect nem garante aceitação pelo Nubank ou outro banco. Não desative proteções do aparelho para instalar ou usar esta edição. Alertas devem ser investigados com o banco/Google pelos canais oficiais.

## Atualizações e assinatura

O aviso de atualização está implementado e só oferece uma versão superior com mesmo pacote e certificado declarados no manifesto público da release. Versões antigas precisam de uma instalação manual inicial compatível. A publicação oficial permanece bloqueada até cadastrar os quatro segredos privados em Settings → Secrets and variables → Actions:

- `JHONDRIVER_KEYSTORE_BASE64`: chave original em base64.
- `JHONDRIVER_KEYSTORE_PASSWORD`: senha do arquivo.
- `JHONDRIVER_KEY_ALIAS`: alias da chave.
- `JHONDRIVER_KEY_PASSWORD`: senha da chave.

Não envie chaves/senhas por mensagens, issues ou commits. A compilação de publicação confere o certificado original `01537f8902325696735cd4db6710ec2186eb21f8007e11ed74d757f8af30d5b8`. A exposição anterior no histórico não foi desfeita; a distribuição oficial precisa de planejamento de rotação. O APK debug de avaliação não atualiza o aplicativo oficial.

## Validação

`gradle testDebugUnitTest assembleDebug`

`python .github/scripts/audit_apk.py app/build/outputs/apk/debug/app-debug.apk`

Teste instrumentado: `ManualOfferTest` confirma ausência das permissões/serviço e cálculo manual de R$ 18,40, 12,6 km e 28 min. Aprovação do Play Protect e compatibilidade bancária exigem validação externa.
