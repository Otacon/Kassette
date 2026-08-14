@file:OptIn(ExperimentalWasmJsInterop::class)

package platform

import frontend.isPageActive
import nes.apu.NesApu

actual class AudioPipeline actual constructor() {
    private var context: JsAny? = null
    private var nextStartTime = 0.0

    actual fun submit(samples: ShortArray, count: Int) {
        if (count <= 0) return
        if (!isPageActive()) return
        val audio = ensureContext()
        val now = audioCurrentTime(audio)
        if (nextStartTime < now) nextStartTime = now + 0.02
        val length = minOf(count, samples.size)
        val normalized = JsArray<JsNumber>()
        var i = 0
        while (i < length) {
            normalized[i] = (samples[i] / 32768.0).toJsNumber()
            i++
        }
        val duration = length.toDouble() / NesApu.DEFAULT_SAMPLE_RATE
        queuePcm(audio, normalized, NesApu.DEFAULT_SAMPLE_RATE, nextStartTime)
        nextStartTime += duration
    }

    actual fun stop() {
        closeAudioContext()
    }

    private fun closeAudioContext() {
        context?.let(::audioClose)
        context = null
        nextStartTime = 0.0
    }

    private fun ensureContext(): JsAny {
        return context ?: createAudioContext().also { context = it }
    }
}

@JsFun("() => new (window.AudioContext || window.webkitAudioContext)()")
private external fun createAudioContext(): JsAny

@JsFun("(context) => context.close()")
private external fun audioClose(context: JsAny)

@JsFun("(context) => context.currentTime")
private external fun audioCurrentTime(context: JsAny): Double

@JsFun(
    """
    (context, samples, sampleRate, startTime) => {
        const buffer = context.createBuffer(1, samples.length, sampleRate);
        const channel = buffer.getChannelData(0);
        for (let i = 0; i < samples.length; i++) {
            channel[i] = samples[i];
        }
        const source = context.createBufferSource();
        source.buffer = buffer;
        source.connect(context.destination);
        source.start(startTime);
    }
    """
)
private external fun queuePcm(context: JsAny, samples: JsArray<JsNumber>, sampleRate: Int, startTime: Double)
