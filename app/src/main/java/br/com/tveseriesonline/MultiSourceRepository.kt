package br.com.tveseriesonline

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Stores multiple authorized M3U/M3U8 sources. A source's last valid result is
 * cached independently, so a temporary outage does not erase its channels.
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
            val cacheKey = CACHE_PREFIX + source.hashCode().toUInt().toString(16)
            try {
                val parsed = M3uParser.parse(download(source))
                val cache = JSONArray()
                parsed.forEach { channel ->
                    cache.put(JSONObject().apply {
                        put("url", channel.streamUrl)
                        put("name", channel.name)
                        put("group", channel.groupName)
                        put("logo", channel.logoUrl ?: JSONObject.NULL)
                        put("tvg", channel.tvgId ?: JSONObject.NULL)
                    })
                }
                prefs.edit().putString(cacheKey, cache.toString()).apply()
                parsed.forEach { all[it.streamUrl] = it }
                updated++
            } catch (error: Exception) {
                errors += "Fonte ${index + 1}: ${error.message ?: "falha na atualização"}"
                // Retain this source's last known-good snapshot.
                runCatching { JSONArray(prefs.getString(cacheKey, "[]")) }.getOrNull()?.let { cache ->
                    for (i in 0 until cache.length()) {
                        val item = cache.optJSONObject(i) ?: continue
                        val url = item.optString("url")
                        if (url.isNotBlank()) all[url] = Channel(
                            streamUrl = url,
                            name = item.optString("name", url),
                            groupName = item.optString("group", "Sem categoria"),
                            logoUrl = item.optString("logo").takeUnless { it == "null" || it.isBlank() },
                            tvgId = item.optString("tvg").takeUnless { it == "null" || it.isBlank() }
                        )
                    }
                }
            }
        }

        require(all.isNotEmpty()) {
            errors.firstOrNull() ?: "Nenhuma das fontes retornou canais válidos."
        }
        ChannelDatabase(context).use { it.replaceFromPlaylist(all.values.toList()) }
        return RefreshSummary(all.size, updated, sources.size, errors)
    }

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
