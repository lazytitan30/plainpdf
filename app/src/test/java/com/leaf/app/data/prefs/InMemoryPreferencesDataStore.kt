package com.leaf.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * DataStore backed by memory for unit tests. The real file-backed store relies on an
 * atomic rename that fails on Windows JVMs, and the repository logic under test does not
 * depend on serialisation anyway.
 */
class InMemoryPreferencesDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val data: Flow<Preferences> get() = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
}
