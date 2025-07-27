package com.melrose.fridgetime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test


/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun testBitmapEncoding() {
        val bitmap = Bitmap.createBitmap(240, 96, Bitmap.Config.RGB_565)
        val packets = NiimbotPrinterClient.NiimbotPacket.naiveEncoder(bitmap)
        val binaryData = packets.toList().map { it.toBytes() }
        val hexData = binaryData.map { it.toHex() }
        // 96 rows — 96 packets
        assertEquals(hexData.size, 96)
        assertEquals(hexData[0], "55558512000020202001ffffffffffffffffffffffffb6aaaa")

    }

    @Test
    fun testCheckerboard() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

        // Load the bitmap from the androidTest resources
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.nim)
        assertEquals(300, bitmap.height);
        val packets = NiimbotPrinterClient.NiimbotPacket.naiveEncoder(bitmap)
        val binaryData = packets.toList().map { it.toBytes() }
        val hexData = binaryData.map { it.toHex() }
        // 96 rows — 96 packets
        assertEquals(hexData.size, 300)
        assertEquals("55558512000020202001ffffffffffffffffffffffffb6aaaa", hexData[0])

        // Now you can work with the loaded bitmap

        // Now you can work with the loaded bitmap
        if (bitmap != null) {
            // Do something with the bitmap
        }
    }

    fun ByteArray.toHex(): String =
        joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}