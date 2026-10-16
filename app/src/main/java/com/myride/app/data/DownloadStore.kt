package com.myride.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class DownloadStore(private val context: Context) {
    fun save(name: String, mimeType: String, write: (OutputStream) -> Unit): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return saveLegacy(name, mimeType, write)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Не удалось создать файл в Downloads")
        return try {
            resolver.openOutputStream(uri)?.use(write) ?: error("Не удалось открыть файл для записи")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(name: String, mimeType: String, write: (OutputStream) -> Unit): Uri {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER,
        )
        check(directory.exists() || directory.mkdirs()) { "Не удалось создать папку Downloads/$FOLDER" }
        val file = File(directory, name)
        FileOutputStream(file).use(write)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
        return Uri.fromFile(file)
    }

    private companion object {
        const val FOLDER = "MyRide"
    }
}
