package com.seasaw.theta.feature.adblock

import android.content.Context
import com.seasaw.theta.core.common.AppError
import com.seasaw.theta.core.common.AppResult
import com.seasaw.theta.core.common.IoDispatcher
import com.seasaw.theta.core.common.resultOf
import com.seasaw.theta.feature.adblock.engine.FilterSnapshot
import com.seasaw.theta.feature.adblock.engine.SnapshotIo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Where the compiled filters live. The newest download wins, and the snapshot bundled in assets guarantees that
 * blocking works on first launch, offline, and after any bad or incompatible download.
 */
@Singleton
class FilterSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    private val updatedFile = File(context.filesDir, "filters/updated.bin")

    suspend fun load(): AppResult<FilterSnapshot> = withContext(dispatcher) {
        loadUpdated() ?: resultOf(mapError = { AppError.Storage }) {
            context.assets.open(BUNDLED_ASSET).use(SnapshotIo::read)
        }
    }

    private fun loadUpdated(): AppResult<FilterSnapshot>? {
        if (!updatedFile.exists()) return null
        return try {
            AppResult.Success(updatedFile.inputStream().use(SnapshotIo::read))
        } catch (ignored: IOException) {
            // Most likely written by an older app version with a different format. The bundled one takes over.
            updatedFile.delete()
            null
        }
    }

    /** Writes to a temporary file and renames, so a crash mid-write can never leave a half-written snapshot. */
    suspend fun save(snapshot: FilterSnapshot): AppResult<Unit> = withContext(dispatcher) {
        resultOf(mapError = { AppError.Storage }) {
            val directory = checkNotNull(updatedFile.parentFile)
            directory.mkdirs()
            val temporary = File(directory, "updating.tmp")
            temporary.outputStream().use { SnapshotIo.write(snapshot, it) }
            if (!temporary.renameTo(updatedFile)) throw IOException("Could not replace the filter snapshot")
        }
    }

    private companion object {
        const val BUNDLED_ASSET = "filters.bin"
    }
}
