package br.com.tveseriesonline

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PublicVodSummary(
    val items: Int,
    val checked: Int,
    val errors: List<String>
)

/**
 * Descobre vídeos no Internet Archive. Só aceita itens cuja licença declarada
 * pelo responsável pelo envio seja domínio público, CC0, CC BY ou CC BY-SA.
 * A licença é uma declaração do remetente e deve ser conferida na página do item.
 */
object PublicVodDiscovery {
    private const val BASE = "https://archive.org"
    private const val MAX_RESULTS_PER_CATEGORY = 12
    private val searches = listOf(
        "Filmes" to "mediatype:movies AND (subject:film OR subject:movie OR title:film OR title:movie) AND licenseurl:[* TO *]",
        "Séries" to "mediatype:movies AND (subject:television OR subject:series OR title:episode OR title:series) AND licenseurl:[* TO *]",
        "Animes" to "mediatype:movies AND (subject:anime OR title:anime OR subject:animation) AND licenseurl:[* TO *]"
    )

    fun refresh(context: Context): PublicVodSummary {
        val found = linkedMapOf<String, VodMovie>()
        val errors = mutableListOf<String>()
        var checked = 0
        for ((category, query) in searches) {
            try {
                val results = search(query)
                results.forEach { result ->
                    checked++
                    val identifier = result.optString("identifier")
                    if (identifier.isBlank() || found.containsKey(identifier)) return@forEach
                    try {
                        val item = readPlayableItem(identifier, category) ?: return@forEach
                        found[identifier] = item
                    } catch (error: Exception) {
                        errors += "$category ($identifier): ${error.message ?: "item indisponível"}"
                    }
                }
            } catch (error: Exception) {
                errors += "$category: ${error.message ?: "busca indisponível"}"
            }
        }
        if (found.isNotEmpty()) {
            VodStore(context).use { it.replacePublicMovies(found.values.toList()) }
        }
        return PublicVodSummary(found.size, checked, errors)
    }

    private fun search(query: String): List<JSONObject> {
        val params = "q=${enc(query)}&fl[]=identifier&fl[]=title&fl[]=licenseurl&rows=$MAX_RESULTS_PER_CATEGORY&output=json"
        val json = JSONObject(get("$BASE/advancedsearch.php?$params"))
        val docs = json.optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
        return (0 until docs.length()).mapNotNull { docs.optJSONObject(it) }
    }

    private fun readPlayableItem(identifier: String, category: String): VodMovie? {
        val json = JSONObject(get("$BASE/metadata/${enc(identifier)}"))
        val metadata = json.optJSONObject("metadata") ?: return null
        val license = metadata.optString("licenseurl").ifBlank { return null }
        if (!isAllowedLicense(license)) return null
        val files = json.optJSONArray("files") ?: return null
        val playable = (0 until files.length()).mapNotNull { files.optJSONObject(it) }
            .filter { file ->
                val name = file.optString("name").lowercase()
                val isVideo = name.endsWith(".mp4") || name.endsWith(".m4v") ||
                    name.endsWith(".webm") || name.endsWith(".ogv")
                isVideo && file.optString("private") != "true" &&
                    file.optString("source") != "metadata"
            }
            .maxByOrNull { it.optString("size").toLongOrNull() ?: 0L } ?: return null

        val fileName = playable.optString("name")
        val title = metadata.optString("title").ifBlank { identifier }
        val description = metadata.optString("description").take(1200).takeIf { it.isNotBlank() }
        val year = metadata.optString("year")
        val categoryLabel = if (year.isBlank()) category else "$category • $year"
        val streamUrl = "$BASE/download/${enc(identifier)}/${Uri.encode(fileName)}"
        return VodMovie(
            id = "ia|$identifier",
            name = title,
            poster = "$BASE/services/img/${enc(identifier)}",
            plot = description,
            category = categoryLabel,
            streamUrl = streamUrl,
            extension = fileName.substringAfterLast('.', "").ifBlank { null }
        )
    }

    private fun isAllowedLicense(value: String): Boolean {
        val license = value.lowercase()
        return license.contains("publicdomain/mark") ||
            license.contains("publicdomain/zero") ||
            license.contains("creativecommons.org/licenses/by/4.0") ||
            license.contains("creativecommons.org/licenses/by-sa/4.0") ||
            license.contains("creativecommons.org/licenses/by/3.0") ||
            license.contains("creativecommons.org/licenses/by-sa/3.0")
    }

    private fun get(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TVFilmeECinema/1.1")
            setRequestProperty("Accept", "application/json")
        }
        try {
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
