package br.com.tveseriesonline

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RefreshScheduler {
    private const val PERIODIC_REFRESH = "six-hour-content-refresh"
    private const val MANUAL_REFRESH = "manual-content-refresh"

    fun scheduleAutomatic(context: Context) {
        val request = PeriodicWorkRequestBuilder<ContentRefreshWorker>(
            6, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_REFRESH,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ContentRefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_REFRESH,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
