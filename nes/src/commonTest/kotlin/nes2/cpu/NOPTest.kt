package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class NOPTest : FreeSpec({

    "NOP" - {

        "does nothing except advance PC" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x11,
                x = 0x22,
                y = 0x33,
                sp = 0x44,
            ).also {
                it.c = true
                it.z = true
                it.i = true
                it.d = true
                it.v = true
                it.n = true
            }

            memory[0x8000] = 0xEA

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

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
    }
})