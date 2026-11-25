# TV e Séries Online

Aplicativo Android para organizar e reproduzir canais de TV, filmes e séries a partir de fontes públicas ou autorizadas pelo usuário.

## Estado atual

Estrutura inicial Android criada em Kotlin + Jetpack Compose. A interface inicial contém áreas para TV ao vivo, filmes e séries e um botão manual **Atualizar agora**.

A rotina WorkManager já está registrada para solicitar uma execução periódica a cada 6 horas. O botão manual agenda uma execução única pelo mesmo worker. **A rotina de coleta/importação ainda é um esqueleto**: não consulta fontes nem altera o catálogo até que os conectores, validadores e armazenamento sejam implementados.

## Próximas etapas

1. Implementar importação e análise de playlists M3U/M3U8 autorizadas.
2. Criar armazenamento local para listas, categorias, metadados e favoritos.
3. Validar URLs e disponibilidade sem substituir a última lista válida quando houver falha.
4. Conectar a reprodução HLS/MP4 pelo Android Media3.
5. Implementar catálogo de filmes e séries com fontes de metadados apropriadas.
6. Testar atualização manual e agendada, inclusive em condições de rede instável.
7. Gerar e testar APK/AAB e preparar assinatura de lançamento.

## Fontes e direitos

O app não fornece listas piratas nem contorna DRM, autenticação, criptografia ou restrições geográficas. O usuário deve utilizar fontes e credenciais que tenha autorização para acessar. Formatos proprietários ou criptografados exigem documentação e acesso legítimo do fornecedor.

Metadados de filmes e séries não concedem direitos de transmissão. A disponibilidade depende das fontes e das licenças correspondentes.

## Abrir o projeto

1. Abra a pasta do repositório no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Selecione um dispositivo Android ou emulador.
4. Execute a configuração `app`.

O APK/AAB de distribuição e a assinatura de lançamento serão configurados após a implementação e os testes.

## Tecnologias

- Kotlin
- Jetpack Compose
- AndroidX WorkManager
- AndroidX Media3 (reprodução HLS/MP4)
