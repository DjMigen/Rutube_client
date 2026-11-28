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
import ru.rutubeclient.mobile.R

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
        // NB: the 2nd arg is the small-icon drawable and the 4th is a
        // user-facing content description. Passing the notification id / channel
        // id here previously crashed the process (BadForegroundServiceNotificationException).
        return notificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_download,
            contentIntent,
            getString(R.string.app_name),
            downloads,
            notMetRequirements,
        )
    }

    private companion object {
        const val JOB_ID = 1
    }
}
