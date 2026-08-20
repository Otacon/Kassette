package nes2

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus
import nes2.fakes.FakePpu

class OamDmaNesTest : FreeSpec({

    lateinit var dma: OamDmaNes
    lateinit var cpuBus: FakeBus
    lateinit var memory: IntArray
    lateinit var ppu: FakePpu

    beforeTest {
        memory = IntArray(0x10_000)
        cpuBus = FakeBus(memory)
        ppu = FakePpu()
        dma = OamDmaNes(cpuBus, ppu)
    }

    "starts DMA with an 8-bit page" {
        dma.start(0x102)

        dma.active shouldBe true
        dma.page shouldBe 0x02
    }

    "transfers the selected CPU page to PPU OAM" {
        val page = 0x02
        val baseAddress = page shl 8

        var offset = 0
        while (offset < 0x100) {
            memory[baseAddress + offset] = offset
            offset++
        }

        dma.start(page)
        dma.transfer()

        ppu.oamWrites.size shouldBe 0x100

        ppu.oamWrites.forEachIndexed { index, value ->
            value shouldBe index
        }

        dma.active shouldBe false
    }

    "reads from the selected page" {
        memory[0x0200] = 0x12
        memory[0x02FF] = 0x34

        dma.start(0x02)
        dma.transfer()

        ppu.oamWrites.first() shouldBe 0x12
        ppu.oamWrites.last() shouldBe 0x34
    }
})