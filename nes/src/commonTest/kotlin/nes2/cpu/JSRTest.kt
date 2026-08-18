package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class JSRTest : FreeSpec({

    "JSR" - {

        "jumps to target address" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0xFD,
            )

            memory[0x8000] = 0x20
            memory[0x8001] = 0x34
            memory[0x8002] = 0x12

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.pc shouldBe 0x1234
            cycles shouldBe 6
        }

        "pushes return address onto stack" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0xFD,
            )

            memory[0x8000] = 0x20
            memory[0x8001] = 0x34
            memory[0x8002] = 0x12

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            // JSR pushes $8002.
            // High byte first at $01FD.
            memory[0x01FD] shouldBe 0x80

            // Then low byte at $01FC.
            memory[0x01FC] shouldBe 0x02

            state.sp shouldBe 0xFB
        }

        "stack pointer wraps while pushing" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                sp = 0x00,
            )

            memory[0x8000] = 0x20
            memory[0x8001] = 0x34
            memory[0x8002] = 0x12

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            memory[0x0100] shouldBe 0x80
            memory[0x01FF] shouldBe 0x02

            state.sp shouldBe 0xFE
        }

        "does not modify registers or flags" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x8000,
                a = 0x11,
                x = 0x22,
                y = 0x33,
                sp = 0xFD,
            ).also {
                it.c = true
                it.z = true
                it.i = true
                it.d = true
                it.v = true
                it.n = true
            }

            memory[0x8000] = 0x20
            memory[0x8001] = 0x34
            memory[0x8002] = 0x12

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.a shouldBe 0x11
            state.x shouldBe 0x22
            state.y shouldBe 0x33

            state.c shouldBe true
            state.z shouldBe true
            state.i shouldBe true
            state.d shouldBe true
            state.v shouldBe true
            state.n shouldBe true
        }
    }
})