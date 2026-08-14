package frontend

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Surface
import io.VideoFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RendererTest {
    @Test
    fun pixelSharpRendererDrawsFramebuffer() {
        assertNotEquals(Color.BLACK, renderCenterPixel(crt = false))
    }

    @Test
    fun crtRendererCompilesAndDrawsFramebuffer() {
        assertNotEquals(Color.BLACK, renderCenterPixel(crt = true))
    }

    @Test
    fun rendererKeepsAspectRatioAndFillsVisibleFrame() {
        assertEquals(Color.BLACK, renderPixel(crt = false, x = 799, y = 240))
        assertEquals(Color.BLACK, renderPixel(crt = true, x = 799, y = 240))
        assertNotEquals(Color.BLACK, renderPixel(crt = false, x = 655, y = 240))
        assertNotEquals(Color.BLACK, renderPixel(crt = true, x = 655, y = 240))
    }

    private fun renderCenterPixel(crt: Boolean): Int {
        val renderer = Renderer()
        val surface = Surface.makeRasterN32Premul(512, 480)
        return try {
            renderer.init(if (crt) VideoFilter.CRT else VideoFilter.NONE)
            renderer.present(testFrame(), 512, 480)
            renderer.draw(surface.canvas)

            val image = surface.makeImageSnapshot()
            try {
                val bitmap = Bitmap.makeFromImage(image)
                try {
                    bitmap.getColor(256, 240)
                } finally {
                    bitmap.close()
                }
            } finally {
                image.close()
            }
        } finally {
            renderer.close()
            surface.close()
        }
    }

    private fun renderPixel(crt: Boolean, x: Int, y: Int): Int {
        val renderer = Renderer()
        val surface = Surface.makeRasterN32Premul(800, 480)
        return try {
            renderer.init(if (crt) VideoFilter.CRT else VideoFilter.NONE)
            renderer.present(testFrame(), 800, 480)
            renderer.draw(surface.canvas)

            val image = surface.makeImageSnapshot()
            try {
                val bitmap = Bitmap.makeFromImage(image)
                try {
                    bitmap.getColor(x, y)
                } finally {
                    bitmap.close()
                }
            } finally {
                image.close()
            }
        } finally {
            renderer.close()
            surface.close()
        }
    }

    private fun testFrame(): ByteArray = ByteArray(256 * 240) { 0x21 }
}
