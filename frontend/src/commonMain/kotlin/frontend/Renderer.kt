package frontend

import io.VideoFilter
import io.readTextResource
import org.jetbrains.skia.*

class Renderer {
    private val frameImageInfo = ImageInfo(
        FRAME_WIDTH,
        FRAME_HEIGHT,
        ColorType.RGBA_8888,
        ColorAlphaType.OPAQUE,
        ColorSpace.sRGB,
    )
    private val frameUploadBuffer = ByteArray(FRAME_WIDTH * FRAME_HEIGHT * 4)
    private var frameBitmap: Bitmap? = null
    private var backgroundPaint: Paint? = null
    private var framePaint: Paint? = null
    private var crtEffect: RuntimeEffect? = null
    private var crtBuilder: RuntimeShaderBuilder? = null
    private var videoFilter = VideoFilter.NONE
    private var initialized = false
    private var outputWidth = 0
    private var outputHeight = 0
    private var presentedFrames = 0L

    fun init(videoFilter: VideoFilter) {
        release()
        try {
            this.videoFilter = videoFilter
            backgroundPaint = Paint().apply { color = Color.BLACK }
            framePaint = Paint().apply { isAntiAlias = false }
            when (videoFilter) {
                VideoFilter.CRT -> {
                    crtEffect = RuntimeEffect.makeForShader(readTextResource(CRT_SHADER_RESOURCE))
                    crtBuilder = RuntimeShaderBuilder(requireNotNull(crtEffect))
                }

                VideoFilter.NONE -> Unit
            }
            presentedFrames = 0L
            initialized = true
        } catch (error: Throwable) {
            release()
            throw error
        }
    }

    fun present(framebuffer: IntArray, windowWidth: Int, windowHeight: Int) {
        check(initialized) { "Skiko renderer is not initialized" }
        require(framebuffer.size >= FRAME_WIDTH * FRAME_HEIGHT) { "Incomplete NES frame" }

        uploadFrame(framebuffer, ensureFrameBitmap())
        outputWidth = windowWidth
        outputHeight = windowHeight
    }

    private fun ensureFrameBitmap(): Bitmap {
        val existing = frameBitmap
        if (existing != null) return existing

        return Bitmap().also { bitmap ->
            check(bitmap.allocPixels(frameImageInfo)) { "Failed to allocate NES frame bitmap" }
            frameBitmap = bitmap
        }
    }

    private fun uploadFrame(source: IntArray, bitmap: Bitmap) {
        var pixel = 0
        var offset = 0
        while (pixel < FRAME_WIDTH * FRAME_HEIGHT) {
            val rgb = nesRgb(source[pixel])
            frameUploadBuffer[offset++] = ((rgb shr 16) and 0xFF).toByte()
            frameUploadBuffer[offset++] = ((rgb shr 8) and 0xFF).toByte()
            frameUploadBuffer[offset++] = (rgb and 0xFF).toByte()
            frameUploadBuffer[offset++] = 0xFF.toByte()
            pixel++
        }
        check(bitmap.installPixels(frameImageInfo, frameUploadBuffer, FRAME_WIDTH * 4)) { "Failed to upload NES frame bitmap" }
    }

    fun draw(canvas: Canvas) {
        val frame = frameBitmap ?: return
        if (outputWidth <= 0 || outputHeight <= 0) return

        val output = Rect.makeWH(outputWidth.toFloat(), outputHeight.toFloat())
        canvas.drawRect(output, requireNotNull(backgroundPaint))
        val destination = destinationRect()
        when (videoFilter) {
            VideoFilter.CRT -> {
                drawCrt(canvas, frame, destination)
            }

            VideoFilter.NONE -> {
                drawLayer(canvas, frame, destination)
            }
        }
        presentedFrames++
    }

    private fun drawLayer(canvas: Canvas, bitmap: Bitmap, destination: Rect) {
        val shader = makeFrameShader(bitmap)
        val paint = requireNotNull(framePaint)
        try {
            paint.shader = shader
            canvas.save()
            try {
                canvas.translate(destination.left, destination.top)
                canvas.scale(destination.width / FRAME_WIDTH, destination.height / FRAME_HEIGHT)
                canvas.drawRect(SOURCE_RECT, paint)
            } finally {
                canvas.restore()
                paint.shader = null
            }
        } finally {
            shader.close()
        }
    }

