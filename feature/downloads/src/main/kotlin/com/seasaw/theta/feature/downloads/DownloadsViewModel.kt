package com.seasaw.theta.feature.downloads

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seasaw.theta.core.common.IoDispatcher
import com.seasaw.theta.core.data.DownloadsRepository
import com.seasaw.theta.core.data.db.DownloadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

enum class DownloadStatus { RUNNING, PAUSED, COMPLETE, FAILED }

data class DownloadRow(
    val id: Long,
    val fileName: String,
    val mimeType: String?,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
) {
    /** Null when the server did not say how big the file is. */
    val fraction: Float? get() = if (bytesTotal > 0) (bytesDownloaded.toFloat() / bytesTotal).coerceIn(0f, 1f) else null
}

data class DownloadsUiState(val rows: List<DownloadRow> = emptyList())

sealed interface DownloadsUiEvent {
    data class Remove(val id: Long) : DownloadsUiEvent
    data class Cancel(val id: Long) : DownloadsUiEvent
}

/** The system DownloadManager owns the transfers. This reads their status for the list, while anything is running. */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val manager: DownloadManager get() = context.getSystemService(DownloadManager::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DownloadsUiState> = repository.observeAll()
        .transformLatest { rows -> emitAll(statusUpdates(rows)) }
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DownloadsUiState())

    private fun statusUpdates(rows: List<DownloadEntity>) = flow {
        while (true) {
            val current = rows.map(::toRow)
            emit(DownloadsUiState(current))
            if (current.none { it.status == DownloadStatus.RUNNING }) break
            delay(POLL_MILLIS)
        }
    }

    fun onEvent(event: DownloadsUiEvent) {
        when (event) {
            is DownloadsUiEvent.Remove -> viewModelScope.launch { repository.remove(event.id) }

            is DownloadsUiEvent.Cancel -> viewModelScope.launch {
                manager.remove(event.id)
                repository.remove(event.id)
            }
        }
    }

    /** Null when the file is gone, for example deleted from another app. */
    fun uriFor(id: Long): Uri? = manager.getUriForDownloadedFile(id)

    private fun toRow(entity: DownloadEntity): DownloadRow {
        val query = DownloadManager.Query().setFilterById(entity.downloadId)
        return manager.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) rowFrom(cursor, entity) else null
        } ?: DownloadRow(entity.downloadId, entity.fileName, entity.mimeType, DownloadStatus.FAILED, 0, 0)
    }

    private fun rowFrom(cursor: Cursor, entity: DownloadEntity): DownloadRow {
        fun column(name: String) = cursor.getColumnIndexOrThrow(name)
        return DownloadRow(
            id = entity.downloadId,
            fileName = entity.fileName,
            mimeType = entity.mimeType,
            status = statusOf(cursor.getInt(column(DownloadManager.COLUMN_STATUS))),
            bytesDownloaded = cursor.getLong(column(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
            bytesTotal = cursor.getLong(column(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
        )
    }

    internal companion object {
        private const val POLL_MILLIS = 1_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun statusOf(raw: Int): DownloadStatus = when (raw) {
            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETE
            DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
            DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
            else -> DownloadStatus.RUNNING
        }
    }
}
