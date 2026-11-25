package br.com.tveseriesonline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** The scheduled and manual jobs use the same multi-source refresh routine. */
class ContentRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val summary = MultiSourceRepository.refreshAll(applicationContext)
        Result.success(androidx.work.workDataOf(
            "channel_count" to summary.channelCount,
            "updated_sources" to summary.updatedSources
        ))
    } catch (error: IllegalArgumentException) {
        Result.failure(androidx.work.workDataOf("error" to (error.message ?: "Fonte inválida")))
    } catch (error: Exception) {
        Result.retry()
    }
}
