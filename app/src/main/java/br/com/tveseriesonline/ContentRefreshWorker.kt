package br.com.tveseriesonline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Single refresh pipeline shared by scheduled and manual refresh requests.
 * Source discovery, validation and persistence will be implemented in the data layer.
 */
class ContentRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Safe starter behavior: do not overwrite the user's data until a valid
        // authorized source has been fetched, parsed and validated.
        return Result.success()
    }
}
