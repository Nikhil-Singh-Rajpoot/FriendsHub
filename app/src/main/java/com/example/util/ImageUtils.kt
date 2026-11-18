package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageUtils {
  private const val MAX_DIM_PX = 280
  private const val JPEG_QUALITY = 72
  private const val MAX_ENCODED_CHARS = 240_000

  suspend fun encodePickerUriToDataString(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
      runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
          val original = BitmapFactory.decodeStream(stream) ?: return@withContext null
          compressBitmapToBase64(original)
        }
      }.getOrNull()
    }

  suspend fun encodeCapturedSelfieFile(file: File): String? =
    withContext(Dispatchers.IO) {
      runCatching {
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
        val exifOrientation =
          runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
              ExifInterface.TAG_ORIENTATION,
              ExifInterface.ORIENTATION_NORMAL,
            )
          }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val rotated = applyExifOrientation(original, exifOrientation)
        if (rotated !== original) original.recycle()
        compressBitmapToBase64(rotated)
      }.getOrNull()
    }

  private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.postRotate(90f)
        matrix.preScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.postRotate(270f)
        matrix.preScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
      else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
  }

  private fun compressBitmapToBase64(bitmap: Bitmap): String? {
    val scale =
      min(
        MAX_DIM_PX.toFloat() / bitmap.width.coerceAtLeast(1),
        MAX_DIM_PX.toFloat() / bitmap.height.coerceAtLeast(1),
      ).coerceAtMost(1f)
    val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
    val scaled =
      if (targetW != bitmap.width || targetH != bitmap.height) {
        Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
      } else {
        bitmap
      }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    if (scaled !== bitmap) scaled.recycle()
    bitmap.recycle()
    return if (encoded.length > MAX_ENCODED_CHARS) null else encoded
  }

  fun decodePhotoData(photoData: String): Bitmap? {
    if (photoData.isBlank()) return null
    return runCatching {
      val clean = photoData.substringAfter("base64,", photoData)
      val bytes = Base64.decode(clean, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
  }
}
