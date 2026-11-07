package com.seasaw.theta.feature.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.seasaw.theta.core.ads.AdPlacement
import com.seasaw.theta.core.ads.AdSlot
import com.seasaw.theta.core.common.url.UrlUtils
import com.seasaw.theta.core.data.BookmarksRepository
import com.seasaw.theta.core.ui.EmptyState
import com.seasaw.theta.core.ui.R as CoreUiR
import com.seasaw.theta.core.ui.SiteAvatar
import com.seasaw.theta.core.ui.ThemePreviews
import com.seasaw.theta.core.ui.ThetaIcons
import com.seasaw.theta.core.ui.ThetaTheme
import com.seasaw.theta.core.ui.ThetaTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookmarkRow(val url: String, val title: String)

data class BookmarksUiState(val rows: List<BookmarkRow> = emptyList())

sealed interface BookmarksUiEvent {
    data class Remove(val url: String) : BookmarksUiEvent
}

@HiltViewModel
class BookmarksViewModel @Inject constructor(private val bookmarks: BookmarksRepository) : ViewModel() {
    val state: StateFlow<BookmarksUiState> = bookmarks.observeAll()
        .map { rows -> BookmarksUiState(rows.map { BookmarkRow(it.url, it.title) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BookmarksUiState())

    fun onEvent(event: BookmarksUiEvent) {
        when (event) {
            is BookmarksUiEvent.Remove -> viewModelScope.launch { bookmarks.remove(event.url) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
fun BookmarksRoute(onOpenUrl: (String) -> Unit, onBack: () -> Unit, viewModel: BookmarksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BookmarksScreen(state, onOpenUrl, onBack, viewModel::onEvent) { AdSlot(AdPlacement.BOOKMARKS) }
}

@Composable
internal fun BookmarksScreen(
    state: BookmarksUiState,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    onEvent: (BookmarksUiEvent) -> Unit,
    adSlot: @Composable () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding()) {
            ThetaTopBar(stringResource(R.string.bookmarks_title), onBack)
            if (state.rows.isEmpty()) {
                EmptyState(
                    ThetaIcons.BookmarkBorder,
                    stringResource(R.string.bookmarks_empty_title),
                    stringResource(R.string.bookmarks_empty_message),
                    Modifier.weight(1f),
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(state.rows, key = { it.url }) { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(row.url) }
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SiteAvatar(UrlUtils.hostOf(row.url)?.removePrefix("www.") ?: row.title)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(
                                    row.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    UrlUtils.displayUrl(row.url),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onEvent(BookmarksUiEvent.Remove(row.url)) }) {
                                Icon(Icons.Filled.Close, stringResource(CoreUiR.string.action_delete))
                            }
                        }
                    }
                }
            }
            adSlot()
        }
    }
}

@ThemePreviews
@Composable
private fun BookmarksPreview() {
    ThetaTheme {
        BookmarksScreen(
            BookmarksUiState(listOf(BookmarkRow("https://trains.example.in", "Train running status"))),
            {},
            {},
            {},
        ) { AdSlot(AdPlacement.BOOKMARKS) }
    }
}
