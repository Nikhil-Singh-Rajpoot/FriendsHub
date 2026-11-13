package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
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
          val scale =
            min(
              MAX_DIM_PX.toFloat() / original.width.coerceAtLeast(1),
              MAX_DIM_PX.toFloat() / original.height.coerceAtLeast(1),
            ).coerceAtMost(1f)
          val targetW = (original.width * scale).toInt().coerceAtLeast(1)
          val targetH = (original.height * scale).toInt().coerceAtLeast(1)
          val scaled =
            if (targetW != original.width || targetH != original.height) {
              Bitmap.createScaledBitmap(original, targetW, targetH, true)
            } else {
              original
            }
          val out = ByteArrayOutputStream()
          scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
          val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
          if (scaled !== original) scaled.recycle()
          original.recycle()
          if (encoded.length > MAX_ENCODED_CHARS) null else encoded
        }
      }.getOrNull()
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
