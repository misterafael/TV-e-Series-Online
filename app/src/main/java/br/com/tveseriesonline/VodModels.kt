package br.com.tveseriesonline

data class XtreamAccount(val server: String, val username: String, val password: String) {
    val id: String get() = "$server|$username"
}
data class VodMovie(val id: String, val name: String, val poster: String?, val plot: String?, val category: String, val streamUrl: String, val extension: String?)
data class VodSeries(val id: String, val name: String, val poster: String?, val plot: String?, val category: String, val year: String?)
data class VodEpisode(val id: String, val seriesId: String, val season: Int, val episode: Int, val title: String, val streamUrl: String, val extension: String?)
data class VodRefreshSummary(val movies: Int, val series: Int, val liveChannels: Int, val errors: List<String>)
