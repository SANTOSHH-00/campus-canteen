package com.example.util

import android.content.Context
import android.net.Uri
import coil.Coil
import com.example.data.session.SessionManager
import java.io.File
import java.io.FileOutputStream

object CanteenImageHelper {

  private const val DIR_CANTEEN_IMAGES = "canteen_images"

  /**
   * Returns a persistent File in the app's internal private storage for this canteen's profile photo.
   */
  fun getLocalProfileFile(context: Context, canteenId: String): File? {
    val dir = File(context.filesDir, DIR_CANTEEN_IMAGES)
    if (!dir.exists()) return null
    val cleanId = canteenId.trim().lowercase()
    val num = cleanId.filter { it.isDigit() }.ifBlank { cleanId.removePrefix("canteen_") }

    // Find the latest timestamped file
    val files = dir.listFiles()?.filter { f ->
      (f.name.contains(cleanId) || f.name.contains(num)) && f.length() > 0
    }
    return files?.maxByOrNull { it.lastModified() }
  }

  /**
   * Clears any local profile image files for this canteen to ensure old photos are completely deleted.
   */
  fun clearLocalCanteenImages(context: Context, canteenId: String) {
    try {
      val dir = File(context.filesDir, DIR_CANTEEN_IMAGES)
      if (dir.exists()) {
        val cleanId = canteenId.trim().lowercase()
        val num = cleanId.filter { it.isDigit() }.ifBlank { cleanId.removePrefix("canteen_") }
        dir.listFiles()?.forEach { file ->
          if (file.name.contains(cleanId) || file.name.contains(num)) {
            file.delete()
          }
        }
      }
      Coil.imageLoader(context).memoryCache?.clear()
    } catch (e: Exception) {
      android.util.Log.w("CanteenImageHelper", "clearLocalCanteenImages warning: ${e.message}")
    }
  }

  /**
   * Saves an image selected from the gallery into internal storage permanently with a unique timestamp.
   * Any previous local image for this canteen is deleted first so the previous image is COMPLETELY GONE.
   */
  fun saveGalleryImageLocally(context: Context, canteenId: String, uri: Uri): File {
    val dir = File(context.filesDir, DIR_CANTEEN_IMAGES).apply { mkdirs() }
    val cleanId = canteenId.trim().lowercase()
    val num = cleanId.filter { it.isDigit() }.ifBlank { cleanId.removePrefix("canteen_") }

    // 1. Delete all old local cached images for this canteen
    dir.listFiles()?.forEach { file ->
      if (file.name.contains(cleanId) || file.name.contains(num)) {
        file.delete()
      }
    }

    // 2. Save with unique timestamp so Coil cache key is fresh
    val newFile = File(dir, "canteen_${num}_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
      FileOutputStream(newFile).use { output ->
        input.copyTo(output)
      }
    }

    // 3. Clear Coil memory cache so previous image is instantly dropped
    try {
      Coil.imageLoader(context).memoryCache?.clear()
    } catch (e: Exception) {}

    // 4. Update session manager with the new absolute file path
    SessionManager.saveCanteenProfile(
      canteenId = canteenId,
      imageUrl = newFile.absolutePath,
      timings = null,
      isOpen = null,
      closeReason = null
    )
    return newFile
  }

  /**
   * Resolves the best available image source for Coil to load from local cache or explicit URL.
   *
   * Priority:
   * 1. Explicit currentUrl (if non-blank)
   * 2. Saved imageUrl from SessionManager
   * 3. Latest persistent local file on disk
   * 4. Fallback: null (Coil shows default monogram / placeholder)
   */
  fun resolveCanteenImageSource(context: Context, canteenId: String, currentUrl: String? = null): Any? {
    // 1. Priority 1: Explicitly provided currentUrl
    val candidate = currentUrl?.ifBlank { null }
    if (!candidate.isNullOrBlank()) {
      if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
        return candidate
      }
      if (candidate.startsWith("file://")) {
        val f = File(candidate.removePrefix("file://"))
        if (f.exists() && f.length() > 0) return f
      }
      if (candidate.startsWith("/")) {
        val f = File(candidate)
        if (f.exists() && f.length() > 0) return f
      }
    }

    // 2. Priority 2: SessionManager saved URL
    val saved = SessionManager.getCanteenImage(canteenId)
    if (!saved.isNullOrBlank()) {
      if (saved.startsWith("http://") || saved.startsWith("https://")) {
        return saved
      }
      if (saved.startsWith("file://")) {
        val f = File(saved.removePrefix("file://"))
        if (f.exists() && f.length() > 0) return f
      }
      if (saved.startsWith("/")) {
        val f = File(saved)
        if (f.exists() && f.length() > 0) return f
      }
    }

    // 3. Priority 3: Local cached profile file
    val localFile = getLocalProfileFile(context, canteenId)
    if (localFile != null && localFile.exists() && localFile.length() > 0) {
      return localFile
    }

    // 4. Fallback: null (Coil shows default monogram)
    return null
  }
}
