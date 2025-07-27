package com.melrose.fridgetime

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DatePrinter(private val context: Context, private val rawPrinterClient: NiimbotPrinterClient) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("BarcodeWidthCache", Context.MODE_PRIVATE)

    companion object {
        private const val CACHE_KEY_PREFIX = "barcode_width_"
    }
    suspend fun printToday() {
        printDate(LocalDate.now())
    }

    suspend fun printerState() {
        val rfid = rawPrinterClient.getRfid()
        val barcode = rfid?.get("barcode") as? String
        if (barcode != null) {
            val labelQuery = NiimbotApiService().getLabelWidth(barcode)
            println(labelQuery)
        } else {
            println("failed to get barcode. rfid: $rfid")
        }
    }

    private suspend fun printWidth(): Result<Int> {
        val rfid = rawPrinterClient.getRfid()
        val barcode = rfid?.get("barcode") as? String
        if (barcode == null) {
            println("failed to get barcode. rfid: $rfid")
            return Result.failure(Exception("failed to get barcode. rfid: $rfid"))
        }
        return getWidthForBarcode(barcode).map { it * 8 }
    }

    private suspend fun getWidthForBarcode(barcodeType: String): Result<Int> {
        // 1. Try to get the width from the cache
        val cachedWidth = getCachedWidth(barcodeType)
        if (cachedWidth != null) {
            println("Cache hit for barcode: $barcodeType, width: $cachedWidth")
            return Result.success(cachedWidth)
        }

        // 2. If not in cache, fetch from the API
        println("Cache miss for barcode: $barcodeType. Fetching from API...")
        val apiWidth = NiimbotApiService().getLabelWidth(barcodeType)
        if (apiWidth.isFailure) {
            return apiWidth
        } else {
            cacheWidth(barcodeType, apiWidth.getOrThrow())
        }

        println("Fetched width for barcode: $barcodeType, width: $apiWidth. Cached.")

        return apiWidth
    }

    private fun cacheWidth(barcodeType: String, width: Int) {
        with(sharedPreferences.edit()) {
            putInt(CACHE_KEY_PREFIX + barcodeType, width)
            apply() // Use apply() for asynchronous saving
        }
    }

    private fun getCachedWidth(barcodeType: String): Int? {
        val width = sharedPreferences.getInt(CACHE_KEY_PREFIX + barcodeType, -1)
        return if (width == -1) null else width // Return null if not found
    }

    suspend fun printTomorrow() {
        printDate(LocalDate.now().plusDays(1))
    }

    private suspend fun printDate(date: LocalDate) {
        val formatter = DateTimeFormatter.ofPattern("E MMM d", Locale.ENGLISH)
        val formattedDate = date.format(formatter)
        printText(formattedDate)
    }

    suspend fun printText(text: String) {
        val width = printWidth().getOrThrow()
        val height = 96
        val bmp = createBitmapWithText(text, width, height)
        rawPrinterClient.printLabel(bmp, width, height)
    }
}

private fun createBitmapWithText(text: String, width: Int, height: Int): Bitmap {
    // Create a blank Bitmap with the specified width and height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    // Create a Canvas to draw on the Bitmap
    val canvas = Canvas(bitmap)

    var size = 48f

    do {
        // Create a Paint object for styling the text
        val paint = Paint().apply {
            color = Color.BLACK // Text color
            textSize = size // Text size in pixels
            typeface = Typeface.DEFAULT // Typeface (you can use other fonts if desired)
        }
        val bounds = Rect()

        paint.getTextBounds(text, 0, text.length, bounds)
        if (bounds.width() > (width - 5) || bounds.height() > height) {
            size -= 2
            continue
        } else {
            // Calculate text position (centered)
            val x = (width - paint.measureText(text)) / 2
            val y = (height - (paint.descent() + paint.ascent())) / 2
            // Draw the text on the Canvas
            canvas.drawText(text, x, y, paint)
            break
        }
    } while (true)

    return bitmap
}