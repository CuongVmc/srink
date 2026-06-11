package com.uy.shrinkspace

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImageItem(
    val uri: Uri,
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
)

object MediaScanner {

    /** Quét toàn bộ ảnh trong máy, sắp xếp ảnh nặng nhất lên đầu. */
    suspend fun scanImages(context: Context): List<ImageItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ImageItem>()
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Images.Media.RELATIVE_PATH)
        }.toTypedArray()

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.SIZE} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val pathCol = if (Build.VERSION.SDK_INT >= 29)
                cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "unknown"
                val mime = cursor.getString(mimeCol) ?: continue
                if (mime == "image/gif") continue
                if (name.contains("_shrink", ignoreCase = true)) continue
                if (pathCol >= 0) {
                    val path = cursor.getString(pathCol) ?: ""
                    if (path.contains("ShrinkSpace", ignoreCase = true)) continue
                }

                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                var sizeBytes = cursor.getLong(sizeCol)
                if (sizeBytes <= 0L) {
                    sizeBytes = readUriSizeBytes(context, uri)
                }

                items.add(
                    ImageItem(
                        uri = uri,
                        id = id,
                        name = name,
                        sizeBytes = sizeBytes,
                        width = cursor.getInt(wCol),
                        height = cursor.getInt(hCol),
                        mimeType = mime,
                    )
                )
            }
        }
        items
    }

    private fun readUriSizeBytes(context: Context, uri: Uri): Long {
        return context.contentResolver.openFileDescriptor(uri, "r")?.use {
            it.statSize.coerceAtLeast(0L)
        } ?: 0L
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }
}
