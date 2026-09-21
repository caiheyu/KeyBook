package com.github.caiheyu.keybook.feature.icon

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Xml
import com.caverock.androidsvg.SVG
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import com.github.caiheyu.keybook.data.model.IconDraft
import org.xmlpull.v1.XmlPullParser

@Singleton
class IconNormalizer @Inject constructor() {
    fun fromUri(contentResolver: ContentResolver, uri: Uri): IconDraft {
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            input.readLimited(MAX_INPUT_BYTES)
        } ?: throw IllegalArgumentException("无法读取所选图片")
        return normalize(bytes)
    }

    fun normalize(bytes: ByteArray): IconDraft {
        require(bytes.isNotEmpty()) { "图片为空" }
        require(bytes.size <= MAX_INPUT_BYTES) { "图片不能超过 8 MiB" }
        val bitmap = when {
            bytes.isSvg() -> decodeSvg(bytes)
            bytes.isIco() -> decodeIco(bytes)
            else -> decodeRaster(bytes)
        }
        return bitmap.useBitmap { source ->
            require(source.width.toLong() * source.height.toLong() <= MAX_PIXELS) { "图片像素过大" }
            val scaled = source.scaleInside(MAX_DIMENSION)
            scaled.useBitmap { normalized -> encodePng(normalized) }
        }
    }

    private fun decodeRaster(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            require(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= MAX_PIXELS) { "图片像素过大" }
            val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                require(info.size.width.toLong() * info.size.height.toLong() <= MAX_PIXELS) { "图片像素过大" }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(calculateSampleSize(info.size.width, info.size.height))
            }
        }
        throw IllegalArgumentException(
            if (bounds.outWidth > 0 && bounds.outHeight > 0) "图片已损坏" else "当前系统不支持此图片格式",
        )
    }

    private fun decodeSvg(bytes: ByteArray): Bitmap {
        require(bytes.size <= MAX_SVG_BYTES) { "SVG 不能超过 1 MiB" }
        validateSvg(bytes)
        val svg = runCatching { SVG.getFromInputStream(ByteArrayInputStream(bytes)) }
            .getOrElse { throw IllegalArgumentException("SVG 格式无效") }
        val viewBox = svg.documentViewBox
        val rawWidth = svg.documentWidth.takeIf { it.isFinite() && it > 0f }
            ?: viewBox?.width()?.takeIf { it.isFinite() && it > 0f }
            ?: MAX_DIMENSION.toFloat()
        val rawHeight = svg.documentHeight.takeIf { it.isFinite() && it > 0f }
            ?: viewBox?.height()?.takeIf { it.isFinite() && it > 0f }
            ?: MAX_DIMENSION.toFloat()
        require(rawWidth.toDouble() * rawHeight.toDouble() <= MAX_PIXELS.toDouble()) { "SVG 画布过大" }
        val scale = minOf(1f, MAX_DIMENSION / rawWidth, MAX_DIMENSION / rawHeight)
        val width = (rawWidth * scale).toInt().coerceIn(1, MAX_DIMENSION)
        val height = (rawHeight * scale).toInt().coerceIn(1, MAX_DIMENSION)
        svg.documentWidth = width.toFloat()
        svg.documentHeight = height.toFloat()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.TRANSPARENT)
            svg.renderToCanvas(Canvas(bitmap))
        }
    }

    private fun validateSvg(bytes: ByteArray) {
        val raw = bytes.toString(Charsets.UTF_8)
        require(!raw.contains("<!DOCTYPE", true) && !raw.contains("<!ENTITY", true)) {
            "SVG 不能包含 DTD 或实体"
        }
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }
        var nodes = 0
        var depth = 0
        var rootSeen = false
        while (true) {
            when (parser.nextToken()) {
                XmlPullParser.START_TAG -> {
                    nodes++
                    depth++
                    require(nodes <= MAX_SVG_NODES && depth <= MAX_SVG_DEPTH) { "SVG 结构过于复杂" }
                    val element = parser.name.lowercase()
                    require(element in ALLOWED_SVG_ELEMENTS) { "SVG 包含不支持的元素：$element" }
                    require(parser.namespace.isNullOrEmpty() || parser.namespace == SVG_NAMESPACE) {
                        "SVG 包含不支持的命名空间"
                    }
                    if (!rootSeen) {
                        require(element == "svg") { "SVG 根元素无效" }
                        rootSeen = true
                    }
                    for (index in 0 until parser.attributeCount) {
                        val name = parser.getAttributeName(index).lowercase()
                        val value = parser.getAttributeValue(index).trim()
                        require(!name.startsWith("on") && name != "style") { "SVG 包含危险属性" }
                        require(name in ALLOWED_SVG_ATTRIBUTES) { "SVG 包含不支持的属性：$name" }
                        if (name == "href") {
                            require(LOCAL_FRAGMENT_REFERENCE.matches(value)) { "SVG 不允许外链资源" }
                        }
                        if (value.contains("url(", true)) {
                            require(LOCAL_URL_REFERENCE.matches(value)) { "SVG 不允许外链资源" }
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.PROCESSING_INSTRUCTION, XmlPullParser.DOCDECL ->
                    throw IllegalArgumentException("SVG 包含不支持的声明")
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        require(rootSeen) { "SVG 根元素无效" }
    }

    private fun decodeIco(bytes: ByteArray): Bitmap {
        require(bytes.size >= 6) { "ICO 文件不完整" }
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(header.short.toInt() == 0 && header.short.toInt() == 1) { "ICO 文件头无效" }
        val count = header.short.toInt() and 0xffff
        require(count in 1..256 && bytes.size >= 6 + count * 16) { "ICO 图层数量无效" }
        val entries = buildList {
            repeat(count) {
                val width = header.get().toInt().and(0xff).let { if (it == 0) 256 else it }
                val height = header.get().toInt().and(0xff).let { if (it == 0) 256 else it }
                header.position(header.position() + 6)
                val size = header.int
                val offset = header.int
                if (width in 1..256 && height in 1..256 && size > 0 && offset >= 0 &&
                    offset.toLong() + size <= bytes.size
                ) add(IcoEntry(width, height, offset, size))
            }
        }
        require(entries.isNotEmpty()) { "ICO 不包含有效图层" }
        val ordered = entries.sortedWith(compareBy<IcoEntry> { kotlin.math.abs(MAX_DIMENSION - maxOf(it.width, it.height)) }
            .thenByDescending { it.width * it.height })
        for (entry in ordered) {
            val layer = bytes.copyOfRange(entry.offset, entry.offset + entry.size)
            runCatching {
                if (layer.hasPngSignature()) decodeRaster(layer) else decodeIcoDib(layer, entry.width, entry.height)
            }.getOrNull()?.let { return it }
        }
        throw IllegalArgumentException("ICO 图层无法解码")
    }

    private fun decodeIcoDib(dib: ByteArray, expectedWidth: Int, expectedHeight: Int): Bitmap {
        require(dib.size >= 40) { "ICO 位图不完整" }
        val data = ByteBuffer.wrap(dib).order(ByteOrder.LITTLE_ENDIAN)
        val headerSize = data.int
        require(headerSize >= 40 && headerSize <= dib.size) { "ICO 位图头无效" }
        val width = data.int
        val doubledHeight = data.int
        require(width == expectedWidth && doubledHeight / 2 == expectedHeight) { "ICO 位图尺寸无效" }
        require(data.short.toInt() == 1) { "ICO 位图平面无效" }
        val bits = data.short.toInt() and 0xffff
        val compression = data.int
        require(bits == 32 && compression == 0) { "仅支持 PNG 或 32 位 ICO 图层" }
        val pixelOffset = headerSize
        val pixelBytes = width.toLong() * expectedHeight * 4
        require(pixelOffset.toLong() + pixelBytes <= dib.size) { "ICO 位图数据不完整" }
        val colors = IntArray(width * expectedHeight)
        for (y in 0 until expectedHeight) {
            val sourceY = expectedHeight - 1 - y
            for (x in 0 until width) {
                val offset = pixelOffset + (sourceY * width + x) * 4
                val b = dib[offset].toInt() and 0xff
                val g = dib[offset + 1].toInt() and 0xff
                val r = dib[offset + 2].toInt() and 0xff
                val a = dib[offset + 3].toInt() and 0xff
                colors[y * width + x] = Color.argb(a, r, g, b)
            }
        }
        return Bitmap.createBitmap(colors, width, expectedHeight, Bitmap.Config.ARGB_8888)
    }

    private fun encodePng(bitmap: Bitmap): IconDraft {
        var current = bitmap
        try {
            while (true) {
                val output = ByteArrayOutputStream()
                require(current.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG 编码失败" }
                val encoded = output.toByteArray()
                if (encoded.size <= MAX_OUTPUT_BYTES) {
                    return IconDraft(encoded, current.width, current.height)
                }
                require(current.width > 32 || current.height > 32) { "图片内容过于复杂，无法压缩到 256 KiB" }
                val next = Bitmap.createScaledBitmap(
                    current,
                    (current.width * 0.85f).toInt().coerceAtLeast(1),
                    (current.height * 0.85f).toInt().coerceAtLeast(1),
                    true,
                )
                if (current !== bitmap) current.recycle()
                current = next
            }
        } finally {
            if (current !== bitmap && !current.isRecycled) current.recycle()
        }
    }

    private fun Bitmap.scaleInside(max: Int): Bitmap {
        if (width <= max && height <= max) return this
        val factor = minOf(max.toFloat() / width, max.toFloat() / height)
        return Bitmap.createScaledBitmap(
            this,
            (width * factor).toInt().coerceAtLeast(1),
            (height * factor).toInt().coerceAtLeast(1),
            true,
        )
    }

    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T = try {
        block(this)
    } finally {
        if (!isRecycled) recycle()
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_DIMENSION * 2 || height / sample > MAX_DIMENSION * 2) sample *= 2
        return sample
    }

    private fun java.io.InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "图片不能超过 8 MiB" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun ByteArray.isSvg(): Boolean {
        val prefix = copyOfRange(0, minOf(size, 512)).toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return prefix.startsWith("<svg", true) || prefix.startsWith("<?xml", true)
    }

    private fun ByteArray.isIco(): Boolean = size >= 4 &&
        this[0] == 0.toByte() && this[1] == 0.toByte() && this[2] == 1.toByte() && this[3] == 0.toByte()

    private fun ByteArray.hasPngSignature(): Boolean = size >= PNG_SIGNATURE.size &&
        copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)

    private data class IcoEntry(val width: Int, val height: Int, val offset: Int, val size: Int)

    companion object {
        const val MAX_INPUT_BYTES = 8 * 1024 * 1024
        const val MAX_OUTPUT_BYTES = 256 * 1024
        const val MAX_DIMENSION = 256
        const val MAX_PIXELS = 16_000_000L
        private const val MAX_SVG_BYTES = 1024 * 1024
        private const val MAX_SVG_NODES = 2_000
        private const val MAX_SVG_DEPTH = 32
        private const val SVG_NAMESPACE = "http://www.w3.org/2000/svg"
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        )
        private val LOCAL_URL_REFERENCE = Regex("(?i)^url\\(\\s*['\"]?#[A-Za-z_][A-Za-z0-9_.:-]*['\"]?\\s*\\)$")
        private val LOCAL_FRAGMENT_REFERENCE = Regex("^#[A-Za-z_][A-Za-z0-9_.:-]*$")
        private val ALLOWED_SVG_ELEMENTS = setOf(
            "svg", "g", "defs", "title", "desc", "metadata", "path", "rect", "circle", "ellipse",
            "line", "polyline", "polygon", "symbol", "lineargradient", "radialgradient",
            "stop", "clippath", "mask", "pattern", "filter", "fegaussianblur", "feoffset",
            "feblend", "fecolormatrix", "fecomposite", "femerge", "femergenode",
        )
        private val ALLOWED_SVG_ATTRIBUTES = setOf(
            "id", "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry",
            "width", "height", "viewbox", "preserveaspectratio", "transform", "d", "points",
            "pathlength", "fill", "fill-opacity", "fill-rule", "stroke", "stroke-width",
            "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "stroke-dasharray",
            "stroke-dashoffset", "stroke-opacity", "opacity", "display", "visibility", "color",
            "stop-color", "stop-opacity", "offset", "gradientunits", "gradienttransform",
            "spreadmethod", "fx", "fy", "href", "clip-path", "clip-rule", "mask", "filter",
            "filterunits", "primitiveunits", "patternunits", "patterncontentunits",
            "patterntransform", "in", "in2", "result", "stddeviation", "dx", "dy", "mode",
            "type", "values", "operator", "k1", "k2", "k3", "k4", "flood-color",
            "flood-opacity", "color-interpolation-filters",
        )
    }
}
