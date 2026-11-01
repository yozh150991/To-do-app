package io.github.yozh150991.todo.android

import android.app.Application
import io.github.yozh150991.todo.core.database.LocalStore
import io.github.yozh150991.todo.core.database.createAppDatabase
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
                    single { LocalStore(createAppDatabase(get())) }
                    single { TaskRepository(get()) }
                },
                uiModule,
            )
        }
    }
}
