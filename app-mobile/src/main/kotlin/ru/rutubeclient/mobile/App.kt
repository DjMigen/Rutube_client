package ru.rutubeclient.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import ru.rutubeclient.core.database.AppDatabase
import ru.rutubeclient.core.database.LibraryRepository
import ru.rutubeclient.core.network.HttpRuTubeApi
import ru.rutubeclient.core.network.RuTubeApi
import ru.rutubeclient.core.player.PlaybackEnvironment
import ru.rutubeclient.mobile.download.DownloadsManager
import ru.rutubeclient.mobile.settings.SettingsRepository

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Lets the playback service route downloaded videos to the offline cache.
        PlaybackEnvironment.offlineProvider = container.downloadsManager
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                getString(R.string.channel_downloads),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
        const val NOTIFICATION_DOWNLOADS = 1001

        fun container(context: Context): AppContainer =
            (context.applicationContext as App).container
    }
}

/** Manual dependency injection — the whole graph is tiny, no framework needed. */
class AppContainer(context: Context) {
    val api = HttpRuTubeApi()
    val db = AppDatabase.get(context)
    val repository = LibraryRepository(db)
    val downloadsManager = DownloadsManager(context.applicationContext, api, repository)
    val settings = SettingsRepository(context.applicationContext)
}
