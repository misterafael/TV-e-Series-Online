# TV e Séries Online

Aplicativo Android para organizar e reproduzir canais, filmes e séries a partir de fontes públicas ou autorizadas pelo usuário.

## Implementado nesta etapa

- Projeto Android em Kotlin + Jetpack Compose.
- Cadastro de uma URL de playlist M3U/M3U8 por HTTP/HTTPS.
- Parser de `#EXTINF`, nome, grupo/categoria, logo e ID EPG (`tvg-id`).
- Importação de canais e remoção de itens antigos não favoritos.
- Banco SQLite local para guardar os canais.
- Favoritos persistidos e preservados mesmo quando uma atualização temporariamente não traz aquele canal.
- Validação da lista antes de substituir o catálogo: uma URL inacessível ou uma playlist inválida não apaga os dados existentes.
- Atualização manual e worker automático chamam o mesmo método de importação.
- WorkManager configurado para solicitar atualização periódica a cada 6 horas.

## Limitações conhecidas

- O cadastro de fonte nesta versão aceita uma URL M3U/M3U8 HTTP/HTTPS; importação de arquivo local e múltiplas fontes ficam para a próxima etapa.
- A leitura do catálogo já está implementada, mas a reprodução de canais ainda não está conectada à interface.
- Filmes, séries, temporadas e episódios ainda não têm integração de catálogo.
- Formatos proprietários ou criptografados (por exemplo, variantes ENC) não são formatos universais: exigem especificação e autorização do fornecedor. O app não contorna DRM, criptografia ou autenticação.
- O Android pode atrasar tarefas em segundo plano por economia de bateria, Doze ou restrições do fabricante. “A cada 6 horas” é a periodicidade solicitada ao sistema, não uma garantia de execução exata no minuto.

## Como testar

1. Abra o repositório no Android Studio e sincronize o Gradle.
2. Execute o app em um dispositivo Android ou emulador.
3. Cole uma URL de playlist M3U/M3U8 que você tenha autorização para acessar.
4. Toque em **Salvar fonte** e depois em **Atualizar agora**.
5. Os canais importados aparecerão na lista. Toque na estrela para marcar ou desmarcar favoritos.
6. Atualize novamente e confira que os favoritos continuam salvos.

## Fontes e direitos

Use somente playlists e transmissões que você tenha autorização para acessar. O projeto não fornece listas piratas nem tenta contornar DRM, autenticação, criptografia ou bloqueios regionais. Metadados de filmes e séries não concedem direitos de transmissão.

## Tecnologias

- Kotlin
- Jetpack Compose
- SQLite (armazenamento local)
- AndroidX WorkManager
- AndroidX Media3 (dependência adicionada; integração de player pendente)
