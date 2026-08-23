package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrayscaleStoreTest {

    @Test
    fun `enabled defaults to false and roundtrips`() {
        val store = GrayscaleStore(FakeSharedPreferences())

        assertFalse(store.enabled)

        store.enabled = true
        assertTrue(store.enabled)

        store.enabled = false
        assertFalse(store.enabled)
    }

    @Test
    fun `colorApps defaults to empty`() {
        val store = GrayscaleStore(FakeSharedPreferences())

        assertEquals(emptySet<String>(), store.colorApps())
    }

    @Test
    fun `add remove and isColorApp`() {
        val store = GrayscaleStore(FakeSharedPreferences())
        val packageName = "com.example.app"

        assertFalse(store.isColorApp(packageName))

        store.addColorApp(packageName)
        assertTrue(store.isColorApp(packageName))
        assertEquals(setOf(packageName), store.colorApps())

        store.removeColorApp(packageName)
        assertFalse(store.isColorApp(packageName))
        assertEquals(emptySet<String>(), store.colorApps())
    }

    @Test
    fun `colorApps returns a copy`() {
        val store = GrayscaleStore(FakeSharedPreferences())
        store.addColorApp("com.example.app")

        val snapshot = store.colorApps().toMutableSet()
        snapshot.add("com.example.intruder")

        assertEquals(setOf("com.example.app"), store.colorApps())
    }

    @Test
    fun `hasSnapshot is false initially`() {
        val store = GrayscaleStore(FakeSharedPreferences())

        assertFalse(store.hasSnapshot)
    }

    @Test
    fun `saveSnapshot snapshotEnabled and snapshotMode roundtrip`() {
        val store = GrayscaleStore(FakeSharedPreferences())

        store.saveSnapshot(1, 11)

        assertTrue(store.hasSnapshot)
        assertEquals(1, store.snapshotEnabled())
        assertEquals(11, store.snapshotMode())
    }

    @Test
    fun `clearSnapshot resets hasSnapshot and defaults`() {
        val store = GrayscaleStore(FakeSharedPreferences())
        store.saveSnapshot(1, 11)

        store.clearSnapshot()

        assertFalse(store.hasSnapshot)
        assertEquals(0, store.snapshotEnabled())
        assertEquals(GrayscalePolicy.DALTONIZER_DISABLED, store.snapshotMode())
    }
}
