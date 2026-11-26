package ru.rutubeclient.mobile.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import ru.rutubeclient.mobile.App
import ru.rutubeclient.mobile.MainActivity

/** Foreground service that performs the actual HLS downloads with a progress notification. */
@OptIn(UnstableApi::class)
class AppDownloadService : DownloadService(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) {

    private val notificationHelper by lazy {
        DownloadNotificationHelper(this, App.CHANNEL_DOWNLOADS)
    }

    override fun getDownloadManager(): DownloadManager =
        (application as App).container.downloadsManager.downloadManager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return notificationHelper.buildProgressNotification(
            this,
            App.NOTIFICATION_DOWNLOADS,
            contentIntent,
            App.CHANNEL_DOWNLOADS,
            downloads,
            notMetRequirements,
        )
    }

    private companion object {
        const val JOB_ID = 1
    }
}
