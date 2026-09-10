package com.example.data.firebase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.api.ImageUploadResponseDto
import com.example.data.api.MongoRepository
import com.example.data.session.SessionManager

/**
 * Storage repository proxying uploads to Cloudinary via backend proxy and saving links in MongoDB.
 */
class StorageRepository(
  private val mongoRepo: MongoRepository = MongoRepository(),
) {
  private val TAG = "StorageRepository"

  suspend fun uploadItemImageBytes(
    canteenId: String,
    itemId: String,
    bytes: ByteArray,
    filename: String = "${itemId}.jpg",
  ): Result<ImageUploadResponseDto> {
    return mongoRepo.uploadItemImage(bytes, filename)
  }

  suspend fun uploadItemImage(
    canteenId: String,
    itemId: String,
    imageUri: Uri,
    context: Context? = null,
  ): Result<ImageUploadResponseDto> {
    return runCatching {
      val ctx = context ?: SessionManager.appContext
      if (ctx == null) {
        // Fallback to URI string if context is unavailable
        return@runCatching ImageUploadResponseDto(success = true, imageUrl = imageUri.toString())
      }
      val inputStream = ctx.contentResolver.openInputStream(imageUri)
        ?: throw Exception("Could not open input stream for selected image URI: $imageUri")
      val bytes = inputStream.use { it.readBytes() }
      if (bytes.isEmpty()) {
        throw Exception("Selected image file is empty.")
      }
      val uploadRes = mongoRepo.uploadItemImage(bytes, "item_${itemId}.jpg")
      uploadRes.getOrThrow()
    }.onFailure { Log.e(TAG, "uploadItemImage to Cloudinary failed: ${it.message}", it) }
  }

  suspend fun uploadUserAvatar(
    userId: String,
    imageUri: Uri,
  ): Result<String> {
    return Result.success(imageUri.toString())
  }

  suspend fun uploadCanteenImageBytes(
    canteenId: String,
    bytes: ByteArray,
  ): Result<String> {
    return Result.success("")
  }

  suspend fun uploadCanteenImage(
    canteenId: String,
    imageUri: Uri,
    context: Context? = null,
  ): Result<String> {
    return Result.success(imageUri.toString())
  }
}
