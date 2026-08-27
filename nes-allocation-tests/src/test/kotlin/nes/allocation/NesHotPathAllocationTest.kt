package nes.allocation

import com.sun.management.ThreadMXBean
import nes.console.NesConsole
import nes.console.NesConsoleOptions
import nes.cpu.ConsoleRegion
import nes.mapper.GameSystem
import nes.mapper.MirroringType
import nes.mapper.NesRomInfo
import nes.mapper.RomData
import nes.mapper.createMapper
import nes.ppu.DefaultNesPpu
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

class NesHotPathAllocationTest {
    @Test
    fun runFrameHotPathStaysWithinAllocationBudget() {
        val allocationCounter = ThreadAllocationCounter.create()
        val console = createConsole("/all_instrs.nes")

        repeat(WarmupFrames) {
            console.runFrame()
        }

        forceGc()

        val before = allocationCounter.currentThreadAllocatedBytes()
        repeat(MeasuredFrames) {
            console.runFrame()
        }
        val allocatedBytes = allocationCounter.currentThreadAllocatedBytes() - before
        val bytesPerFrame = allocatedBytes.toDouble() / MeasuredFrames

        println("NES hot path allocated $allocatedBytes bytes across $MeasuredFrames frames ($bytesPerFrame bytes/frame)")

        assertTrue(
            allocatedBytes <= AllocationBudgetBytes,
            "Expected <= $AllocationBudgetBytes bytes allocated across $MeasuredFrames warmed frames, " +
                "but allocated $allocatedBytes bytes ($bytesPerFrame bytes/frame)."
        )
    }

    private class ThreadAllocationCounter private constructor(private val bean: ThreadMXBean) {
        fun currentThreadAllocatedBytes(): Long = bean.getThreadAllocatedBytes(Thread.currentThread().threadId())

        companion object {
            fun create(): ThreadAllocationCounter {
                val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
                    ?: error("Thread allocation counters require com.sun.management.ThreadMXBean")
                check(bean.isThreadAllocatedMemorySupported) {
                    "Thread allocated-memory counters are not supported by this JVM"
                }
                if (!bean.isThreadAllocatedMemoryEnabled) {
                    bean.isThreadAllocatedMemoryEnabled = true
                }
                return ThreadAllocationCounter(bean)
            }
        }
    }

    private companion object {
        const val WarmupFrames = 120
        const val MeasuredFrames = 240
        const val AllocationBudgetBytes = 0L

        fun forceGc() {
            repeat(3) {
                System.gc()
                Thread.sleep(20)
            }
        }

        fun createConsole(resourceName: String): NesConsole {
            val bytes = loadResource(resourceName)
            val rom = parseINes(
                name = resourceName,
                bytes = bytes,
            )

            return NesConsole(
                mapper = createMapper(rom),
                ppu = DefaultNesPpu(),
                options = NesConsoleOptions(
                    region = ConsoleRegion.Ntsc,
                    randomizeCpuPpuAlignment = false,
                    initializeRam = { it.fill(0) },
                ),
            ).also {
                it.powerOn()
            }
        }

        fun loadResource(path: String): ByteArray =
            checkNotNull(NesHotPathAllocationTest::class.java.getResourceAsStream(path)) { "Resource not found: $path" }
                .use { it.readBytes() }

        fun parseINes(name: String, bytes: ByteArray): RomData {
            require(bytes.size >= 16) { "ROM is smaller than an iNES header" }
            require(
                bytes[0].u8() == 'N'.code &&
                    bytes[1].u8() == 'E'.code &&
                    bytes[2].u8() == 'S'.code &&
                    bytes[3].u8() == 0x1A
            ) {
                "Invalid iNES ROM: $name"
            }

            val prgBanks = bytes[4].u8()
            val chrBanks = bytes[5].u8()
            val flags6 = bytes[6].u8()
            val flags7 = bytes[7].u8()
            val hasBattery = flags6 and 0x02 != 0
            val hasTrainer = flags6 and 0x04 != 0
            val mapperId = (flags6 ushr 4) or (flags7 and 0xF0)
            val mirroring = when {
                flags6 and 0x08 != 0 -> MirroringType.FourScreens
                flags6 and 0x01 != 0 -> MirroringType.Vertical
                else -> MirroringType.Horizontal
            }

            var offset = 16
            val trainer = if (hasTrainer) {
                bytes.copyOfRange(offset, offset + 512).also { offset += 512 }
            } else {
                ByteArray(0)
            }

            val prgSize = prgBanks * 16 * 1024
            val chrSize = chrBanks * 8 * 1024
            require(offset + prgSize + chrSize <= bytes.size) { "Invalid iNES sizes in $name" }

            val prgRom = bytes.copyOfRange(offset, offset + prgSize)
            offset += prgSize
            val chrRom = bytes.copyOfRange(offset, offset + chrSize)

            return RomData(
                info = NesRomInfo(
                    romName = name,
                    filename = name,
                    mapperID = mapperId,
                    system = GameSystem.NesNtsc,
                    hasBattery = hasBattery,
                    hasTrainer = hasTrainer,
                    mirroring = mirroring,
                ),
                prgRom = prgRom,
                chrRom = chrRom,
                trainerData = trainer,
            )
        }

        fun Byte.u8(): Int = toInt() and 0xFF
    }
}
