package frontend

import co.touchlab.kermit.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nes.NesMachine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class EmulatorRuntimeHost(
    private val machine: NesMachine,
    audio: AudioPipeline,
    private val input: EmulatorInput,
) : AutoCloseable {
    val frameBuffer = SharedFrameBuffer()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val runtime = EmulatorRuntime(machine, audio, input, frameBuffer)
    private var loop: Job? = null
    private var paused = false

    fun start(
        onFps: (Int) -> Unit,
        onError: (Throwable) -> Unit = { error -> log.e(error) { "Emulator loop failed" } },
    ) {
        check(loop == null) { "Emulator runtime host is already started" }
        input.init()
        loop = scope.startEmulatorLoop(
            frameNanos = { machine.timing.frameNanos },
            step = {
                mutex.withLock { !paused && runtime.step() }
            },
            onFps = onFps,
            onError = onError,
        )
    }

    fun stop() {
        loop?.cancel()
        loop = null
    }

    suspend fun pause() = mutex.withLock {
        paused = true
        runtime.pause()
    }

    suspend fun resume() = mutex.withLock {
        paused = false
    }

    override fun close() {
        stop()
        scope.cancel()
        runtime.close()
    }
}

private fun CoroutineScope.startEmulatorLoop(
    frameNanos: suspend () -> Long,
    step: suspend () -> Boolean,
    onFps: (Int) -> Unit,
    onError: (Throwable) -> Unit,
): Job = launch {
    var frames = 0
    var fpsTime = TimeSource.Monotonic.markNow()
    var nextFrame = TimeSource.Monotonic.markNow()

    try {
        while (isActive) {
            val frameDuration = frameNanos().nanoseconds
            val remaining = -nextFrame.elapsedNow()
            if (remaining > Duration.ZERO) {
                delay(remaining)
            }

            val frameRendered = step()
            if (frameRendered) frames++

            if (fpsTime.elapsedNow() >= 1.seconds) {
                onFps(frames)
                frames = 0
                fpsTime = TimeSource.Monotonic.markNow()
            }

            nextFrame += frameDuration
            if (nextFrame.elapsedNow() > frameDuration * 4) {
                nextFrame = TimeSource.Monotonic.markNow() + frameDuration
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        onError(error)
    }
}

private val log = Logger.withTag("EmulatorRuntimeHost")
