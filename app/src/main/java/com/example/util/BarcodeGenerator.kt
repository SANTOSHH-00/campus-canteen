package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * Robust Barcode & QR Code generator utility using ZXing ("Zebra Crossing") technology.
 * Generates high-contrast, crystal-clear Bitmaps locally on device for dynamic order verification.
 */
object BarcodeGenerator {
  private const val TAG = "BarcodeGenerator"

  /**
   * Generates a 2D QR Code Bitmap.
   *
   * @param content The string payload (order verification token, order ID, or URL).
   * @param sizePx Pixel dimension for square QR code (width and height).
   * @param margin Margin in blocks around QR code.
   * @return A clean, scannable Bitmap or null if content is blank.
   */
  fun generateQrCode(
    content: String,
    sizePx: Int = 512,
    margin: Int = 1,
  ): Bitmap? {
    if (content.isBlank()) return null
    return try {
      val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
        put(EncodeHintType.CHARACTER_SET, "UTF-8")
        put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
        put(EncodeHintType.MARGIN, margin)
      }

      val writer = QRCodeWriter()
      val bitMatrix: BitMatrix = writer.encode(
        content.trim(),
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        hints
      )

      createBitmapFromBitMatrix(bitMatrix)
    } catch (e: Exception) {
      Log.e(TAG, "Error generating QR Code for content: $content", e)
      null
    }
  }

  /**
   * Generates a 1D Code 128 Barcode Bitmap.
   *
   * @param content The alphanumeric payload (e.g. QB-XXXXXXXX or Order ID).
   * @param widthPx Barcode pixel width.
   * @param heightPx Barcode pixel height.
   * @return A high-contrast Bitmap with black bars on white background, or null if invalid.
   */
  fun generateCode128Barcode(
    content: String,
    widthPx: Int = 600,
    heightPx: Int = 180,
  ): Bitmap? {
    if (content.isBlank()) return null
    return try {
      // Code 128 supports ASCII 0-127. Clean token to alphanumeric + standard symbols.
      val clean = content.trim().filter { it.code in 32..126 }
      if (clean.isBlank()) return null

      val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
        put(EncodeHintType.CHARACTER_SET, "UTF-8")
        put(EncodeHintType.MARGIN, 2)
      }

      val writer = MultiFormatWriter()
      val bitMatrix: BitMatrix = writer.encode(
        clean,
        BarcodeFormat.CODE_128,
        widthPx,
        heightPx,
        hints
      )

      createBitmapFromBitMatrix(bitMatrix)
    } catch (e: Exception) {
      Log.e(TAG, "Error generating Code 128 Barcode for content: $content", e)
      // Fallback: try generating QR Code representation if Code 128 fails on character constraints
      generateQrCode(content, sizePx = heightPx.coerceAtLeast(200))
    }
  }

  /**
   * Converts a ZXing BitMatrix into an Android ARGB_8888 Bitmap.
   */
  private fun createBitmapFromBitMatrix(matrix: BitMatrix): Bitmap {
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)

    for (y in 0 until height) {
      val offset = y * width
      for (x in 0 until width) {
        pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
      }
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
  }
}

