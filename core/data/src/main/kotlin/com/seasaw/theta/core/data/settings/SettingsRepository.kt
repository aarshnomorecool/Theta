package com.seasaw.theta.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.seasaw.theta.core.common.ApplicationScope
import com.seasaw.theta.core.common.url.SearchEngine
import com.seasaw.theta.core.data.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val settings: Flow<UserSettings> = store.data
        // An unreadable settings file must not take the browser down. Defaults are a usable fallback.
        .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
        .map(::toUserSettings)

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME_MODE] = mode.id }
    }
    suspend fun setLanguage(language: AppLanguage) {
        store.edit { it[Keys.LANGUAGE] = language.id }
    }
    suspend fun setSearchEngine(engine: SearchEngine) {
        store.edit { it[Keys.SEARCH_ENGINE] = engine.id }
    }
    suspend fun setAdBlockEnabled(enabled: Boolean) {
        store.edit { it[Keys.ADBLOCK_ENABLED] = enabled }
    }
    suspend fun setLiteMode(enabled: Boolean) {
        store.edit { it[Keys.LITE_MODE] = enabled }
    }
    suspend fun setClearOnExit(enabled: Boolean) {
        store.edit { it[Keys.CLEAR_ON_EXIT] = enabled }
    }
    suspend fun setDiagnosticsConsent(enabled: Boolean) {
        store.edit { it[Keys.DIAGNOSTICS_CONSENT] = enabled }
    }
    suspend fun setHistoryGroupedBySite(grouped: Boolean) {
        store.edit { it[Keys.HISTORY_GROUPED] = grouped }
    }

    suspend fun setFlag(flag: SettingFlag, enabled: Boolean) {
        store.edit { it[flag.key] = enabled }
    }

    suspend fun markFirstSessionCompleted() {
        store.edit { it[Keys.FIRST_SESSION_COMPLETED] = true }
    }

    suspend fun setTextZoomPercent(percent: Int) {
        store.edit {
            it[Keys.TEXT_ZOOM] = percent.coerceIn(UserSettings.MIN_TEXT_ZOOM, UserSettings.MAX_TEXT_ZOOM)
        }
    }

    suspend fun acceptPrivacyNotice(diagnosticsConsent: Boolean) {
        store.edit {
            it[Keys.PRIVACY_NOTICE_ACCEPTED] = true
            it[Keys.DIAGNOSTICS_CONSENT] = diagnosticsConsent
        }
    }

    /**
     * Resets every preference except the privacy answer and first-session marker. Keeping them means "delete all my
     * data" does not re-prompt for consent or qualify the next launch as a first session for the ads rules.
     */
    suspend fun resetToDefaults() {
        store.edit { preferences ->
            val accepted = preferences[Keys.PRIVACY_NOTICE_ACCEPTED]
            val consent = preferences[Keys.DIAGNOSTICS_CONSENT]
            val firstSessionDone = preferences[Keys.FIRST_SESSION_COMPLETED]
            preferences.clear()
            accepted?.let { preferences[Keys.PRIVACY_NOTICE_ACCEPTED] = it }
            consent?.let { preferences[Keys.DIAGNOSTICS_CONSENT] = it }
            firstSessionDone?.let { preferences[Keys.FIRST_SESSION_COMPLETED] = it }
        }
    }

    private fun toUserSettings(preferences: Preferences): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            themeMode = ThemeMode.fromId(preferences[Keys.THEME_MODE]),
            language = AppLanguage.fromId(preferences[Keys.LANGUAGE]),
            searchEngine = SearchEngine.fromId(preferences[Keys.SEARCH_ENGINE]),
            adBlockEnabled = preferences[Keys.ADBLOCK_ENABLED] ?: defaults.adBlockEnabled,
            liteMode = preferences[Keys.LITE_MODE] ?: defaults.liteMode,
            clearOnExit = preferences[Keys.CLEAR_ON_EXIT] ?: defaults.clearOnExit,
            textZoomPercent = preferences[Keys.TEXT_ZOOM] ?: defaults.textZoomPercent,
            privacyNoticeAccepted = preferences[Keys.PRIVACY_NOTICE_ACCEPTED] ?: defaults.privacyNoticeAccepted,
            diagnosticsConsent = preferences[Keys.DIAGNOSTICS_CONSENT] ?: defaults.diagnosticsConsent,
            firstSessionCompleted = preferences[Keys.FIRST_SESSION_COMPLETED] ?: defaults.firstSessionCompleted,
            historyGroupedBySite = preferences[Keys.HISTORY_GROUPED] ?: defaults.historyGroupedBySite,
            ntpBackgroundPhotos = preferences.flag(SettingFlag.NTP_PHOTOS),
            ntpShowPrivacyStats = preferences.flag(SettingFlag.NTP_STATS),
            ntpShowRecentSites = preferences.flag(SettingFlag.NTP_RECENT),
            tabPreviews = preferences.flag(SettingFlag.TAB_PREVIEWS),
            blockAutoplay = preferences.flag(SettingFlag.BLOCK_AUTOPLAY),
            highContrast = preferences.flag(SettingFlag.HIGH_CONTRAST),
            showZoomButtons = preferences.flag(SettingFlag.ZOOM_BUTTONS),
        )
    }

    private fun Preferences.flag(flag: SettingFlag): Boolean = this[flag.key] ?: flag.default

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
        val LITE_MODE = booleanPreferencesKey("lite_mode")
        val CLEAR_ON_EXIT = booleanPreferencesKey("clear_on_exit")
        val TEXT_ZOOM = intPreferencesKey("text_zoom")
        val PRIVACY_NOTICE_ACCEPTED = booleanPreferencesKey("privacy_notice_accepted")
        val DIAGNOSTICS_CONSENT = booleanPreferencesKey("diagnostics_consent")
        val FIRST_SESSION_COMPLETED = booleanPreferencesKey("first_session_completed")
        val HISTORY_GROUPED = booleanPreferencesKey("history_grouped_by_site")
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object SettingsModule {
    @Provides
    @Singleton
    fun dataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("settings") },
    )

    @Provides
    fun clock(): Clock = Clock { System.currentTimeMillis() }
}
