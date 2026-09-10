package com.leaf.app.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    private val store = InMemoryPreferencesDataStore()
    private val repo = SettingsRepository(store)

    @Test
    fun defaultsWhenNothingStored() = runTest {
        repo.settings.test {
            assertEquals(Settings(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun themeChangeIsObservedAndPersisted() = runTest {
        repo.settings.test {
            assertEquals(AppTheme.SYSTEM, awaitItem().theme)
            repo.setTheme(AppTheme.BLACK)
            assertEquals(AppTheme.BLACK, awaitItem().theme)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unknownEnumValueFallsBackToDefault() = runTest {
        store.edit { it[stringPreferencesKey("theme")] = "NEON" }
        repo.settings.test {
            assertEquals(AppTheme.SYSTEM, awaitItem().theme)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveTreeCanBeClearedBackToAlwaysAsk() = runTest {
        repo.settings.test {
            assertNull(awaitItem().defaultSaveTreeUri)
            repo.setDefaultSaveTreeUri("content://tree/primary%3ADocs")
            assertEquals("content://tree/primary%3ADocs", awaitItem().defaultSaveTreeUri)
            repo.setDefaultSaveTreeUri(null)
            assertNull(awaitItem().defaultSaveTreeUri)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun scanOcrDefaultsOnAndCanBeTurnedOff() = runTest {
        repo.settings.test {
            assertTrue(awaitItem().scanOcr)
            repo.setScanOcr(false)
            assertFalse(awaitItem().scanOcr)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun ocrLanguagesPersistAndBlankRestoresDefault() = runTest {
        repo.settings.test {
            assertEquals(Settings.defaultOcrLanguages(), awaitItem().ocrLanguages)
            repo.setOcrLanguages("eng+deu")
            assertEquals("eng+deu", awaitItem().ocrLanguages)
            repo.setOcrLanguages("  ")
            assertEquals(Settings.defaultOcrLanguages(), awaitItem().ocrLanguages)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun blankOutputPatternRestoresDefault() = runTest {
        repo.settings.test {
            awaitItem()
            repo.setOutputNamePattern("{op}-{name}")
            assertEquals("{op}-{name}", awaitItem().outputNamePattern)
            repo.setOutputNamePattern("   ")
            assertEquals(Settings.DEFAULT_OUTPUT_PATTERN, awaitItem().outputNamePattern)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun eitherGestureUnlocksAndOnlyPlayCanTakeThePackBack() = runTest {
        repo.settings.test {
            assertFalse(awaitItem().isSupporter)
            // The big tip unlocks on its own and survives a Play check that finds no pack.
            repo.setSupporterTipped(true)
            assertTrue(awaitItem().isSupporter)
            repo.setSupporterUnlockOwned(false)
            assertTrue(awaitItem().isSupporter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun packWithoutTipIsRevokedWhenPlayNoLongerHoldsIt() = runTest {
        repo.settings.test {
            awaitItem()
            repo.setSupporterUnlockOwned(true)
            val owned = awaitItem()
            assertTrue(owned.isSupporter)
            assertTrue(owned.supporterUnlockOwned)
            assertFalse(owned.supporterTipped)
            repo.setSupporterUnlockOwned(false)
            assertFalse(awaitItem().isSupporter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun legacySupporterFlagCountsAsThePackUntilRewritten() = runTest {
        store.edit { it[booleanPreferencesKey("is_supporter")] = true }
        repo.settings.test {
            val legacy = awaitItem()
            assertTrue(legacy.supporterUnlockOwned)
            assertTrue(legacy.isSupporter)
            repo.setSupporterUnlockOwned(false)
            assertFalse(awaitItem().isSupporter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun booleanTogglesRoundTrip() = runTest {
        repo.settings.test {
            val initial = awaitItem()
            assertTrue(initial.doubleTapZoom)
            assertFalse(initial.keepScreenOn)
            repo.setDoubleTapZoom(false)
            assertFalse(awaitItem().doubleTapZoom)
            repo.setKeepScreenOn(true)
            assertTrue(awaitItem().keepScreenOn)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
