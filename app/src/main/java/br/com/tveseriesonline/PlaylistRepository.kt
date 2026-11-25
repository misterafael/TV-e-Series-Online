package br.com.tveseriesonline

import android.content.Context
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object PlaylistRepository {
    private const val PREFS = "playlist_settings"
    private const val SOURCE_URL = "source_url"
    private const val MAX_PLAYLIST_BYTES = 12 * 1024 * 1024

    fun saveSource(context: Context, url: String) {
        require(isAllowedSourceUrl(url)) {
            "Informe um endereço HTTP ou HTTPS válido."
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SOURCE_URL, url.trim())
            .apply()
    }

    fun getSource(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SOURCE_URL, "")
            .orEmpty()

    fun loadChannels(context: Context): List<Channel> =
        ChannelDatabase(context).use { it.getChannels() }

    fun setFavorite(context: Context, url: String, favorite: Boolean) {
        ChannelDatabase(context).use { it.setFavorite(url, favorite) }
    }

    suspend fun refreshConfiguredSource(context: Context): Int {
        val source = getSource(context)
        require(source.isNotBlank()) { "Adicione primeiro o endereço da sua playlist M3U/M3U8." }
        val body = downloadPlaylist(source)
        val parsed = M3uParser.parse(body)
        ChannelDatabase(context).use { it.replaceFromPlaylist(parsed) }
        return parsed.size
    }

    private fun downloadPlaylist(source: String): String {
        require(isAllowedSourceUrl(source)) { "A fonte precisa usar HTTP ou HTTPS." }
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/x-mpegURL, application/vnd.apple.mpegurl, text/plain, */*")
            setRequestProperty("User-Agent", "TVeSeriesOnline/1.0 Android")
        }

        try {
            val status = connection.responseCode
            require(status in 200..299) { "A fonte respondeu com HTTP $status." }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_PLAYLIST_BYTES) {
                        "A playlist excede o limite de 12 MB nesta versão."
                    }
                    output.write(buffer, 0, read)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    private fun isAllowedSourceUrl(value: String): Boolean = runCatching {
        val url = URL(value.trim())
        (url.protocol.equals("http", true) || url.protocol.equals("https", true)) &&
            !url.host.isNullOrBlank()
    }.getOrDefault(false)
}
