package br.com.tveseriesonline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodic refresh and manual refresh share PlaylistRepository.refreshConfiguredSource().
 * A failed download or invalid playlist never replaces the last known-good catalog.
 */
class ContentRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val count = PlaylistRepository.refreshConfiguredSource(applicationContext)
            Result.success(
                androidx.work.workDataOf("imported_count" to count)
            )
        } catch (error: IllegalArgumentException) {
            // Missing source or invalid playlist: retrying will not fix user input.
            Result.failure(
                androidx.work.workDataOf("error" to (error.message ?: "Fonte inválida"))
            )
        } catch (error: Exception) {
            // Network and temporary server failures can recover on a later attempt.
            Result.retry()
        }
    }
}
