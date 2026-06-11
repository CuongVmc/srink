package com.uy.shrinkspace

import android.content.ContentUris
import android.content.Context
import android.net.Uri
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
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE,
        )

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

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val mime = cursor.getString(mimeCol) ?: continue
                // Bỏ qua GIF (nén sẽ mất animation)
                if (mime == "image/gif") continue
                items.add(
                    ImageItem(
                        uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        ),
                        id = id,
                        name = cursor.getString(nameCol) ?: "unknown",
                        sizeBytes = cursor.getLong(sizeCol),
                        width = cursor.getInt(wCol),
                        height = cursor.getInt(hCol),
                        mimeType = mime,
                    )
                )
            }
        }
        items
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
