package io.github.yozh150991.todo.android

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.yozh150991.todo.ui.App
import io.github.yozh150991.todo.ui.FeatureFlags

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Debug builds (applicationId *.debug) show unfinished features; release builds do not.
        val development = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        setContent { App(FeatureFlags.of(development)) }
    }
}
