package com.github.caiheyu.keybook.feature.icon

import com.github.caiheyu.keybook.data.model.IconDraft
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebsiteIconParsingTest {
    @Test
    fun extractsDataAndRelativeIconReferencesWithoutLosingDataUrl() {
        val dataUrl = "data:image/png;base64,iVBORw0KGgo="
        val html = """
            <html><head>
              <link rel="icon" href="$dataUrl">
              <link rel="apple-touch-icon" href="/images/touch.png#old">
              <link rel="manifest" href="manifest.webmanifest#ignored">
            </head></html>
        """.trimIndent().toByteArray()

        val references = WebsiteIconParsing.extractDocumentReferences(
            html,
            "https://example.com/account/page#fragment".toHttpUrl(),
        )

        assertEquals(
            listOf(dataUrl, "https://example.com/images/touch.png"),
            references.iconSources,
        )
        assertEquals(
            listOf("https://example.com/account/manifest.webmanifest"),
            references.manifestSources,
        )
    }

    @Test
    fun decodesStrictImageBase64AndRejectsOtherData() {
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4),
            WebsiteIconParsing.decodeDataUrl("data:image/png;base64,AQIDBA=="),
        )
        assertThrows(IllegalArgumentException::class.java) {
            WebsiteIconParsing.decodeDataUrl("data:text/html;base64,AQIDBA==")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebsiteIconParsing.decodeDataUrl("data:image/png;base64,AQIDBA%3D%3D")
        }
    }

    @Test
    fun ordersLargestDecodableCandidateFirstAndKeepsStableTies() {
        fun candidate(name: String, width: Int, height: Int) = WebsiteIconCandidate(
            source = name,
            icon = IconDraft(byteArrayOf(1), width, height),
        )
        val candidates = listOf(
            candidate("small", 32, 32),
            candidate("wide", 256, 128),
            candidate("square-first", 256, 256),
            candidate("square-second", 256, 256),
        )

        assertEquals(
            listOf("square-first", "square-second", "wide", "small"),
            WebsiteIconParsing.sortCandidates(candidates).map { it.source },
        )
    }
}
