package com.seasaw.theta.core.webview

import android.webkit.PermissionRequest
import com.seasaw.theta.core.data.SitePermissionsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Permissions a site can request. [id] is the value persisted in Room, so it must not change. */
enum class SitePermission(val id: String) {
    CAMERA("camera"),
    MICROPHONE("microphone"),
    LOCATION("location"),
    ;

    companion object {
        fun fromWebResource(resource: String): SitePermission? = when (resource) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> CAMERA
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> MICROPHONE
            else -> null
        }
    }
}

enum class PermissionDecision { GRANTED, DENIED, ASK }

/** Applies the per-site rule: ask once per site, remember in Room, and never remember in incognito. */
@Singleton
class SitePermissionCoordinator @Inject constructor(private val repository: SitePermissionsRepository) {
    /**
     * A request for several permissions is only settled when all of them are. Incognito always asks: reading a regular
     * decision there would quietly link the private session to the user's normal browsing.
     */
    suspend fun lookup(host: String, requested: Set<SitePermission>, isIncognito: Boolean): PermissionDecision {
        if (isIncognito) return PermissionDecision.ASK
        val answers = requested.map { repository.decision(host, it.id) }
        return when {
            answers.any { it == false } -> PermissionDecision.DENIED
            answers.all { it == true } -> PermissionDecision.GRANTED
            else -> PermissionDecision.ASK
        }
    }

    suspend fun remember(host: String, requested: Set<SitePermission>, granted: Boolean, isIncognito: Boolean) {
        if (isIncognito) return
        requested.forEach { repository.remember(host, it.id, granted) }
    }
}
