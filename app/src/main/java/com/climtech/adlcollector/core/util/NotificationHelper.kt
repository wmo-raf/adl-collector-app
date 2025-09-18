package com.climtech.adlcollector.core.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.climtech.adlcollector.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID_UPLOAD_STATUS = "upload_status"
        const val CHANNEL_ID_UPLOAD_FAILURES = "upload_failures"

        const val NOTIFICATION_ID_UPLOAD_PROGRESS = 2001
        const val NOTIFICATION_ID_UPLOAD_FAILURE = 2002
        const val NOTIFICATION_ID_UPLOAD_SUCCESS = 2003
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Upload status channel
            val statusChannel = NotificationChannel(
                CHANNEL_ID_UPLOAD_STATUS,
                "Upload Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress when uploading observations"
                setShowBadge(false)
            }

            // Upload failures channel
            val failuresChannel = NotificationChannel(
                CHANNEL_ID_UPLOAD_FAILURES,
                "Upload Failures",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when observation uploads repeatedly fail"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(listOf(statusChannel, failuresChannel))
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showUploadProgress(uploading: Int, total: Int) {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPLOAD_STATUS)
            .setSmallIcon(R.drawable.ic_notification_upload)
            .setContentTitle("Uploading Observations")
            .setContentText("$uploading of $total observations")
            .setProgress(total, uploading, false)
            .setOngoing(true)
            .setSilent(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_UPLOAD_PROGRESS, notification)
    }

    fun hideUploadProgress() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_UPLOAD_PROGRESS)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showUploadSuccess(count: Int) {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPLOAD_STATUS)
            .setSmallIcon(R.drawable.ic_notification_success)
            .setContentTitle("Upload Complete")
            .setContentText("Successfully uploaded $count observations")
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPLOAD_SUCCESS, notification)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showUploadFailure(tenantName: String, failureCount: Int, errorMessage: String?) {
        if (!hasNotificationPermission()) return

        val title = "Upload Failed"
        val text = buildString {
            append("Unable to sync observations for $tenantName")
            if (failureCount > 1) {
                append(" ($failureCount failures)")
            }
            if (!errorMessage.isNullOrBlank()) {
                append(": $errorMessage")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPLOAD_FAILURES)
            .setSmallIcon(R.drawable.ic_notification_error)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPLOAD_FAILURE, notification)
    }

    fun clearUploadFailure() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_UPLOAD_FAILURE)
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}