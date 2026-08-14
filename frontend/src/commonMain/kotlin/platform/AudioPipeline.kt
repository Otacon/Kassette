package platform


expect class AudioPipeline() {
    fun submit(samples: ShortArray, count: Int)
    fun stop()
}