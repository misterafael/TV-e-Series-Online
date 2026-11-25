package br.com.tveseriesonline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * A atualização periódica de seis em seis horas também procura novas listas
 * públicas, registra as válidas e só então atualiza o catálogo.
 */
class ContentRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val discovery = PublicSourceDiscovery.discoverAndRegister(applicationContext)
        val summary = ContentRefreshRepository.refreshAll(applicationContext)
        Result.success(androidx.work.workDataOf(
            "discovered_sources" to discovery.added,
            "checked_sources" to discovery.checked,
            "m3u_channels" to summary.m3uChannels,
            "xtream_channels" to summary.xtreamChannels,
            "movies" to summary.vodMovies,
            "series" to summary.vodSeries
        ))
    } catch (error: IllegalArgumentException) {
        Result.failure(androidx.work.workDataOf("error" to (error.message ?: "Fonte inválida")))
    } catch (error: Exception) {
        Result.retry()
    }
}
