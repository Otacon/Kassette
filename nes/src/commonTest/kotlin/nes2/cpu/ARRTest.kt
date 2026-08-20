package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class ARRTest : FreeSpec({
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

    "ANDs immediate then rotates right and sets carry and overflow from bits 6 and 5" {
        state.pc = 0x8000
        state.a = 0xFF
        state.c = false
        memory[0x8000] = 0x6B
        memory[0x8001] = 0x80

        val cycles = cpu.step()

        state.a shouldBe 0x40
        state.c shouldBe true
        state.v shouldBe true
        state.z shouldBe false
        state.n shouldBe false
        state.pc shouldBe 0x8002
        cycles shouldBe 2
    }

    "rotates carry into bit 7" {
        state.pc = 0x8000
        state.a = 0x02
        state.c = true
        memory[0x8000] = 0x6B
        memory[0x8001] = 0x02

        cpu.step()

        state.a shouldBe 0x81
        state.c shouldBe false
        state.v shouldBe false
        state.n shouldBe true
    }
})
