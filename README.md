# TouchDrop 0.1 — teste de foto pelo toque NFC

Protótipo Android nativo em Java, com interface escura e roxa. Selecione **uma foto**, encoste as traseiras dos dois celulares e confirme no receptor. O NFC inicia a conexão e dispara vibração e animação; a foto segue pela rede local e é salva em **Pictures/TouchDrop**.

## O que esta versão implementa

- Escolha de uma foto pelo seletor de arquivos do Android, sem acesso amplo à galeria.
- NFC real: remetente em modo leitor ISO-DEP; receptor com `HostApduService` (HCE).
- Dados curtos da sessão pelo NFC e confirmação do toque nos dois aparelhos.
- Animação de ondas e vibração quando o diálogo NFC é concluído.
- Confirmação explícita de recebimento antes de transferir os bytes da foto.
- Transferência por socket na mesma rede Wi-Fi/hotspot, sem servidor na internet.
- Progresso, cancelamento, limite de 25 MiB e verificação SHA-256.
- Identificação do formato pelo decodificador de imagens, descarte de arquivos incompletos e salvamento via MediaStore.
- Finalização/cancelamento ao sair do app. Esta versão exige os apps abertos.

## Testar no Galaxy S23 e Galaxy A36

1. Instale o mesmo APK nos dois aparelhos.
2. Ligue o NFC e deixe as telas desbloqueadas.
3. Conecte os dois ao **mesmo Wi-Fi**. Alternativa: ligue o hotspot de um e conecte o outro a ele.
4. No aparelho de destino, abra o TouchDrop e toque em **Receber foto**.
5. No remetente, toque em **Escolher uma foto**, selecione uma imagem pequena para o primeiro teste e toque em **Enviar por encostar**.
6. Aproxime as traseiras, ajustando a posição até alinhar as antenas NFC. Mantenha próximo até aparecer **Toque detectado**.
7. Pode separar os aparelhos. No receptor, toque em **Receber**.
8. Aguarde a confirmação de salvamento. Confira a galeria ou a pasta **Pictures/TouchDrop**.
9. Para inverter o sentido ou enviar outra foto, inicie outra sessão pelos botões.

A animação acompanha o evento de toque em cada aparelho; não há sincronização de quadros entre telas nem detecção da posição física da antena. A velocidade depende da rede e não tem valor garantido.

## Gerar o APK

### Android Studio

- Requisitos: JDK 17, Android SDK 35 e Build Tools 35.0.0.
- Abra esta pasta como projeto no Android Studio.
- Use **Gradle 8.11.1** em Settings → Build Tools → Gradle → Gradle distribution (distribuição local). Este pacote não inclui o wrapper binário do Gradle.
- Sincronize o projeto e execute a tarefa **`:app:assembleDebug`**, ou use a opção Build APK(s).
- Saída: `app/build/outputs/apk/debug/app-debug.apk`.

### Terminal

Com Java 17, Gradle 8.11.1 e SDK configurados em `ANDROID_HOME` ou `local.properties`:

```bash
./scripts/test-protocol.sh
gradle --no-daemon :app:assembleDebug :app:lintDebug
```

### GitHub Actions

O arquivo `.github/workflows/android.yml` instala as ferramentas, executa os testes do protocolo, compila e roda o lint. Ao subir os arquivos na branch `main` ou executar o workflow manualmente, o resultado fica em **Actions → Build TouchDrop APK → Artifacts → TouchDrop-0.1-debug-apk**. Baixe o ZIP do artifact e extraia o APK.

A conta que envia o workflow precisa ter acesso de escrita ao conteúdo e aos arquivos de workflow do repositório. O workflow tem apenas permissão de leitura e não publica releases.

## Estado de verificação

- Os seis testes locais de protocolo passaram: bytes íntegros, truncamento, alteração, limites de tamanho, identificação de sessão e delimitação exata do payload.
- A compilação Android e o lint **não foram executados neste ambiente**: sem SDK, os downloads das ferramentas não puderam ser concluídos.
- A comunicação NFC, a animação e o envio físico S23 ↔ A36 ainda precisam de teste nos aparelhos.
- O envio para `CometinhaStudios/TouchDrop` foi recusado pelo GitHub com HTTP 403. Este pacote contém os arquivos para importação, mas não foi publicado no repositório.

## Limites do protótipo

Android 10+; NFC e HCE no receptor. Fotos JPG, PNG, WebP, GIF e HEIC/HEIF compatíveis com o decodificador do aparelho; até 25 MiB e até 100 megapixels. Não há Wi-Fi Direct, descoberta automática, envio em segundo plano, retomada, envio de apps ou múltiplos arquivos nesta versão.

Use uma rede de teste de confiança: este transporte básico não inclui criptografia de ponta a ponta. O token obtido pelo NFC limita a sessão e o hash detecta corrupção, mas não substituem criptografia. O endereço enviado é IPv4 privado; redes com isolamento entre clientes, VPNs e alguns hotspots podem impedir o acesso. O app não altera as configurações de rede nem cria o hotspot sozinho.

## Se o toque não funcionar

- Garanta que um aparelho esteja em **Receber foto** e o outro em **Enviar por encostar**.
- Retire acessórios metálicos e ajuste a posição das traseiras.
- Abra e mantenha ambos os apps em primeiro plano, com NFC ligado e tela desbloqueada.
- Inverta os papéis para verificar se a falha ocorre só em uma direção.
- Se vibrou/animou, mas não transferiu, confira o mesmo Wi-Fi/hotspot e o isolamento de clientes do roteador. O toque NFC e o envio pela rede são etapas diferentes.

## Estrutura

- `MainActivity.java`: interface, seleção, envio, confirmação e MediaStore.
- `TouchHceService.java` / `TouchSession.java`: comandos NFC, sessão e evento de toque.
- `RippleView.java`: animação nativa.
- `Wire.java`: streaming limitado, hash e identificação de sessão.
- `app/src/test/.../WireTest.java`: testes JVM do protocolo.
- `.github/workflows/android.yml`: compilação e lint no GitHub Actions.

## Referências técnicas

- https://developer.android.com/develop/connectivity/nfc/hce
- https://developer.android.com/reference/android/nfc/NfcAdapter#enableReaderMode(android.app.Activity,%20android.nfc.NfcAdapter.ReaderCallback,%20int,%20android.os.Bundle)
- https://developer.android.com/training/data-storage/shared/media

O AID `F0544F55434801` é exclusivo deste protótipo para testes; antes de distribuição ampla, revisar registro e possíveis conflitos conforme a documentação HCE.
