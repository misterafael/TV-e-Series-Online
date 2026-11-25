package br.com.tveseriesonline

import android.content.Context
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages multiple authorized M3U/M3U8 sources.
 * Only the latest successfully parsed result of each source is included in a
 * refresh. A failed source is excluded; its previous cached content is deleted.
 */
object MultiSourceRepository {
    private const val PREFS = "multi_source_settings"
    private const val SOURCES_KEY = "source_urls"
    private const val CACHE_PREFIX = "cache_"
    private const val MAX_BYTES = 12 * 1024 * 1024

    fun getSources(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SOURCES_KEY, null)
            ?.let { runCatching { JSONArray(it) }.getOrNull() }
            ?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }
            .orEmpty()

    fun addSource(context: Context, value: String) {
        val source = value.trim()
        require(isAllowedUrl(source)) { "Informe um endereço HTTP ou HTTPS válido." }
        val sources = (getSources(context) + source).distinct()
        saveSources(context, sources)
    }

    fun removeSource(context: Context, value: String) {
        saveSources(context, getSources(context).filterNot { it == value })
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(cacheKey(value)).apply()
    }

    private fun saveSources(context: Context, sources: List<String>) {
        val array = JSONArray()
        sources.forEach(array::put)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(SOURCES_KEY, array.toString()).apply()
    }

    suspend fun refreshAll(context: Context): RefreshSummary {
        val sources = getSources(context)
        require(sources.isNotEmpty()) { "Adicione ao menos uma fonte M3U/M3U8." }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = LinkedHashMap<String, Channel>()
        var updated = 0
        val errors = mutableListOf<String>()

        for ((index, source) in sources.withIndex()) {
            val key = cacheKey(source)
            try {
                val parsed = M3uParser.parse(download(source))
                require(parsed.isNotEmpty()) { "A fonte não contém canais válidos." }
                parsed.forEach { all[it.streamUrl] = it }
                // Cache only the last successful raw playlist for diagnostics/future use.
                prefs.edit().putString(key, "valid").apply()
                updated++
            } catch (error: Exception) {
                // Explicit cleanup: do not reuse or keep data from a failed source.
                prefs.edit().remove(key).apply()
                errors += "Fonte ${index + 1} excluída desta atualização: ${error.message ?: "conteúdo inválido"}"
            }
        }

        // Do not erase the entire catalog if every source is unavailable or invalid.
        require(all.isNotEmpty()) {
            errors.firstOrNull() ?: "Nenhuma fonte retornou conteúdo válido; o catálogo atual foi mantido."
        }

        ChannelDatabase(context).use { it.replaceFromPlaylist(all.values.toList()) }
        return RefreshSummary(all.size, updated, sources.size, errors)
    }

    private fun cacheKey(source: String): String =
        CACHE_PREFIX + source.hashCode().toUInt().toString(16)

    private fun download(source: String): String {
        require(isAllowedUrl(source)) { "A fonte precisa usar HTTP ou HTTPS." }
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/x-mpegURL, application/vnd.apple.mpegurl, text/plain, */*")
            setRequestProperty("User-Agent", "TVeSeriesOnline/1.0 Android")
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_BYTES) { "Playlist excede 12 MB." }
                    output.write(buffer, 0, read)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    private fun isAllowedUrl(value: String): Boolean = runCatching {
        val url = URL(value)
        (url.protocol.equals("http", true) || url.protocol.equals("https", true)) && url.host.isNotBlank()
    }.getOrDefault(false)
}

data class RefreshSummary(
    val channelCount: Int,
    val updatedSources: Int,
    val totalSources: Int,
    val errors: List<String>
)
