package platform

import dev.zacsweers.metro.Inject
import nes.apu.NesApu
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10.*
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10.*
import java.nio.ShortBuffer

@Inject
actual class AudioPipeline actual constructor() : AutoCloseable {
    private val sampleRate = NesApu.DEFAULT_SAMPLE_RATE
    private val device: Long
    private val context: Long
    private val source: Int
    private val buffers = IntArray(8)
    private val freeBuffers = IntArray(8)
    private var freeCount = 0
    private val upload: ShortBuffer = BufferUtils.createShortBuffer(NesApu.MAX_FRAME_SAMPLES)

    init {
        device = alcOpenDevice(null as CharSequence?)
        if (device == 0L) throw IllegalStateException("OpenAL audio device initialization failure")
        context = alcCreateContext(device, null as IntArray?)
        if (context == 0L) throw IllegalStateException("OpenAL audio context initialization failure")
        alcMakeContextCurrent(context)
        AL.createCapabilities(ALC.createCapabilities(device))
        source = alGenSources()
        alGenBuffers(buffers)
        var i = 0
        while (i < buffers.size) {
            freeBuffers[freeCount++] = buffers[i]
            i++
        }
        alSourcef(source, AL_GAIN, 0.65f)
    }

    actual fun submit(samples: ShortArray, count: Int) {
        if (count <= 0) return
        alcMakeContextCurrent(context)
        unqueueProcessed()
        if (freeCount == 0) return
        val buffer = freeBuffers[--freeCount]
        upload.clear()
        upload.put(samples, 0, minOf(count, upload.capacity()))
        upload.flip()
        alBufferData(buffer, AL_FORMAT_MONO16, upload, sampleRate)
        alSourceQueueBuffers(source, buffer)
        if (alGetSourcei(source, AL_SOURCE_STATE) != AL_PLAYING) alSourcePlay(source)
    }

    actual fun stop() {
        alcMakeContextCurrent(context)
        alSourceStop(source)
        unqueueAll()
    }

    private fun unqueueProcessed() {
        var processed = alGetSourcei(source, AL_BUFFERS_PROCESSED)
        while (processed > 0) {
            if (freeCount < freeBuffers.size) freeBuffers[freeCount++] =
                alSourceUnqueueBuffers(source) else alSourceUnqueueBuffers(source)
            processed--
        }
    }

    private fun unqueueAll() {
        var queued = alGetSourcei(source, AL_BUFFERS_QUEUED)
        while (queued > 0) {
            if (freeCount < freeBuffers.size) freeBuffers[freeCount++] =
                alSourceUnqueueBuffers(source) else alSourceUnqueueBuffers(source)
            queued--
        }
    }

    override fun close() {
        alcMakeContextCurrent(context)
        alSourceStop(source)
        unqueueAll()
        alDeleteSources(source)
        alDeleteBuffers(buffers)
        alcMakeContextCurrent(0)
        alcDestroyContext(context)
        alcCloseDevice(device)
    }
}
