package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class NOPTest : FreeSpec({
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



    "does nothing except advance PC" {
        state = CpuState(
            pc = 0x8000,
            a = 0x11,
            x = 0x22,
            y = 0x33,
            sp = 0x44,
            status = 0xEF,
        )

        memory[0x8000] = 0xEA

        bus = FakeBus(memory = memory)
        cpu = Cpu6502(bus = bus, state = state)
        val cycles = cpu.step()

        state.a shouldBe 0x11
        state.x shouldBe 0x22
        state.y shouldBe 0x33
        state.sp shouldBe 0x44

        state.c shouldBe true
        state.z shouldBe true
        state.i shouldBe true
        state.d shouldBe true
        state.v shouldBe true
        state.n shouldBe true

        state.pc shouldBe 0x8001
        cycles shouldBe 2
    }
})