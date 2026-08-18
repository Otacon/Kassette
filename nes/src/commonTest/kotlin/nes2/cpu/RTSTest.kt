package nes2.cpu

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import nes2.fakes.FakeBus

class RTSTest : FreeSpec({

    "RTS" - {

        "pulls return address and advances to next instruction" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x1234,
                sp = 0xFB,
            )

            memory[0x1234] = 0x60

            // Stack contains $8002.
            // SP points below the last pushed byte.
            memory[0x01FC] = 0x02
            memory[0x01FD] = 0x80

            val cycles = Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            // RTS pulls $8002 then adds one.
            state.pc shouldBe 0x8003
            state.sp shouldBe 0xFD
            cycles shouldBe 6
        }

        "stack pointer wraps while pulling" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x1234,
                sp = 0xFE,
            )

            memory[0x1234] = 0x60

            // First pull: SP FF -> low byte.
            memory[0x01FF] = 0x34

            // Second pull: SP wraps to 00 -> high byte.
            memory[0x0100] = 0x12

            Cpu6502(
                bus = FakeBus(memory = memory),
                state = state,
            ).step()

            state.pc shouldBe 0x1235
            state.sp shouldBe 0x00
        }

        "does not modify registers or flags" {
            val memory = IntArray(0x10_000)
            val state = CpuState(
                pc = 0x1234,
                a = 0x11,
                x = 0x22,
                y = 0x33,
                sp = 0xFB,
            ).also {
                it.c = true
                it.z = true
                it.i = true
                it.d = true
                it.v = true
                it.n = true
            }

            memory[0x1234] = 0x60
            memory[0x01FC] = 0x02
            memory[0x01FD] = 0x80

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

    "JSR followed by RTS returns to instruction after JSR" {
        val memory = IntArray(0x10_000)
        val state = CpuState(
            pc = 0x8000,
            sp = 0xFD,
        )

        // JSR $9000
        memory[0x8000] = 0x20
        memory[0x8001] = 0x00
        memory[0x8002] = 0x90

        // RTS
        memory[0x9000] = 0x60

        val cpu = Cpu6502(
            bus = FakeBus(memory = memory),
            state = state,
        )

        cpu.step() shouldBe 6
        state.pc shouldBe 0x9000
        state.sp shouldBe 0xFB

        cpu.step() shouldBe 6
        state.pc shouldBe 0x8003
        state.sp shouldBe 0xFD
    }
})