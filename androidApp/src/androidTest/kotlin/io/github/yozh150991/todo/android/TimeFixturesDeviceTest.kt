package io.github.yozh150991.todo.android

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yozh150991.todo.core.testing.TimeGoldenFixtures
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Golden time fixtures (spec §4.0.1, TIME-01..04, TIME-08) on the device's own java.time and tzdb. */
@RunWith(AndroidJUnit4::class)
class TimeFixturesDeviceTest {
    @Test
    fun goldenTimeFixtures() {
        val results = TimeGoldenFixtures.runAll()
        Log.i(EVIDENCE_TAG, "[time, Android API ${Build.VERSION.SDK_INT}, tzdb ${tzdbVersion()}]")
        results.forEach { Log.i(EVIDENCE_TAG, "[time, Android API ${Build.VERSION.SDK_INT}] $it") }
        assertTrue(results.filterNot { it.passed }.joinToString("\n"), results.all { it.passed })
    }

    private fun tzdbVersion(): String =
        runCatching { android.icu.util.TimeZone.getTZDataVersion() }.getOrDefault("?")

    private companion object {
        const val EVIDENCE_TAG = "TodoEvidence"
    }
}
