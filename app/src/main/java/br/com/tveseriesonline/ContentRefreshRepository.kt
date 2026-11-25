package br.com.tveseriesonline

import android.content.Context

data class FullRefreshSummary(
    val m3uChannels: Int, val vodMovies: Int, val vodSeries: Int,
    val xtreamChannels: Int, val errors: List<String>
)

object ContentRefreshRepository {
    suspend fun refreshAll(context: Context): FullRefreshSummary {
        val errors = mutableListOf<String>()
        val hasM3u = MultiSourceRepository.getSources(context).isNotEmpty()
        val hasXtream = VodRepository.accounts(context).isNotEmpty()

        var m3uCount = 0
        var xtreamLive = 0
        var m3uSuccess = false
        var xtreamSuccess = false
        var publicSuccess = false

        // O catálogo público de vídeo é independente de M3U e de conta Xtream.
        try {
            val publicResult = PublicVodDiscovery.refresh(context)
            publicSuccess = publicResult.items > 0
            errors += publicResult.errors
        } catch (e: Exception) {
            errors += "Catálogo público de filmes/séries/animes: ${e.message ?: "falha na busca"}"
        }

        if (hasM3u) {
            try {
                val m3uResult = MultiSourceRepository.refreshAll(context)
                m3uCount = m3uResult.channelCount
                errors += m3uResult.errors
                m3uSuccess = true
            } catch (e: Exception) { errors += "M3U/M3U8: ${e.message ?: "falha"}" }
        }
        if (hasXtream) {
            try {
                val result = VodRepository.refreshAll(context)
                xtreamLive = result.liveChannels
                errors += result.errors
                xtreamSuccess = true
            } catch (e: Exception) { errors += "Xtream: ${e.message ?: "falha"}" }
        }

        // Uma falha em Xtream não apaga os vídeos públicos já encontrados.
        if (xtreamSuccess && !m3uSuccess) ChannelDatabase(context).use { it.clearCatalog() }
        if (m3uSuccess && !xtreamSuccess) VodRepository.clearCatalog(context)

        val allMovies = VodRepository.movies(context)
        val allSeries = VodRepository.series(context)
        val publicCatalogExists = allMovies.any { it.id.startsWith("ia|") }
        require(m3uSuccess || xtreamSuccess || publicSuccess || publicCatalogExists) {
            errors.firstOrNull()
                ?: "Nenhuma fonte pública autorizada respondeu. O catálogo anterior foi preservado."
        }

        val movieCount = allMovies.count { !it.category.startsWith("Séries") && !it.category.startsWith("Animes") }
        val seriesCount = allSeries.size
        return FullRefreshSummary(m3uCount, movieCount, seriesCount, xtreamLive, errors)
    }
}
