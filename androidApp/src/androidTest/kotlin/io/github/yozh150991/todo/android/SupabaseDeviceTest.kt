package io.github.yozh150991.todo.android

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yozh150991.todo.core.sync.SupabaseProbe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * supabase-kt modules on Android against a local Supabase stack on the emulator's host
 * (CI job "Supabase modules: Android"). Skipped unless the runner arguments supabaseUrl and
 * supabaseKey are given.
 */
@RunWith(AndroidJUnit4::class)
class SupabaseDeviceTest {
    @Test
    fun supabaseModules() = runBlocking<Unit> {
        val arguments = InstrumentationRegistry.getArguments()
        val url = arguments.getString("supabaseUrl")
        val key = arguments.getString("supabaseKey")
        assumeTrue("no local Supabase stack for this run", !url.isNullOrBlank() && !key.isNullOrBlank())

        val platform = "Android API ${Build.VERSION.SDK_INT}"
        Log.i(EVIDENCE_TAG, "[supabase, $platform] $url")
        // Each result is logged as soon as it is known: a hung run still leaves evidence in logcat.
        val checks = SupabaseProbe.run(url!!, key!!, platform) { Log.i(EVIDENCE_TAG, "[supabase, $platform] $it") }
        assertTrue(checks.filterNot { it.passed }.joinToString("\n"), checks.all { it.passed })
    }

    private companion object {
        const val EVIDENCE_TAG = "TodoEvidence"
    }
}
