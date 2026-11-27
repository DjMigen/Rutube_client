package ru.rutubeclient.tv

import android.app.Application
import android.content.Context
import ru.rutubeclient.core.database.AppDatabase
import ru.rutubeclient.core.database.LibraryRepository
import ru.rutubeclient.core.network.HttpRuTubeApi
import ru.rutubeclient.core.network.RuTubeApi

class TvApp : Application() {

    lateinit var container: TvAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = TvAppContainer(this)
    }

    companion object {
        fun container(context: Context): TvAppContainer =
            (context.applicationContext as TvApp).container
    }
}

class TvAppContainer(context: Context) {
    val api = HttpRuTubeApi()
    val db = AppDatabase.get(context)
    val repository = LibraryRepository(db)
}
