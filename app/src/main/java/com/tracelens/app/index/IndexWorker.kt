package com.tracelens.app.index

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tracelens.app.TraceLensApp

/** Indexing di background. Resumable: dijalankan ulang akan melanjutkan gambar yang belum selesai. */
class IndexWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TraceLensApp
        val one = inputData.getLong(KEY_COLLECTION, -1L)
        val ids = if (one > 0) listOf(one) else app.container.db.collections().all().map { it.id }
        for (id in ids) app.container.indexer.syncAndProcess(id)
        return Result.success()
    }

    companion object {
        const val KEY_COLLECTION = "collectionId"
        private const val UNIQUE = "tracelens-index"

        /** [collectionId] null → semua koleksi. Antrean berurutan (tidak paralel) agar hemat RAM. */
        fun enqueue(context: Context, collectionId: Long?) {
            val req = OneTimeWorkRequestBuilder<IndexWorker>()
                .setInputData(Data.Builder().putLong(KEY_COLLECTION, collectionId ?: -1L).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
