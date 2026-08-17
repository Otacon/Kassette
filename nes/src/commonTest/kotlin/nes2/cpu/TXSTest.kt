package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class TXSTest : FreeSpec({

    "TXS" - {

        "copies X into SP" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                x = 0x42,
                sp = 0x00,
            )

            memory[state.pc] = 0x9A

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.sp shouldBe 0x42
            state.x shouldBe 0x42

            state.pc shouldBe 0x8001
            cycles shouldBe 2
        }

        "copies zero into SP without setting zero flag" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                x = 0x00,
                sp = 0xFF,
            ).also {
                it.z = false
            }

            memory[state.pc] = 0x9A

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.sp shouldBe 0x00

            // TXS does not affect flags.
            state.z shouldBe false
        }

        "copies value with bit 7 set without setting negative flag" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                x = 0x80,
            ).also {
                it.n = false
            }

            memory[state.pc] = 0x9A

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.sp shouldBe 0x80
            state.n shouldBe false
        }

        "does not modify any flags" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                x = 0x42,
            ).also {
                it.c = true
                it.z = true
                it.i = true
                it.d = true
                it.v = true
                it.n = true
            }

            memory[state.pc] = 0x9A

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.c shouldBe true
            state.z shouldBe true
            state.i shouldBe true
            state.d shouldBe true
            state.v shouldBe true
            state.n shouldBe true

            state.pc shouldBe 0x8001
            cycles shouldBe 2
        }

        "does not modify X" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                x = 0xAB,
                sp = 0x00,
            )

            memory[state.pc] = 0x9A

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.x shouldBe 0xAB
            state.sp shouldBe 0xAB
        }
    }
})