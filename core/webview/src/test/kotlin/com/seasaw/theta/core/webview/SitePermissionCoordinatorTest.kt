package com.seasaw.theta.core.webview

import com.seasaw.theta.core.data.SitePermissionsRepository
import com.seasaw.theta.core.data.db.SitePermissionDao
import com.seasaw.theta.core.data.db.SitePermissionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePermissionCoordinatorTest {
    private class FakeDao : SitePermissionDao {
        val rows = HashMap<Pair<String, String>, Boolean>()
        override suspend fun decision(host: String, permission: String) = rows[host to permission]
        override suspend fun upsert(entry: SitePermissionEntity) {
            rows[entry.host to entry.permission] = entry.granted
        }

        override fun observeAll(): Flow<List<SitePermissionEntity>> = flowOf(emptyList())
        override suspend fun deleteForHost(host: String) = Unit
        override suspend fun deleteAll() = rows.clear()
    }

    private val dao = FakeDao()
    private val coordinator = SitePermissionCoordinator(SitePermissionsRepository(dao) { 0L })
    private val camera = setOf(SitePermission.CAMERA)

    @Test
    fun `unknown site is asked`() = runTest {
        assertEquals(PermissionDecision.ASK, coordinator.lookup("a.example", camera, isIncognito = false))
    }

    @Test
    fun `remembered answers are reused per site`() = runTest {
        coordinator.remember("a.example", camera, granted = true, isIncognito = false)
        assertEquals(PermissionDecision.GRANTED, coordinator.lookup("a.example", camera, isIncognito = false))
        assertEquals(PermissionDecision.ASK, coordinator.lookup("b.example", camera, isIncognito = false))
    }

    @Test
    fun `a partly answered request asks again`() = runTest {
        coordinator.remember("a.example", camera, granted = true, isIncognito = false)
        val both = setOf(SitePermission.CAMERA, SitePermission.MICROPHONE)
        assertEquals(PermissionDecision.ASK, coordinator.lookup("a.example", both, isIncognito = false))
    }

    @Test
    fun `any denial denies the whole request`() = runTest {
        coordinator.remember("a.example", camera, granted = true, isIncognito = false)
        coordinator.remember("a.example", setOf(SitePermission.MICROPHONE), granted = false, isIncognito = false)
        val both = setOf(SitePermission.CAMERA, SitePermission.MICROPHONE)
        assertEquals(PermissionDecision.DENIED, coordinator.lookup("a.example", both, isIncognito = false))
    }

    @Test
    fun `incognito writes nothing`() = runTest {
        coordinator.remember("a.example", camera, granted = true, isIncognito = true)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `incognito ignores what regular browsing remembered`() = runTest {
        coordinator.remember("a.example", camera, granted = true, isIncognito = false)
        assertEquals(PermissionDecision.ASK, coordinator.lookup("a.example", camera, isIncognito = true))
    }
}
