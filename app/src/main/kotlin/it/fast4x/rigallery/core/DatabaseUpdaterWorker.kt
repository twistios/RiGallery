package it.fast4x.rigallery.core

import android.content.Context
import androidx.compose.ui.util.fastMap
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.fast4x.rigallery.feature_node.data.data_source.InternalDatabase
import it.fast4x.rigallery.feature_node.domain.model.MediaVersion
import it.fast4x.rigallery.feature_node.domain.repository.MediaRepository
import it.fast4x.rigallery.feature_node.presentation.util.isMediaUpToDate
import it.fast4x.rigallery.feature_node.presentation.util.mediaStoreVersion
import it.fast4x.rigallery.feature_node.presentation.util.printDebug
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

fun WorkManager.updateDatabase() {
    val uniqueWork = OneTimeWorkRequestBuilder<DatabaseUpdaterWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build()
        )
        .build()

    enqueueUniqueWork("DatabaseUpdaterWorker", ExistingWorkPolicy.KEEP, uniqueWork)
}

@HiltWorker
class DatabaseUpdaterWorker @AssistedInject constructor(
    private val database: InternalDatabase,
    private val repository: MediaRepository,
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (database.isMediaUpToDate(appContext)) {
            printDebug("Database is up to date")
            return Result.success()
        }
        withContext(Dispatchers.IO) {
            val mediaVersion = appContext.mediaStoreVersion
            printDebug("Database is not up to date. Updating to version $mediaVersion")
            database.getMediaDao().setMediaVersion(MediaVersion(mediaVersion))
            val media = repository.getMedia().map { it.data ?: emptyList() }.firstOrNull()
            media?.let {
                database.getMediaDao().updateMedia(it)
                database.getClassifierDao().deleteDeclassifiedImages(it.fastMap { m -> m.id })
            }
            delay(5000)
        }

        return Result.success()
    }
}

