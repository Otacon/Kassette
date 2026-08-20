package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class ALRTest : FreeSpec({
    lateinit var memory: IntArray
    lateinit var state: CpuState
    lateinit var bus: FakeBus
    lateinit var cpu: Cpu6502

    beforeTest {
        memory = IntArray(0x10_000)
        state = CpuState()
        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
    }

    "ANDs immediate then shifts right" {
        state.pc = 0x8000
        state.a = 0x83
        memory[0x8000] = 0x4B
        memory[0x8001] = 0x03

        val cycles = cpu.step()

        state.a shouldBe 0x01
        state.c shouldBe true
        state.z shouldBe false
        state.n shouldBe false
        state.pc shouldBe 0x8002
        cycles shouldBe 2
    }

    "sets zero when shifted result is zero" {
        state.pc = 0x8000
        state.a = 0x01
        memory[0x8000] = 0x4B
        memory[0x8001] = 0xFE

        cpu.step()

        state.a shouldBe 0x00
        state.c shouldBe false
        state.z shouldBe true
        state.n shouldBe false
    }
})
