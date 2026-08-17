package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class TSXTest : FreeSpec({

    "TSX" - {

        "copies SP into X" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0x42,
                x = 0x00,
            )

            memory[state.pc] = 0xBA

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.x shouldBe 0x42
            state.sp shouldBe 0x42

            state.z shouldBe false
            state.n shouldBe false

            state.pc shouldBe 0x8001
            cycles shouldBe 2
        }

        "sets zero flag when SP is zero" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0x00,
                x = 0xFF,
            )

            memory[state.pc] = 0xBA

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.x shouldBe 0x00
            state.z shouldBe true
            state.n shouldBe false
        }

        "sets negative flag when bit 7 is set" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0x80,
            )

            memory[state.pc] = 0xBA

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.x shouldBe 0x80
            state.z shouldBe false
            state.n shouldBe true
        }

        "clears zero and negative flags" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0x42,
            ).also {
                it.z = true
                it.n = true
            }

            memory[state.pc] = 0xBA

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.x shouldBe 0x42
            state.z shouldBe false
            state.n shouldBe false
        }

        "does not modify unrelated flags" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0x42,
            ).also {
                it.c = true
                it.v = true
                it.i = true
                it.d = true
            }

            memory[state.pc] = 0xBA

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.c shouldBe true
            state.v shouldBe true
            state.i shouldBe true
            state.d shouldBe true
        }

        "does not modify SP" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0xAB,
            )

            memory[state.pc] = 0xBA

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.sp shouldBe 0xAB
            state.x shouldBe 0xAB
        }
    }
})