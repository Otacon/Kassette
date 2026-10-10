package platform

import cnames.structs.SDL_AudioStream
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import nes.apu.NesApu
import sdl3.SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK
import sdl3.SDL_AUDIO_S16
import sdl3.SDL_AudioSpec
import sdl3.SDL_ClearAudioStream
import sdl3.SDL_DestroyAudioStream
import sdl3.SDL_INIT_AUDIO
import sdl3.SDL_InitSubSystem
import sdl3.SDL_OpenAudioDeviceStream
import sdl3.SDL_PutAudioStreamData
import sdl3.SDL_ResumeAudioStreamDevice

@OptIn(ExperimentalForeignApi::class)
actual class AudioPipeline actual constructor() {
    private val stream: CPointer<SDL_AudioStream>? = memScoped {
        SDL_InitSubSystem(SDL_INIT_AUDIO)
        val spec = alloc<SDL_AudioSpec> {
            format = SDL_AUDIO_S16
            channels = 1
            freq = NesApu.DEFAULT_SAMPLE_RATE
        }
        SDL_OpenAudioDeviceStream(SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK, spec.ptr, null, null)
            ?.also { SDL_ResumeAudioStreamDevice(it) }
    }

    actual fun submit(samples: ShortArray, count: Int) {
        val stream = stream ?: return
        if (count <= 0) return
        val bytes = minOf(count, samples.size) * Short.SIZE_BYTES
        samples.usePinned { pinned ->
            SDL_PutAudioStreamData(stream, pinned.addressOf(0), bytes.convert())
        }
    }

    actual fun stop() {
        stream?.let { SDL_ClearAudioStream(it) }
    }

    fun close() {
        stream?.let { SDL_DestroyAudioStream(it) }
    }
}
