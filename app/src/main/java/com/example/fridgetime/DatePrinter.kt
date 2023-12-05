package com.example.fridgetime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DatePrinter(private val rawPrinterClient: NiimbotPrinterClient) {
    suspend fun printToday() {
        printDate(LocalDate.now())
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
        val bmp = createBitmapWithText(text, 240, 96)
        rawPrinterClient.printLabel(bmp, 240, 96)
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