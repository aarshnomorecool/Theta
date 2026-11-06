package com.seasaw.theta.core.config

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Serves [ConfigDefaults] alone when Firebase is unconfigured, as it is on builds without a google-services.json. */
@Singleton
internal class FirebaseConfigSource @Inject constructor(@ApplicationContext private val context: Context) :
    ConfigSource {
    private val remote: FirebaseRemoteConfig? by lazy {
        if (FirebaseApp.getApps(context).isEmpty()) {
            null
        } else {
            FirebaseRemoteConfig.getInstance().apply {
                setConfigSettingsAsync(
                    FirebaseRemoteConfigSettings.Builder()
                        .setMinimumFetchIntervalInSeconds(MIN_FETCH_INTERVAL_SECONDS)
                        .build(),
                )
                setDefaultsAsync(ConfigDefaults.all)
            }
        }
    }

    override fun boolean(key: String): Boolean =
        remote?.getBoolean(key) ?: (ConfigDefaults.all[key] as? Boolean ?: false)

    override fun long(key: String): Long = remote?.getLong(key) ?: (ConfigDefaults.all[key] as? Long ?: 0L)

    override fun string(key: String): String = remote?.getString(key) ?: (ConfigDefaults.all[key] as? String).orEmpty()

    override suspend fun fetchAndActivate() {
        val config = remote ?: return
        suspendCancellableCoroutine { continuation ->
            // Success and failure are treated alike: on failure the last activated values or defaults stay in force.
            config.fetchAndActivate().addOnCompleteListener { continuation.resume(Unit) }
        }
    }

    private companion object {
        const val MIN_FETCH_INTERVAL_SECONDS = 6 * 60 * 60L
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ConfigModule {
    @Binds
    abstract fun appConfig(implementation: RemoteAppConfig): AppConfig

    @Binds
    abstract fun configSource(implementation: FirebaseConfigSource): ConfigSource
}
