package br.com.tveseriesonline

import android.content.Context

data class FullRefreshSummary(
    val m3uChannels: Int,
    val vodMovies: Int,
    val vodSeries: Int,
    val xtreamChannels: Int,
    val errors: List<String>
)

object ContentRefreshRepository {
    suspend fun refreshAll(context: Context): FullRefreshSummary {
        val errors = mutableListOf<String>()
        var m3uCount = 0
        var movies = 0
        var series = 0
        var xtreamLive = 0
        val m3uSources = MultiSourceRepository.getSources(context)
        if (m3uSources.isNotEmpty()) {
            try { m3uCount = MultiSourceRepository.refreshAll(context).channelCount }
            catch (e: Exception) { errors += "M3U/M3U8: ${e.message ?: "falha"}" }
        }
        if (VodRepository.accounts(context).isNotEmpty()) {
            try {
                val result = VodRepository.refreshAll(context)
                movies = result.movies
                series = result.series
                xtreamLive = result.liveChannels
                errors += result.errors
            } catch (e: Exception) { errors += "Xtream: ${e.message ?: "falha"}" }
        }
        require(m3uCount + movies + series + xtreamLive > 0) {
            errors.firstOrNull() ?: "Cadastre ao menos uma fonte M3U/M3U8 ou conta Xtream Codes."
        }
        return FullRefreshSummary(m3uCount,movies,series,xtreamLive,errors)
    }
}