    fun close() {
        release()
    }

    private fun drawCrt(canvas: Canvas, frameBitmap: Bitmap, destination: Rect) {
        val frameShader = makeFrameShader(frameBitmap)
        try {
            val builder = requireNotNull(crtBuilder)
            builder.child("frameTexture", frameShader)
            builder.uniform("outputSize", outputWidth.toFloat(), outputHeight.toFloat())
            builder.uniform("destinationOrigin", destination.left, destination.top)
            builder.uniform("destinationSize", destination.width, destination.height)
            builder.uniform("time", presentedFrames / 60f)

            val shader = builder.makeShader()
            val paint = requireNotNull(framePaint)
            try {
                paint.shader = shader
                canvas.drawRect(destination, paint)
            } finally {
                paint.shader = null
                shader.close()
            }
        } finally {
            frameShader.close()
        }
    }

    private fun makeFrameShader(frameBitmap: Bitmap): Shader {
        return frameBitmap.makeShader(
            FilterTileMode.CLAMP,
            FilterTileMode.CLAMP,
            SamplingMode.DEFAULT,
            null,
        )
    }

    private fun destinationRect(): Rect {
        val width = outputWidth.toFloat()
        val height = outputHeight.toFloat()
        val scale = minOf(width / FRAME_WIDTH, height / FRAME_HEIGHT)
        val destinationWidth = FRAME_WIDTH * scale
        val destinationHeight = FRAME_HEIGHT * scale
        val left = (width - destinationWidth) * 0.5f
        val top = (height - destinationHeight) * 0.5f
        return Rect.makeLTRB(left, top, left + destinationWidth, top + destinationHeight)
    }

    private fun release() {
        framePaint?.shader = null
        frameBitmap?.close()
        crtBuilder?.close()
        crtEffect?.close()
        framePaint?.close()
        backgroundPaint?.close()
        frameBitmap = null
        crtBuilder = null
        crtEffect = null
        framePaint = null
        backgroundPaint = null
        initialized = false
    }

    private companion object {
        const val FRAME_WIDTH = 256
        const val FRAME_HEIGHT = 240
        const val CRT_SHADER_RESOURCE = "shaders/crt.sksl"
        val SOURCE_RECT = Rect.makeWH(FRAME_WIDTH.toFloat(), FRAME_HEIGHT.toFloat())

        val NES_PALETTE = intArrayOf(
            0x545454, 0x001D74, 0x081090, 0x300088, 0x440064, 0x5C0030, 0x540400, 0x3C1800,
            0x202A00, 0x083A00, 0x004000, 0x003C00, 0x00323C, 0x000000, 0x000000, 0x000000,
            0x989698, 0x084CC4, 0x3032EC, 0x5C1EE4, 0x8814B0, 0xA01464, 0x982220, 0x783C00,
            0x545A00, 0x287200, 0x087C00, 0x007628, 0x006678, 0x000000, 0x000000, 0x000000,
            0xECEEEC, 0x4C9AEC, 0x787CEC, 0xB062EC, 0xE454EC, 0xEC58B4, 0xEC6A64, 0xD48820,
            0xA0AA00, 0x74C400, 0x4CD020, 0x38CC6C, 0x38B4CC, 0x3C3C3C, 0x000000, 0x000000,
            0xECEEEC, 0xA8CCEC, 0xBCBCEC, 0xD4B2EC, 0xECAEEC, 0xECAED4, 0xECB4B0, 0xE4C490,
            0xCCD278, 0xB4DE78, 0xA8E290, 0x98E2B4, 0xA0D6E4, 0xA0A2A0, 0x000000, 0x000000,
        )

        fun nesRgb(value: Int): Int {
            var rgb = NES_PALETTE[value and 0x3F]
            val emphasis = value and 0x1C0
            if (emphasis == 0) return rgb
            var r = (rgb shr 16) and 0xFF
            var g = (rgb shr 8) and 0xFF
            var b = rgb and 0xFF
            if ((emphasis and 0x040) == 0) r = r * 3 / 4
            if ((emphasis and 0x080) == 0) g = g * 3 / 4
            if ((emphasis and 0x100) == 0) b = b * 3 / 4
            return (r shl 16) or (g shl 8) or b
        }
    }
}
