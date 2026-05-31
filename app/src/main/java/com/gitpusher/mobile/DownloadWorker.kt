package com.gitpusher.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.content.pm.ServiceInfo

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "download_channel_v1"
    private val baseNotificationId = inputData.getString("name")?.hashCode() ?: System.currentTimeMillis().toInt()
    private val notificationId = Math.abs(baseNotificationId)

    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val fileName = inputData.getString("name") ?: "download.zip"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        try {
            setForeground(createForegroundInfo(fileName, 0))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val request = Request.Builder().url(url).build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure()
            }

            val body = response.body ?: return Result.failure()
            val fileLength = body.contentLength()
            
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(file)

            val buffer = ByteArray(8 * 1024)
            var total = 0L
            var count: Int
            var lastUpdate = System.currentTimeMillis()

            while (inputStream.read(buffer).also { count = it } != -1) {
                total += count
                outputStream.write(buffer, 0, count)

                val now = System.currentTimeMillis()
                if (now - lastUpdate > 1000) {
                    val progress = if (fileLength > 0) ((total * 100) / fileLength).toInt() else 0
                    try {
                        setForeground(createForegroundInfo(fileName, progress))
                    } catch (e: Exception) {}
                    lastUpdate = now
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            val successNotification = NotificationCompat.Builder(applicationContext, channelId)
                .setContentTitle("Download Complete")
                .setContentText(fileName)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .build()
            notificationManager.notify(notificationId + 1000, successNotification)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            val errorNotification = NotificationCompat.Builder(applicationContext, channelId)
                .setContentTitle("Download Failed")
                .setContentText(e.message)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .build()
            notificationManager.notify(notificationId + 2000, errorNotification)
            Result.failure()
        }
    }

    private fun createForegroundInfo(fileName: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Downloading $fileName")
            .setContentText(if (progress > 0) "$progress%" else "Preparing...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()
            
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
