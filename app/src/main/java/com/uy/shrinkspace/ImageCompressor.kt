package com.uy.shrinkspace

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CompressResult(
    val original: ImageItem,
    val newUri: Uri?,
    val newSizeBytes: Long,
    val skipped: Boolean,
)

object ImageCompressor {

    /**
     * Nén 1 ảnh: thu nhỏ về tối đa [maxDimension] px cạnh dài,
     * encode WebP/JPEG với [quality], lưu vào album "ShrinkSpace".
     * Nếu ảnh sau nén không nhỏ hơn ảnh gốc thì bỏ qua.
     */
    suspend fun compress(
        context: Context,
        item: ImageItem,
        quality: Int,
        maxDimension: Int,
    ): CompressResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(item.uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return@withContext CompressResult(item, null, 0, skipped = true)

        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= maxDimension ||
            bounds.outHeight / (sample * 2) >= maxDimension
        ) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = resolver.openInputStream(item.uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return@withContext CompressResult(item, null, 0, skipped = true)

        // Scale chính xác về maxDimension nếu vẫn lớn hơn
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide > maxDimension) {
            val scale = maxDimension.toFloat() / longSide
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
            if (scaled != bitmap) bitmap.recycle()
            bitmap = scaled
        }

        // Giữ đúng chiều xoay từ EXIF
        bitmap = applyExifRotation(resolver.openInputStream(item.uri), bitmap)

        val format = if (Build.VERSION.SDK_INT >= 30)
            Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.JPEG
        val ext = if (Build.VERSION.SDK_INT >= 30) "webp" else "jpg"
        val mime = if (Build.VERSION.SDK_INT >= 30) "image/webp" else "image/jpeg"

        val baseName = item.name.substringBeforeLast('.')
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${baseName}_shrink.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ShrinkSpace")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val newUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: run {
                bitmap.recycle()
                return@withContext CompressResult(item, null, 0, skipped = true)
            }

        resolver.openOutputStream(newUri)?.use { out ->
            bitmap.compress(format, quality, out)
        }
        bitmap.recycle()

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(newUri, values, null, null)

        val newSize = resolver.query(
            newUri, arrayOf(MediaStore.Images.Media.SIZE), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L

        // Không tiết kiệm được thì xoá bản nén, giữ ảnh gốc
        if (newSize == 0L || newSize >= item.sizeBytes) {
            resolver.delete(newUri, null, null)
            return@withContext CompressResult(item, null, item.sizeBytes, skipped = true)
        }

        CompressResult(item, newUri, newSize, skipped = false)
    }

    private fun applyExifRotation(
        stream: java.io.InputStream?,
        bitmap: Bitmap,
    ): Bitmap {
        val orientation = stream?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: return bitmap

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
