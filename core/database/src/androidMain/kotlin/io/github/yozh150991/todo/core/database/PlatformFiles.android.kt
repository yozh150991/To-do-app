package io.github.yozh150991.todo.core.database

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal actual object PlatformFiles {
    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun size(path: String): Long = File(path).let { if (it.exists()) it.length() else 0L }

    actual fun delete(path: String) {
        Files.deleteIfExists(Paths.get(path))
    }

    actual fun moveAtomic(from: String, to: String) {
        Files.move(Paths.get(from), Paths.get(to), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    actual fun copy(from: String, to: String) {
        Files.copy(Paths.get(from), Paths.get(to), StandardCopyOption.REPLACE_EXISTING)
    }

    actual fun usableBytes(path: String): Long {
        val file = File(path).absoluteFile
        val dir = if (file.isDirectory) file else file.parentFile ?: file
        return dir.usableSpace
    }

    actual fun sha256(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(Paths.get(path)).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    actual fun writeText(path: String, text: String) {
        File(path).writeText(text, Charsets.UTF_8)
    }

    actual fun readText(path: String): String = File(path).readText(Charsets.UTF_8)

    actual fun siblings(path: String): List<String> =
        File(path).absoluteFile.parentFile?.listFiles()?.map { it.absolutePath }.orEmpty()
}
