package io.github.yozh150991.todo.core.database

/**
 * Minimal file operations needed around the database file (snapshot, restore, markers).
 * Android and JVM share the same java.nio implementation. Web keeps its database in OPFS through
 * the worker (ADR-004) and has no file-level snapshot yet (spec §3.4, gate of phase Ф7).
 */
internal expect object PlatformFiles {
    fun exists(path: String): Boolean

    /** Size in bytes, or 0 when the file does not exist. */
    fun size(path: String): Long

    /** Deletes the file if it exists. */
    fun delete(path: String)

    /** Atomic rename within one directory, replacing the target. */
    fun moveAtomic(from: String, to: String)

    /** Copies [from] over [to], replacing it. */
    fun copy(from: String, to: String)

    /** Free bytes usable by this process on the volume that holds [path]. */
    fun usableBytes(path: String): Long

    fun sha256(path: String): String

    fun writeText(path: String, text: String)

    fun readText(path: String): String

    /** Absolute paths of the files in the directory that contains [path]. */
    fun siblings(path: String): List<String>
}
