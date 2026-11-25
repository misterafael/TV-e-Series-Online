# TV e Séries Online

Aplicativo Android para reunir fontes autorizadas de TV ao vivo, filmes e séries.

## Fontes compatíveis nesta versão

- **M3U/M3U8 por URL HTTP/HTTPS:** cadastro de várias playlists e importação de canais ao vivo.
- **Xtream Codes compatível:** cadastro de várias contas com servidor, usuário e senha; importação de TV ao vivo, filmes, séries, temporadas e episódios.

Não existe um formato universal para todos os fornecedores. APIs privadas, formatos proprietários ou criptografados exigem documentação e autorização do fornecedor. O aplicativo não contorna DRM, criptografia, autenticação ou bloqueios regionais. Importação de arquivos locais e APIs proprietárias não documentadas ainda não estão incluídas.

## Funcionalidades

- Interface em Kotlin + Jetpack Compose com áreas de TV ao vivo, filmes e séries.
- Busca por nome e categoria.
- Capas e sinopses quando fornecidas pela fonte.
- Carregamento de temporadas e episódios pelo endpoint de detalhes da série.
- Player Media3 para canais, filmes e episódios, com suporte aos módulos Progressive, HLS, DASH e SmoothStreaming; a reprodução depende dos formatos, codecs e permissões suportados pelo aparelho e pela fonte.
- Várias playlists M3U/M3U8 e várias contas Xtream cadastradas simultaneamente.
- Atualização manual e atualização automática solicitada ao Android a cada 6 horas.
- Atualização manual e automática usam o mesmo fluxo.
- Quando uma fonte falha, ela não é misturada ao catálogo atualizado. Se outra fonte válida atualizar, o conteúdo antigo da fonte que falhou é removido. Se todas as fontes falharem, o catálogo anterior é preservado para evitar perda por indisponibilidade geral.
- Banco SQLite local para os catálogos e favoritos de canais.

## Compilação

1. Abra o repositório no Android Studio.
2. Sincronize o Gradle.
3. Execute a configuração debug ou use o workflow Android build em GitHub Actions.
4. O APK debug é publicado como artifact tv-e-series-online-debug.

## Testes recomendados no aparelho

1. Cadastre uma playlist M3U/M3U8 autorizada e uma conta Xtream de teste.
2. Toque em Atualizar todas as fontes.
3. Confira TV ao vivo, filmes, séries, capas e sinopses.
4. Abra uma série e confira o carregamento de temporadas e episódios.
5. Teste a reprodução de um canal, um filme e um episódio.
6. Teste uma fonte indisponível junto com outra fonte válida e confirme a limpeza do conteúdo da fonte com erro.
7. Teste a atualização automática após o intervalo solicitado pelo Android.

O WorkManager solicita a execução a cada 6 horas, mas o Android pode atrasá-la por economia de bateria, Doze ou restrições do fabricante.

## Direitos e privacidade

Use somente fontes, credenciais e transmissões que você tenha autorização para acessar. As credenciais Xtream são necessárias para consultar a API do provedor; não compartilhe o APK com contas de terceiros nem publique credenciais em issues ou logs.
