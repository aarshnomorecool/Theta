package com.seasaw.theta.core.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * The in-app language picker. Android 13 and up have system support. Older versions, which most of the target phones
 * run, need the choice applied by hand to each Activity's base context. That has to be read synchronously, before any
 * coroutine can run, which is why this uses plain SharedPreferences rather than DataStore.
 */
object AppLocales {
    private const val PREFS = "app_locale"
    private const val KEY_TAG = "language_tag"

    /** Pass a null [languageTag] to follow the system language. */
    fun apply(activity: Activity, languageTag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (languageTag ==
                null
            ) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(languageTag)
            }
            activity.getSystemService(LocaleManager::class.java).applicationLocales = locales
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_TAG, languageTag) }
            activity.recreate()
        }
    }

    /** Call from Activity.attachBaseContext. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(configuration)
    }
}
