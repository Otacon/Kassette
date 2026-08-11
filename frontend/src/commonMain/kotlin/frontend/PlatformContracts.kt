package frontend

interface AudioPipeline {
    fun submit(samples: ShortArray, count: Int)

    fun pause() = Unit

    fun close() = Unit
}

interface VideoOutput {
    fun submit(framebuffer: ByteArray)
}
