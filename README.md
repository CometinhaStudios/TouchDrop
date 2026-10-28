# Cyber90s Dual 0.1

Protótipo Android em paisagem para executar dois jogos em paralelo, lado a lado, com seleção de ROM independente e mapeamento de um controle Bluetooth/USB por lado.

## Como funciona

- Cada metade da tela é um WebView separado, com seu próprio contexto JavaScript e instância EmulatorJS.
- Cada lado seleciona o sistema e a ROM independentemente.
- O botão **Mapear controle** aguarda um botão físico e associa aquele controle ao emulador selecionado.
- O mapeamento é guardado no aparelho por descritor do controle.
- Os arquivos de ROM são selecionados localmente pelo seletor do Android; nenhuma ROM comercial é incluída.
- O motor EmulatorJS é carregado do CDN oficial, então é necessária conexão à internet ao iniciar os jogos.

## Gerar o APK

O workflow `.github/workflows/build-apk.yml` compila o APK de debug no GitHub Actions. O artefato é `Cyber90s-Dual-debug-apk`.

## Limitações do protótipo

Esta é uma primeira versão para testar o conceito, não uma versão validada em aparelhos. O suporte a sistemas depende dos cores disponibilizados pelo EmulatorJS. O desempenho com dois jogos simultâneos depende do celular, e o áudio dos dois jogos pode tocar ao mesmo tempo. A compatibilidade de cada controle deve ser testada no aparelho. Use apenas ROMs que você tenha direito de utilizar.
