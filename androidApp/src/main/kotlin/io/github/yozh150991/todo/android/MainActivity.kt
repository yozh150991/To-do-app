package io.github.yozh150991.todo.android

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.yozh150991.todo.ui.App
import io.github.yozh150991.todo.ui.FeatureFlags
import io.github.yozh150991.todo.ui.navigation.TaskLink

class MainActivity : ComponentActivity() {
    /** The task to show, from the intent that started or reached the activity (UX_SPEC §2.1 «Глибокі посилання»). */
    private var taskLink by mutableStateOf<TaskLink?>(null)
    private var links = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Debug builds (applicationId *.debug) show unfinished features; release builds do not.
        val development = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // After a rotation the navigation state is restored; the intent must not open the task again.
        if (savedInstanceState == null) taskLink = intent.taskLink()
        setContent { App(FeatureFlags.of(development), openTask = taskLink) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.taskLink()?.let { taskLink = it }
    }

    private fun Intent.taskLink(): TaskLink? {
        val id = getStringExtra(EXTRA_TASK_ID) ?: data?.taskIdFromUri() ?: return null
        return TaskLink(id, nonce = ++links)
    }

    /** `<applicationId>://task/<id>` — for tests (`adb shell am start -d …`, Maestro `openLink`). */
    private fun Uri.taskIdFromUri(): String? =
        if (scheme == packageName && host == "task") pathSegments.firstOrNull()?.takeIf { it.isNotBlank() } else null

    companion object {
        /** Extra with the ID of the task to show; reminder notifications (Ф1a PR 7) put it in their intent. */
        const val EXTRA_TASK_ID = "io.github.yozh150991.todo.extra.TASK_ID"

        /** An explicit intent that opens the task's details over the start list. */
        fun openTask(context: Context, taskId: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TASK_ID, taskId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
