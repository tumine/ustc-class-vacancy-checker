package com.ustc.vacancychecker.data.remote

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * APK 下载状态
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int = 0) : DownloadState()
    data class Completed(val fileUri: Uri, val fileName: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * APK 下载管理器
 */
@Singleton
class ApkDownloader @Inject constructor() {
    
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState
    
    private var currentDownloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null
    
    /**
     * 检查是否有通知权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    /**
     * 开始下载 APK
     */
    fun startDownload(
        context: Context,
        downloadUrl: String,
        fileName: String
    ): Boolean {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        // 根据通知权限设置通知可见性
        val notificationVisibility = if (hasNotificationPermission(context)) {
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        } else {
            // 没有通知权限时仍然尝试显示（某些设备可能允许）
            DownloadManager.Request.VISIBILITY_VISIBLE
        }
        
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("应用更新: $fileName")
            setDescription("正在下载新版本...")
            setNotificationVisibility(notificationVisibility)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            setMimeType("application/vnd.android.package-archive")
        }
        
        currentDownloadId = downloadManager.enqueue(request)
        _downloadState.value = DownloadState.Downloading(0)
        
        // 注册下载完成广播接收器
        registerDownloadReceiver(context)
        
        return true
    }
    
    /**
     * 查询下载进度
     */
    fun queryProgress(context: Context) {
        if (currentDownloadId == -1L) return
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(currentDownloadId)
        
        downloadManager.query(query).use { cursor ->
            if (cursor.moveToFirst()) {
                val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                
                if (bytesDownloadedIndex >= 0 && bytesTotalIndex >= 0 && statusIndex >= 0) {
                    val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                    val bytesTotal = cursor.getLong(bytesTotalIndex)
                    val status = cursor.getInt(statusIndex)
                    
                    if (bytesTotal > 0) {
                        val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                        _downloadState.value = DownloadState.Downloading(progress)
                    }
                    
                    if (status == DownloadManager.STATUS_FAILED) {
                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                        _downloadState.value = DownloadState.Error("下载失败，错误码: $reason")
                        unregisterDownloadReceiver(context)
                    }
                }
            }
        }
    }
    
    private fun registerDownloadReceiver(context: Context) {
        unregisterDownloadReceiver(context)
        
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == currentDownloadId) {
                    handleDownloadComplete(ctx, id)
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(downloadReceiver, filter)
        }
    }
    
    private fun handleDownloadComplete(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        
        downloadManager.query(query).use { cursor ->
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex >= 0 && cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                    val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                    
                    if (uriIndex >= 0) {
                        val localUri = cursor.getString(uriIndex)
                        val fileName = if (titleIndex >= 0) cursor.getString(titleIndex) else "update.apk"
                        
                        if (localUri != null) {
                            _downloadState.value = DownloadState.Completed(Uri.parse(localUri), fileName)
                        }
                    }
                }
            }
        }
        
        unregisterDownloadReceiver(context)
    }
    
    private fun unregisterDownloadReceiver(context: Context) {
        downloadReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // 忽略未注册的接收器异常
            }
        }
        downloadReceiver = null
    }
    
    /**
     * 安装 APK
     */
    fun installApk(context: Context, fileUri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Android 7.0+ 需要 FileProvider
                    val filePath = fileUri.path ?: run {
                        // 尝试从 URI 解码路径
                        java.net.URLDecoder.decode(fileUri.toString(), "UTF-8")
                            .removePrefix("file://")
                    }
                    val file = File(filePath)
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else {
                    fileUri
                }
                
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error("安装失败: ${e.message}")
        }
    }
    
    /**
     * 重置下载状态
     */
    fun reset() {
        _downloadState.value = DownloadState.Idle
        currentDownloadId = -1L
    }
    
    /**
     * 清理资源（注销广播接收器，但保留下载状态）
     */
    fun cleanup(context: Context) {
        unregisterDownloadReceiver(context)
    }
}
