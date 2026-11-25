package br.com.tveseriesonline

import java.net.URL
import java.util.Locale

/**
 * Classifica formatos de fontes sem tentar contornar autenticação, DRM ou controles do provedor.
 * A detecção é indicativa: a validação definitiva acontece ao interpretar a resposta.
 */
enum class SourceFormat {
    M3U, M3U8, XMLTV, XTREAM_API, JSON_API, UNKNOWN
}

data class DetectedSource(
    val format: SourceFormat,
    val normalizedUrl: String?,
    val explanation: String
)

object SourceFormatDetector {
    fun fromUrl(value: String): DetectedSource {
        val raw = value.trim()
        val url = runCatching { URL(raw) }.getOrNull()
            ?: return DetectedSource(SourceFormat.UNKNOWN, null, "Endereço inválido.")
        if (!url.protocol.equals("http", true) && !url.protocol.equals("https", true)) {
            return DetectedSource(SourceFormat.UNKNOWN, null, "Somente HTTP e HTTPS são aceitos.")
        }
        val path = url.path.lowercase(Locale.ROOT)
        val query = url.query.orEmpty().lowercase(Locale.ROOT)
        val format = when {
            path.endsWith(".m3u8") -> SourceFormat.M3U8
            path.endsWith(".m3u") -> SourceFormat.M3U
            path.endsWith(".xml") || path.endsWith(".xmltv") || path.endsWith(".gz") -> SourceFormat.XMLTV
            path.endsWith("/player_api.php") || query.contains("action=get_") -> SourceFormat.XTREAM_API
            path.endsWith(".json") -> SourceFormat.JSON_API
            else -> SourceFormat.UNKNOWN
        }
        val message = when (format) {
            SourceFormat.M3U, SourceFormat.M3U8 -> "Playlist M3U detectada pelo endereço; o conteúdo será validado no download."
            SourceFormat.XMLTV -> "Possível guia XMLTV; confirme que o arquivo contém XMLTV válido."
            SourceFormat.XTREAM_API -> "Possível API Xtream; requer credenciais e autorização do provedor."
            SourceFormat.JSON_API -> "Endpoint JSON identificado; requer adaptador documentado para importação."
            SourceFormat.UNKNOWN -> "Formato não identificado pelo endereço. Será necessário selecionar um tipo compatível."
        }
        return DetectedSource(format, raw, message)
    }

    fun fromContent(content: String): SourceFormat {
        val sample = content.trimStart().take(4096)
        return when {
            sample.startsWith("#EXTM3U", ignoreCase = true) -> SourceFormat.M3U
            sample.startsWith("<?xml", ignoreCase = true) &&
                (sample.contains("<tv", ignoreCase = true) || sample.contains("<programme", ignoreCase = true)) -> SourceFormat.XMLTV
            sample.startsWith("{") || sample.startsWith("[") -> SourceFormat.JSON_API
            else -> SourceFormat.UNKNOWN
        }
    }
}
