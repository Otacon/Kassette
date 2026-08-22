package frontend

import co.touchlab.kermit.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nes.cartridge.Cartridge
import platform.AudioPipeline
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
    val isPoweredOn: StateFlow<Boolean> get() = machine.isPoweredOn

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

    suspend fun pause() = onRuntime {
        paused = true
        runtime.pause()
    }

    suspend fun resume() = onRuntime {
        paused = false
    }

    suspend fun setSoundEnabled(enabled: Boolean) = onRuntime {
        runtime.soundEnabled = enabled
    }

    suspend fun loadCartridge(cartridge: Cartridge) = onRuntime {
        machine.powerOff()
        machine.insert(cartridge)
        machine.powerOn()
    }

    suspend fun powerOff() = onRuntime {
        machine.powerOff()
    }

    suspend fun reset() = onRuntime {
        machine.reset()
    }

    suspend fun captureState(): NesMachineState = onRuntime {
        machine.captureState()
    }

    suspend fun restoreState(state: NesMachineState) = onRuntime {
        machine.restoreState(state)
    }

    override fun close() {
        stop()
        scope.cancel()
        runtime.close()
    }

    private suspend fun <T> onRuntime(operation: () -> T): T = scope.async {
        mutex.withLock { operation() }
    }.await()
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

            if (frameRendered) {
                nextFrame += frameDuration
                if (nextFrame.elapsedNow() > frameDuration * 4) {
                    nextFrame = TimeSource.Monotonic.markNow() + frameDuration
                }
            } else {
                nextFrame = TimeSource.Monotonic.markNow() + frameDuration
                delay(1)
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        onError(error)
    }
}

private val log = Logger.withTag("EmulatorRuntimeHost")
