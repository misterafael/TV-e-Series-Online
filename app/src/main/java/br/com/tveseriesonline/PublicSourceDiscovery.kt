package br.com.tveseriesonline

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

data class PublicSourceResult(val added: Int, val checked: Int, val errors: List<String>)

/**
 * Diretório inicial de playlists públicas mantido pelo projeto iptv-org.
 * Os links são públicos, mas a disponibilidade e os direitos de cada stream
 * podem variar; cada playlist é validada antes de ser cadastrada.
 */
object PublicSourceDiscovery {
    private val directory = listOf(
        "Brasil" to "https://iptv-org.github.io/iptv/countries/br.m3u",
        "Notícias" to "https://iptv-org.github.io/iptv/categories/news.m3u",
        "Educação" to "https://iptv-org.github.io/iptv/categories/education.m3u",
        "Religiosos" to "https://iptv-org.github.io/iptv/categories/religious.m3u",
        "Esportes" to "https://iptv-org.github.io/iptv/categories/sports.m3u"
    )

    suspend fun discoverAndRegister(context: Context): PublicSourceResult {
        val existing = MultiSourceRepository.getSources(context).toSet()
        var added = 0
        var checked = 0
        val errors = mutableListOf<String>()
        for ((label, address) in directory) {
            checked++
            try {
                val body = download(address)
                require(SourceFormatDetector.fromContent(body) == SourceFormat.M3U) {
                    "Resposta não é uma playlist M3U válida."
                }
                require(M3uParser.parse(body).isNotEmpty()) { "Playlist sem canais válidos." }
                if (address !in existing) {
                    MultiSourceRepository.addSource(context, address)
                    added++
                }
            } catch (e: Exception) {
                errors += "$label: ${e.message ?: "indisponível"}"
            }
        }
        return PublicSourceResult(added, checked, errors)
    }

    private fun download(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TVeSeriesOnline/1.0")
            setRequestProperty("Accept", "application/x-mpegURL, text/plain")
        }
        try {
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 12 * 1024 * 1024) { "Playlist excede 12 MB." }
                    output.write(buffer, 0, count)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }
}
