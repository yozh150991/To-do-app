package io.github.yozh150991.todo.android

import android.app.Application
import android.os.Build
import android.util.Log
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.openAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
import io.github.yozh150991.todo.ui.about.AppInfo
import io.github.yozh150991.todo.ui.uiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class TodoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TodoApplication)
            modules(
                module {
                    // Opened lazily off the main thread; schema upgrades are snapshot-protected (FIX-01).
                    single {
                        LocalStoreProvider {
                            val result = openAppDatabase(this@TodoApplication, log = { Log.i("TodoDatabase", it) })
                            LocalStore(result.database)
                        }
                    }
                    single { TaskRepository(get<LocalStoreProvider>()) }
                    single { appInfo() }
                },
                uiModule,
            )
        }
    }

    /** Version and device for the "About" dialog (and bug reports). */
    private fun appInfo(): AppInfo {
        @Suppress("DEPRECATION") // the PackageInfoFlags overload is API 33+, minSdk is 26
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        return AppInfo(
            version = "${info.versionName} ($code)",
            platform = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                "${Build.MANUFACTURER} ${Build.MODEL}, ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}",
        )
    }
}
