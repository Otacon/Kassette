package frontend

import io.VideoFilter
import io.readTextResource
import org.jetbrains.skia.*

class PlatformRenderer : Renderer {
    private val frameImageInfo = ImageInfo(
        FRAME_WIDTH,
        FRAME_HEIGHT,
        ColorType.ALPHA_8,
        ColorAlphaType.OPAQUE,
        ColorSpace.sRGB,
    )
    private var frameBitmap: Bitmap? = null
    private var backgroundPaint: Paint? = null
    private var framePaint: Paint? = null
    private var paletteEffect: RuntimeEffect? = null
    private var paletteBuilder: RuntimeShaderBuilder? = null
    private var crtEffect: RuntimeEffect? = null
    private var crtBuilder: RuntimeShaderBuilder? = null
    private var videoFilter = VideoFilter.NONE
    private var initialized = false
    private var outputWidth = 0
    private var outputHeight = 0
    private var presentedFrames = 0L

    override fun init(videoFilter: VideoFilter) {
        release()
        try {
            this.videoFilter = videoFilter
            backgroundPaint = Paint().apply { color = Color.BLACK }
            framePaint = Paint().apply { isAntiAlias = false }
            paletteEffect = RuntimeEffect.makeForShader(readTextResource(PALETTE_SHADER_RESOURCE))
            paletteBuilder = RuntimeShaderBuilder(requireNotNull(paletteEffect))
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

    override fun present(framebuffer: ByteArray, windowWidth: Int, windowHeight: Int) {
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

    private fun uploadFrame(source: ByteArray, bitmap: Bitmap) {
        check(bitmap.installPixels(frameImageInfo, source, FRAME_WIDTH)) { "Failed to upload NES frame bitmap" }
    }

    override fun draw(canvas: Canvas) {
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
        val shader = makeFrameColorShader(bitmap)
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

    override fun close() {
        release()
    }

    private fun drawCrt(canvas: Canvas, frameBitmap: Bitmap, destination: Rect) {
        val frameShader = makeFrameColorShader(frameBitmap)
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

    private fun makeFrameColorShader(frameBitmap: Bitmap): Shader {
        val indexShader = frameBitmap.makeShader(
            FilterTileMode.CLAMP,
            FilterTileMode.CLAMP,
            SamplingMode.DEFAULT,
            null,
        )
        try {
            val builder = requireNotNull(paletteBuilder)
            builder.child("indexTexture", indexShader)
            return builder.makeShader()
        } finally {
            indexShader.close()
        }
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
        paletteBuilder?.close()
        paletteEffect?.close()
        crtBuilder?.close()
        crtEffect?.close()
        framePaint?.close()
        backgroundPaint?.close()
        frameBitmap = null
        paletteBuilder = null
        paletteEffect = null
        crtBuilder = null
        crtEffect = null
        framePaint = null
        backgroundPaint = null
        initialized = false
    }

    private companion object {
        const val FRAME_WIDTH = 256
        const val FRAME_HEIGHT = 240
        const val PALETTE_SHADER_RESOURCE = "shaders/palette.sksl"
        const val CRT_SHADER_RESOURCE = "shaders/crt.sksl"
        val SOURCE_RECT = Rect.makeWH(FRAME_WIDTH.toFloat(), FRAME_HEIGHT.toFloat())
    }
}
