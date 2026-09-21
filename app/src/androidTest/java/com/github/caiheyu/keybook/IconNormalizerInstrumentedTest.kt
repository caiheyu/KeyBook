package com.github.caiheyu.keybook

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import com.github.caiheyu.keybook.feature.icon.IconNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IconNormalizerInstrumentedTest {
    private val normalizer = IconNormalizer()

    @Test
    fun rasterIsScaledAndEncodedAsBoundedPng() {
        val source = Bitmap.createBitmap(1024, 512, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }
        val bytes = ByteArrayOutputStream().also {
            source.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }.toByteArray()
        source.recycle()

        val normalized = normalizer.normalize(bytes)

        assertEquals(256, normalized.width)
        assertEquals(128, normalized.height)
        assertTrue(normalized.pngBytes.size <= IconNormalizer.MAX_OUTPUT_BYTES)
        assertTrue(normalized.pngBytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE))
    }

    @Test
    fun pngAndWebpAreAccepted() {
        val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        try {
            listOf(Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.WEBP).forEach { format ->
                val bytes = ByteArrayOutputStream().also {
                    assertTrue(bitmap.compress(format, 100, it))
                }.toByteArray()

                val normalized = normalizer.normalize(bytes)

                assertEquals(8, normalized.width)
                assertEquals(4, normalized.height)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun gifAndBmpAreAccepted() {
        val gif = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==")
        val bmp = bmp24(width = 1, height = 1, includePixels = true)

        assertEquals(1, normalizer.normalize(gif).width)
        assertEquals(1, normalizer.normalize(bmp).width)
    }

    @Test
    fun oversizedInputAndDeclaredPixelCountAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizer.normalize(ByteArray(IconNormalizer.MAX_INPUT_BYTES + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizer.normalize(bmp24(width = 5_000, height = 4_000, includePixels = false))
        }
    }

    @Test
    fun icoWithPngLayerIsDetectedByContent() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.CYAN)
        }
        val png = ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        bitmap.recycle()
        val ico = ByteBuffer.allocate(22 + png.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0)
            putShort(1)
            putShort(1)
            put(32)
            put(32)
            put(0)
            put(0)
            putShort(1)
            putShort(32)
            putInt(png.size)
            putInt(22)
            put(png)
        }.array()

        val normalized = normalizer.normalize(ico)

        assertEquals(32, normalized.width)
        assertEquals(32, normalized.height)
    }

    @Test
    fun safeSvgRendersButScriptAndExternalLinksAreRejected() {
        val safe = """
            <svg xmlns="http://www.w3.org/2000/svg" width="64" height="32">
              <rect width="64" height="32" fill="#176B5B"/>
            </svg>
        """.trimIndent().toByteArray()
        assertEquals(64, normalizer.normalize(safe).width)

        val script = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
        assertThrows(IllegalArgumentException::class.java) {
            normalizer.normalize(script.toByteArray())
        }
        val external = "<svg xmlns=\"http://www.w3.org/2000/svg\"><use href=\"https://evil.test/a.svg#x\"/></svg>"
        assertThrows(IllegalArgumentException::class.java) {
            normalizer.normalize(external.toByteArray())
        }
        val unknownAttribute = "<svg xmlns=\"http://www.w3.org/2000/svg\" data-source=\"secret\"><path d=\"M0 0\"/></svg>"
        assertThrows(IllegalArgumentException::class.java) {
            normalizer.normalize(unknownAttribute.toByteArray())
        }
        val foreignNamespace = "<svg xmlns=\"urn:not-svg\" width=\"10\" height=\"10\"><path d=\"M0 0\"/></svg>"
        assertThrows(IllegalArgumentException::class.java) {
            normalizer.normalize(foreignNamespace.toByteArray())
        }
    }

    @Test
    fun svgUseElementIsRejectedEvenForLocalReferences() {
        val use = """
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16">
              <defs><rect id="shape" width="16" height="16"/></defs>
              <use href="#shape"/>
            </svg>
        """.trimIndent().toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            normalizer.normalize(use)
        }
    }

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        )

        private fun bmp24(width: Int, height: Int, includePixels: Boolean): ByteArray {
            val rowBytes = ((width * 3L + 3L) / 4L * 4L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val pixelBytes = if (includePixels) rowBytes * height else 0
            return ByteBuffer.allocate(54 + pixelBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
                put('B'.code.toByte())
                put('M'.code.toByte())
                putInt(54 + pixelBytes)
                putShort(0)
                putShort(0)
                putInt(54)
                putInt(40)
                putInt(width)
                putInt(height)
                putShort(1)
                putShort(24)
                putInt(0)
                putInt(rowBytes * height)
                putInt(2_835)
                putInt(2_835)
                putInt(0)
                putInt(0)
                if (includePixels) {
                    put(0xff.toByte())
                    put(0)
                    put(0)
                    repeat(pixelBytes - 3) { put(0) }
                }
            }.array()
        }
    }
}
