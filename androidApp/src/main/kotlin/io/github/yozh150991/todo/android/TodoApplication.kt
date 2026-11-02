package io.github.yozh150991.todo.android

import android.app.Application
import android.util.Log
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.LocalStoreProvider
import io.github.yozh150991.todo.core.database.openAppDatabase
import io.github.yozh150991.todo.core.domain.TaskRepository
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
                },
                uiModule,
            )
        }
    }
}
