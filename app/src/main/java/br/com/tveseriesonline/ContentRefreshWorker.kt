package br.com.tveseriesonline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Atualização automática usa exatamente o mesmo fluxo da atualização manual. */
class ContentRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val summary = ContentRefreshRepository.refreshAll(applicationContext)
        Result.success(androidx.work.workDataOf(
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
