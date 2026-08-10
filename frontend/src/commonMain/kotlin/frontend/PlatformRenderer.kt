package frontend

import io.VideoFilter
import io.readTextResource
import org.jetbrains.skia.*

class PlatformRenderer : Renderer {
    private val backgroundUpload = ByteArray(FRAME_WIDTH * FRAME_HEIGHT * BYTES_PER_PIXEL)
    private val spriteUpload = ByteArray(FRAME_WIDTH * FRAME_HEIGHT * BYTES_PER_PIXEL)
    private val imageInfo = ImageInfo(
        FRAME_WIDTH,
        FRAME_HEIGHT,
        ColorType.RGBA_8888,
        ColorAlphaType.PREMUL,
        ColorSpace.sRGB,
    )
    private var backgroundImage: Image? = null
    private var spriteImage: Image? = null
    private var backgroundPaint: Paint? = null
    private var framePaint: Paint? = null
    private var crtEffect: RuntimeEffect? = null
    private var crtBuilder: RuntimeShaderBuilder? = null
    private var shadowEffect: RuntimeEffect? = null
    private var shadowBuilder: RuntimeShaderBuilder? = null
    private var videoFilter = VideoFilter.NONE
    private var castShadowEnabled = false
    private var initialized = false
    private var outputWidth = 0
    private var outputHeight = 0
    private var presentedFrames = 0L

    override fun init(videoFilter: VideoFilter) {
        release()
        try {
            this.videoFilter = videoFilter
            castShadowEnabled = videoFilter == VideoFilter.CAST_SHADOWS
            backgroundPaint = Paint().apply { color = Color.BLACK }
            framePaint = Paint().apply { isAntiAlias = false }
            when (videoFilter) {
                VideoFilter.CRT -> {
                    crtEffect = RuntimeEffect.makeForShader(readTextResource(CRT_SHADER_RESOURCE))
                    crtBuilder = RuntimeShaderBuilder(requireNotNull(crtEffect))
                }

                VideoFilter.CAST_SHADOWS -> {
                    shadowEffect = RuntimeEffect.makeForShader(readTextResource(SHADOW_SHADER_RESOURCE))
                    shadowBuilder = RuntimeShaderBuilder(requireNotNull(shadowEffect))
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

    override fun present(frame: VideoFrame, windowWidth: Int, windowHeight: Int) {
        check(initialized) { "Skiko renderer is not initialized" }
        require(frame.background.size >= FRAME_WIDTH * FRAME_HEIGHT) { "Incomplete NES background layer" }
        require(frame.sprites.size >= FRAME_WIDTH * FRAME_HEIGHT) { "Incomplete NES sprite layer" }

        backgroundImage = makeImage(frame.background, backgroundUpload, backgroundImage)
        spriteImage = makeImage(frame.sprites, spriteUpload, spriteImage)
        outputWidth = windowWidth
        outputHeight = windowHeight
    }

    private fun makeImage(source: IntArray, upload: ByteArray, previous: Image?): Image {
        var src = 0
        var dst = 0
        while (src < FRAME_WIDTH * FRAME_HEIGHT) {
            val color = source[src++]
            upload[dst++] = (color shr 16).toByte()
            upload[dst++] = (color shr 8).toByte()
            upload[dst++] = color.toByte()
            upload[dst++] = (color ushr 24).toByte()
        }

        previous?.close()
        return Image.makeRaster(imageInfo, upload, FRAME_WIDTH * BYTES_PER_PIXEL)
    }

    override fun draw(canvas: Canvas) {
        val background = backgroundImage ?: return
        val sprites = spriteImage ?: return
        if (outputWidth <= 0 || outputHeight <= 0) return

        val output = Rect.makeWH(outputWidth.toFloat(), outputHeight.toFloat())
        canvas.drawRect(output, requireNotNull(backgroundPaint))
        val destination = destinationRect()
        when (videoFilter) {
            VideoFilter.CRT -> {
                drawCrt(canvas, background, sprites, destination)
            }

            VideoFilter.CAST_SHADOWS -> {
                drawLayer(canvas, background, destination)
                drawShadow(canvas, sprites, destination)
                drawLayer(canvas, sprites, destination)
            }

            VideoFilter.NONE -> {
                drawLayer(canvas, background, destination)
                drawLayer(canvas, sprites, destination)
            }
        }
        presentedFrames++
    }

    private fun drawShadow(canvas: Canvas, spriteImage: Image, destination: Rect) {
        val spriteShader = spriteImage.makeShader(
            FilterTileMode.CLAMP,
            FilterTileMode.CLAMP,
            SamplingMode.LINEAR,
            null,
        )
        val builder = requireNotNull(shadowBuilder)
        try {
            builder.child("spriteTexture", spriteShader)
            builder.uniform("destinationOrigin", destination.left, destination.top)
            builder.uniform("destinationSize", destination.width, destination.height)

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
            spriteShader.close()
        }
    }

    private fun drawLayer(canvas: Canvas, image: Image, destination: Rect) {
        canvas.drawImageRect(
            image,
            SOURCE_RECT,
            destination,
            SamplingMode.DEFAULT,
            null,
            true,
        )
    }

    override fun close() {
        release()
    }

    private fun drawCrt(canvas: Canvas, backgroundImage: Image, spriteImage: Image, destination: Rect) {
        val backgroundShader = backgroundImage.makeShader(
            FilterTileMode.CLAMP,
            FilterTileMode.CLAMP,
            SamplingMode.LINEAR,
            null,
        )
        val spriteShader = spriteImage.makeShader(
            FilterTileMode.CLAMP,
            FilterTileMode.CLAMP,
            SamplingMode.LINEAR,
            null,
        )
        try {
            val builder = requireNotNull(crtBuilder)
            try {
                builder.child("backgroundTexture", backgroundShader)
                builder.child("spriteTexture", spriteShader)
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
                spriteShader.close()
            }
        } finally {
            backgroundShader.close()
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
        backgroundImage?.close()
        spriteImage?.close()
        crtBuilder?.close()
        crtEffect?.close()
        shadowBuilder?.close()
        shadowEffect?.close()
        framePaint?.close()
        backgroundPaint?.close()
        backgroundImage = null
        spriteImage = null
        crtBuilder = null
        crtEffect = null
        shadowBuilder = null
        shadowEffect = null
        framePaint = null
        backgroundPaint = null
        initialized = false
    }

    private companion object {
        const val FRAME_WIDTH = 256
        const val FRAME_HEIGHT = 240
        const val BYTES_PER_PIXEL = 4
        const val CRT_SHADER_RESOURCE = "shaders/crt.sksl"
        const val SHADOW_SHADER_RESOURCE = "shaders/shadow.sksl"
        val SOURCE_RECT = Rect.makeWH(FRAME_WIDTH.toFloat(), FRAME_HEIGHT.toFloat())
    }
}
