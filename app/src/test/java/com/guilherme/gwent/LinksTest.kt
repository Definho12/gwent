package com.guilherme.gwent

import com.guilherme.gwent.engine.Links
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test fun findsUrlInSharedText() {
        assertEquals("https://pin.it/3abcDEF", Links.firstUrl("Olha esse Pin no Pinterest! https://pin.it/3abcDEF"))
        assertNull(Links.firstUrl("sem link aqui"))
        assertTrue(Links.isPinterest("https://pin.it/abc"))
        assertTrue(Links.isPinterest("https://br.pinterest.com/pin/123456789/"))
        assertTrue(!Links.isPinterest("https://tenor.com/view/x"))
    }

    @Test fun pinPrefersVideoThenGif() {
        val withVideo = JSONObject("""{"images":{"orig":{"url":"https://i.pinimg.com/originals/a/b/c.gif"}},
            "videos":{"video_list":{"V_HLSV4":{"url":"https://v1.pinimg.com/videos/x.m3u8"},"V_EXP7":{"url":"https://v1.pinimg.com/videos/x_720w.mp4"}}}}""")
        assertEquals("https://v1.pinimg.com/videos/x_720w.mp4", Links.pickFromPin(withVideo))
        val gifOnly = JSONObject("""{"images":{"orig":{"url":"https://i.pinimg.com/originals/a/b/c.gif"}},"videos":null}""")
        assertEquals("https://i.pinimg.com/originals/a/b/c.gif", Links.pickFromPin(gifOnly))
        val story = JSONObject("""{"images":{"orig":{"url":"https://i.pinimg.com/originals/a.jpg"}},
            "story_pin_data":{"pages":[{"blocks":[{"video":{"video_list":{"V_EXP4":{"url":"https://v1.pinimg.com/videos/s.mp4"}}}}]}]}}""")
        assertEquals("https://v1.pinimg.com/videos/s.mp4", Links.pickFromPin(story))
    }

    @Test fun readsPageTags() {
        val tenor = """<html><meta property="og:image" content="https://media.tenor.com/a.png">
            <meta property="og:video:secure_url" content="https://media.tenor.com/a.mp4"></html>"""
        assertEquals("https://media.tenor.com/a.mp4", Links.fromHtml(tenor))
        val pin = """<script>{"url":"https://i.pinimg.com/originals/12/34/56/abcdef.gif"}</script><meta property="og:image" content="https://i.pinimg.com/736x/x.jpg">"""
        assertEquals("https://i.pinimg.com/originals/12/34/56/abcdef.gif", Links.fromHtml(pin))
        val plain = """<meta content="https://site/x.jpg" property="og:image">"""
        assertEquals("https://site/x.jpg", Links.fromHtml(plain))
    }
}
