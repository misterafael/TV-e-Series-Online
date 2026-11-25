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
        require(hasM3u || hasXtream) { "Cadastre ao menos uma fonte M3U/M3U8 ou conta Xtream Codes." }

        var m3uCount = 0
        var movies = 0
        var series = 0
        var xtreamLive = 0
        var m3uSuccess = false
        var xtreamSuccess = false

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
                movies = result.movies
                series = result.series
                xtreamLive = result.liveChannels
                errors += result.errors
                xtreamSuccess = true
            } catch (e: Exception) { errors += "Xtream: ${e.message ?: "falha"}" }
        }

        // A successful source family replaces stale content from the family that failed or was removed.
        if (xtreamSuccess && !m3uSuccess) ChannelDatabase(context).use { it.clearCatalog() }
        if (m3uSuccess && !xtreamSuccess) VodRepository.clearCatalog(context)

        require(m3uSuccess || xtreamSuccess) {
            errors.firstOrNull() ?: "Nenhuma fonte respondeu com conteúdo válido; o catálogo anterior foi preservado."
        }
        return FullRefreshSummary(m3uCount,movies,series,xtreamLive,errors)
    }
}
