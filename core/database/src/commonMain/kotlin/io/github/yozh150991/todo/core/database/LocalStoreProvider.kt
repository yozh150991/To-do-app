package io.github.yozh150991.todo.core.database

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Opens the workspace database once, off the caller's thread, and hands out the same [LocalStore].
 *
 * The mutex guarantees a single open (and therefore a single schema upgrade) per process; nothing
 * reaches the database before [get] returns. If opening fails — for example an upgrade was rolled
 * back — the exception propagates to the caller and the next [get] tries again.
 */
class LocalStoreProvider(private val open: suspend () -> LocalStore) {
    private val mutex = Mutex()
    private var store: LocalStore? = null

    suspend fun get(): LocalStore = mutex.withLock {
        store ?: open().also { store = it }
    }

    /** Closes the store if it was opened; used on application shutdown. */
    fun closeIfOpen() {
        store?.close()
        store = null
    }

    companion object {
        fun of(store: LocalStore) = LocalStoreProvider { store }
    }
}
